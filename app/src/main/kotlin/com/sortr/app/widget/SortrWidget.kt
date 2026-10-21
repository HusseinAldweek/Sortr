package com.sortr.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.sortr.app.R
import com.sortr.app.launch.LaunchRequests
import com.sortr.app.ui.common.formatCount
import com.sortr.domain.model.GoalProgress
import com.sortr.domain.repository.GoalRepository
import com.sortr.domain.repository.PhotoRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Home screen widget: how much is left to sort, today's goal, and a Start button. */
@AndroidEntryPoint
class SortrWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var updater: WidgetUpdater

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        updater.scope.launch {
            try {
                updater.refresh()
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) = updater.setActive(true)

    override fun onDisabled(context: Context) = updater.setActive(false)
}

/** What the widget shows. */
data class WidgetState(val toReview: Int, val goal: GoalProgress)

/** Keeps the widgets up to date while Sortr runs; Android refreshes them every 30 minutes otherwise. */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photos: PhotoRepository,
    private val goals: GoalRepository,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val active = MutableStateFlow(false)
    private val manager: AppWidgetManager get() = AppWidgetManager.getInstance(context)

    /** Starts following the counts. Call once, when the app starts. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        active.value = widgetIds().isNotEmpty()
        scope.launch {
            // Only watch the database while a widget is on the home screen.
            active.flatMapLatest { on -> if (on) state() else emptyFlow() }
                .distinctUntilChanged()
                .debounce(UPDATE_DELAY_MS)
                .collect(::push)
        }
    }

    fun setActive(on: Boolean) {
        active.value = on
    }

    suspend fun refresh() = push(state().first())

    private fun state() = combine(photos.observeSummary().map { it.toReview }, goals.progress, ::WidgetState)

    private fun push(state: WidgetState) {
        val ids = widgetIds()
        if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(state))
    }

    private fun widgetIds(): IntArray = manager.getAppWidgetIds(ComponentName(context, SortrWidgetProvider::class.java))

    private fun views(state: WidgetState) = RemoteViews(context.packageName, R.layout.widget_sortr).apply {
        val done = state.toReview == 0
        setTextViewText(R.id.widget_count, formatCount(state.toReview))
        setTextViewText(R.id.widget_caption, context.getString(if (done) R.string.widget_done else R.string.widget_left))
        setViewVisibility(R.id.widget_start, if (done) View.GONE else View.VISIBLE)
        if (state.goal.enabled) {
            setTextViewText(R.id.widget_goal, context.getString(R.string.widget_goal, formatCount(state.goal.today), formatCount(state.goal.goal)))
            setViewVisibility(R.id.widget_goal, View.VISIBLE)
        } else {
            setViewVisibility(R.id.widget_goal, View.GONE)
        }
        setOnClickPendingIntent(android.R.id.background, activity(REQUEST_OPEN, LaunchRequests.openAppIntent(context)))
        setOnClickPendingIntent(R.id.widget_start, activity(REQUEST_START, LaunchRequests.sessionIntent(context)))
    }

    private fun activity(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private companion object {
        const val UPDATE_DELAY_MS = 1_000L
        const val REQUEST_OPEN = 10
        const val REQUEST_START = 11
    }
}
