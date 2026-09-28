package br.com.redclaw.swt.module.api

/** Immediate acknowledgement returned by a companion APK launch endpoint. */
data class LaunchResult(
    val requestId: String,
    /** Global id in the form `moduleId:gameId`. */
    val gameId: String,
    val status: Status,
    /** Stable machine-readable code; localized presentation belongs to the host. */
    val messageCode: String? = null,
) {
    init {
        require(requestId.isNotBlank()) { "Request id must not be blank" }
        ModuleGameId.parse(gameId)
        require(messageCode == null || messageCode.matches(MESSAGE_CODE_PATTERN)) {
            "Invalid launch result message code"
        }
    }

    enum class Status {
        ACCEPTED,
        REJECTED,
        MODULE_UNAVAILABLE,
        USER_ACTION_REQUIRED,
    }

    companion object {
        private val MESSAGE_CODE_PATTERN = Regex("[a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)*")
    }
}
