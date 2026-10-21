package com.sortr.app.launch

import android.content.Context
import android.content.Intent
import com.sortr.app.MainActivity
import com.sortr.domain.model.SessionFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests from outside Sortr's screens, like the widget's Start button or the daily goal
 * reminder, to open a sort session. They wait here until the app is ready to show it.
 */
@Singleton
class LaunchRequests @Inject constructor() {

    private val _session = MutableStateFlow<String?>(null)

    /** Filter key of the session to open; null when there's none. */
    val session: StateFlow<String?> = _session.asStateFlow()

    fun handle(intent: Intent?) {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        intent.getStringExtra(EXTRA_SESSION)?.let { _session.value = it }
    }

    fun consume() {
        _session.value = null
    }

    companion object {
        private const val ACTION_SORT = "com.sortr.app.action.SORT"
        private const val EXTRA_SESSION = "com.sortr.app.extra.SESSION"

        /** Opens Sortr straight into a session, bringing back the running app if there is one. */
        fun sessionIntent(context: Context, filterKey: String = SessionFilter.AllPhotos.key): Intent =
            openAppIntent(context).setAction(ACTION_SORT).putExtra(EXTRA_SESSION, filterKey)

        /** Opens Sortr where it was, or on Home. */
        fun openAppIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
