package com.sortr.data.faces

import android.content.Context
import android.util.Log
import com.sortr.data.di.ApplicationScope
import com.sortr.domain.model.FaceModelState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user-supplied face model in assets/models/. Checked once in the background at startup, so
 * screens can explain a missing or unusable file instead of failing silently.
 */
@Singleton
class FaceModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val lock = Any()
    private var embedder: FaceEmbedder? = null

    private val _state = MutableStateFlow(if (assetExists()) FaceModelState.READY else FaceModelState.MISSING)
    val state: StateFlow<FaceModelState> = _state.asStateFlow()

    init {
        if (_state.value == FaceModelState.READY) {
            scope.launch(Dispatchers.Default) { runCatching { embedder() } }
        }
    }

    /** Loads the model on first use. Throws if it's missing or unusable. */
    fun embedder(): FaceEmbedder = synchronized(lock) {
        embedder?.let { return it }
        check(_state.value != FaceModelState.MISSING) { "Face model file is missing" }
        try {
            FaceEmbedder.load(context.assets, ASSET_PATH).also {
                embedder = it
                Log.i(TAG, "Face model loaded: input ${it.inputSize}×${it.inputSize}, embedding ${it.embeddingSize}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Face model can't be used", e)
            _state.value = FaceModelState.UNSUPPORTED
            throw e
        }
    }

    private fun assetExists(): Boolean =
        runCatching { context.assets.list(ASSET_DIR)?.contains(ASSET_FILE) == true }.getOrDefault(false)

    companion object {
        private const val TAG = "FaceModel"
        const val ASSET_DIR = "models"
        const val ASSET_FILE = "mobilefacenet.tflite"
        const val ASSET_PATH = "$ASSET_DIR/$ASSET_FILE"
    }
}
