package com.example.comfyui_remote.domain

/** Rules for local-queue items that can't progress on their own (Phase 98). Statuses are QueueStatus names. */
object QueueRecovery {
    const val INTERRUPTED_MESSAGE = "Interrupted: the app closed while this was running. Retry it."
    const val NO_SERVER_MESSAGE = "No server saved. Set the server address first."

    /** EXECUTING with nothing currently running it: the app was killed mid-run. */
    fun isStale(status: String, itemId: Long, currentItemId: Long?): Boolean =
        status == "EXECUTING" && itemId != currentItemId

    fun canRetry(status: String): Boolean = status == "FAILED"

    /** Deletable unless it is the item running right now. */
    fun canDelete(status: String, itemId: Long, currentItemId: Long?): Boolean =
        status != "EXECUTING" || isStale(status, itemId, currentItemId)
}
