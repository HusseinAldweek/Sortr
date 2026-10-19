package com.sortr.domain.model

/** A face's position as fractions (0–1) of the photo's displayed width and height. */
data class FaceBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class FaceRef(
    val faceId: Long,
    val photoId: Long,
    val photoUri: String,
    val box: FaceBox,
    val personId: Long?,
)

data class Person(
    val id: Long,
    val name: String?,
    val photoCount: Int,
    val faceCount: Int,
    val hidden: Boolean,
    val cover: FaceRef?,
)

data class FaceIndexProgress(val indexed: Int, val total: Int) {
    val remaining: Int get() = (total - indexed).coerceAtLeast(0)
    val isComplete: Boolean get() = remaining == 0
}

enum class FaceModelState {
    READY,

    /** No model file in the app. */
    MISSING,

    /** A model file is there, but its inputs or outputs aren't what face grouping needs. */
    UNSUPPORTED,
}
