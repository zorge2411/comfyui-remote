package com.example.comfyui_remote.domain

/** A form field with the label the form shows for it. [key] is "nodeId/fieldName". */
data class LabelledField(val key: String, val field: InputField, val label: String)

/** The inputs of one node, shown as a collapsible section. */
data class NodeGroup(val nodeId: String, val title: String, val fields: List<LabelledField>)

/** How the workflow form is laid out (Phase 101): prompts, main settings, then the rest per node. */
data class FormModel(
    val prompt: LabelledField?,
    val negative: LabelledField?,
    val main: List<LabelledField>,
    val groups: List<NodeGroup>
)

object FormLayout {

    private data class Role(val names: Set<String>, val label: String)

    /** Main settings, in the order the form shows them. */
    private val MAIN_ROLES = listOf(
        Role(setOf("seed", "noise_seed"), "Seed"),
        Role(setOf("width"), "Width"),
        Role(setOf("height"), "Height"),
        Role(setOf("steps"), "Steps"),
        Role(setOf("cfg"), "CFG"),
        Role(setOf("sampler_name"), "Sampler"),
        Role(setOf("scheduler"), "Scheduler"),
        Role(setOf("denoise"), "Denoise"),
        Role(setOf("batch_size"), "Batch size"),
        Role(setOf("ckpt_name"), "Checkpoint"),
        Role(setOf("unet_name"), "Model")
    )

    private val PROMPT_FIELDS = setOf("text", "prompt")

    /**
     * Splits [inputs] into the prompt and negative prompt (string fields of the conditioning source nodes),
     * main settings, and per-node groups. A main role is promoted only when exactly one field in the workflow
     * has it; with two samplers, their steps stay in the node groups so the form never guesses.
     */
    fun build(inputs: List<InputField>, positiveNodeId: String?, negativeNodeId: String?): FormModel {
        fun promptOf(nodeId: String?, label: String): LabelledField? {
            if (nodeId == null) return null
            val field = inputs.firstOrNull {
                it.nodeId == nodeId && it is InputField.StringInput && it.fieldName in PROMPT_FIELDS
            } ?: return null
            return LabelledField(field.key, field, label)
        }

        val prompt = promptOf(positiveNodeId, "Prompt")
        val negative = promptOf(negativeNodeId, "Negative prompt").takeIf { it?.key != prompt?.key }
        val taken = mutableSetOf<String>()
        prompt?.let { taken += it.key }
        negative?.let { taken += it.key }

        val main = mutableListOf<LabelledField>()
        for (role in MAIN_ROLES) {
            val matches = inputs.filter { it.fieldName in role.names && it.key !in taken }
            if (matches.size == 1) {
                val field = matches.single()
                main += LabelledField(field.key, field, role.label)
                taken += field.key
            }
        }

        val groups = LinkedHashMap<String, MutableList<LabelledField>>()
        val titles = HashMap<String, String>()
        for (field in inputs) {
            if (field.key in taken) continue
            groups.getOrPut(field.nodeId) { mutableListOf() } += LabelledField(field.key, field, humanize(field.fieldName))
            titles.putIfAbsent(field.nodeId, field.nodeTitle)
        }
        return FormModel(
            prompt = prompt,
            negative = negative,
            main = main,
            groups = groups.map { (id, fields) -> NodeGroup(id, titles[id] ?: id, fields) }
        )
    }

    /** "filename_prefix" → "Filename prefix"; dotted dynamic-combo inputs "format.codec" → "Format › codec". */
    fun humanize(fieldName: String): String {
        val text = fieldName.split('.').joinToString(" › ") { it.replace('_', ' ').trim() }
        return text.replaceFirstChar { it.uppercaseChar() }
    }
}
