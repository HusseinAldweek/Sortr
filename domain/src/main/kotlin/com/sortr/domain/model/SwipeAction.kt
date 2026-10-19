package com.sortr.domain.model

/** The four decisions a user can make on a card. */
enum class SwipeAction(val resultStatus: PhotoStatus, val actionType: ActionType) {
    KEEP(PhotoStatus.KEPT, ActionType.KEEP),
    DELETE(PhotoStatus.TRASHED, ActionType.DELETE),
    PRIVATE(PhotoStatus.VAULTED, ActionType.PRIVATE),
    SKIP(PhotoStatus.SKIPPED, ActionType.SKIP),
}

/** Everything recorded in the action log, used for undo and stats. */
enum class ActionType {
    KEEP,
    DELETE,
    PRIVATE,
    SKIP,

    /** Taken back out of the in-app Trash. */
    RESTORE,

    /** Deleted from the device after the system confirmation. */
    PURGE,
}

/** A decision that has been written to the database and can be undone. */
data class Decision(
    val actionId: Long,
    val photo: Photo,
    val action: SwipeAction,
    val previousStatus: PhotoStatus,
)
