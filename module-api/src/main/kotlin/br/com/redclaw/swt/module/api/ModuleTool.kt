package br.com.redclaw.swt.module.api

/** A module-specific tool that the host can expose in its own UI. */
data class ModuleTool(
    val id: String,
    val title: String,
    val actionId: String,
    val iconId: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Tool id must not be blank" }
        require(title.isNotBlank()) { "Tool title must not be blank" }
        require(actionId.isNotBlank()) { "Tool action id must not be blank" }
    }
}
