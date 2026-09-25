package com.example.comfyui_remote.domain

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * A workflow template from the server's template library (the comfyui-workflow-templates package
 * ComfyUI serves at /templates/). Thumbnails and workflow files live next to /templates/index.json.
 */
data class WorkflowTemplate(
    val name: String,
    val title: String,
    val description: String,
    val mediaSubtype: String,
    val tags: List<String>,
    val models: List<String>,
    /** False for templates that call paid API nodes and need a Comfy account. */
    val openSource: Boolean
) {
    /** Path of the workflow file, relative to the server root. */
    val workflowPath: String get() = "templates/$name.json"

    /** First thumbnail, as the ComfyUI frontend builds it (useTemplateWorkflows.getTemplateThumbnailUrl). */
    fun thumbnailUrl(serverBaseUrl: String): String =
        "${serverBaseUrl.trimEnd('/')}/templates/$name-1.$mediaSubtype"
}

data class TemplateCategory(
    val title: String,
    /** Group heading, e.g. "Foundation" or "Applied". */
    val group: String,
    val templates: List<WorkflowTemplate>
)

object WorkflowTemplateIndex {

    const val INDEX_FILE = "index.json"

    /** Parses /templates/index.json; entries without a name are skipped. */
    fun parse(json: String): List<TemplateCategory> {
        val root = JsonParser.parseString(json)
        if (!root.isJsonArray) return emptyList()
        return root.asJsonArray.mapNotNull { el ->
            val category = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val templates = category.getAsJsonArray("templates")?.mapNotNull { t ->
                t.takeIf { it.isJsonObject }?.asJsonObject?.let(::parseTemplate)
            }.orEmpty()
            if (templates.isEmpty()) return@mapNotNull null
            TemplateCategory(
                title = category.string("title") ?: "Templates",
                group = category.string("category") ?: "",
                templates = templates
            )
        }
    }

    private fun parseTemplate(t: JsonObject): WorkflowTemplate? {
        val name = t.string("name")?.takeIf { it.isNotBlank() } ?: return null
        return WorkflowTemplate(
            name = name,
            title = t.string("title") ?: name,
            description = t.string("description") ?: "",
            mediaSubtype = t.string("mediaSubtype") ?: "webp",
            tags = t.strings("tags"),
            models = t.strings("models"),
            openSource = t.get("openSource")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
        )
    }

    /** Templates matching every word of [query] in title, description, tags or models; optionally one category. */
    fun filter(categories: List<TemplateCategory>, query: String, category: String? = null): List<WorkflowTemplate> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return categories
            .filter { category == null || it.title == category }
            .flatMap { it.templates }
            .distinctBy { it.name }
            .filter { t ->
                val text = (listOf(t.title, t.description, t.name) + t.tags + t.models).joinToString(" ").lowercase()
                words.all { it in text }
            }
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.strings(key: String): List<String> =
        getAsJsonArray(key)?.mapNotNull { e: JsonElement -> e.takeIf { it.isJsonPrimitive }?.asString }.orEmpty()
}
