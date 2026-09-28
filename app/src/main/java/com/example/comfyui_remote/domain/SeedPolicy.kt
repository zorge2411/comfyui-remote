package com.example.comfyui_remote.domain

/** Seeds for one run (Phase 101): a Fixed seed is sent as typed, every other seed gets a new random value. */
object SeedPolicy {

    /** Returns the inputs to send and the seed used per field key ("nodeId/fieldName"). */
    fun resolve(inputs: List<InputField>, random: () -> Long): Pair<List<InputField>, Map<String, Long>> {
        val used = LinkedHashMap<String, Long>()
        val resolved = inputs.map { field ->
            if (field is InputField.SeedInput) {
                val seed = if (field.fixed == true) field.value else random()
                used[field.key] = seed
                field.copy(value = seed)
            } else {
                field
            }
        }
        return resolved to used
    }
}
