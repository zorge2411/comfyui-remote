package com.example.comfyui_remote.domain.corpus

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.example.comfyui_remote.domain.ApiPromptValidator
import com.example.comfyui_remote.domain.GraphToApiConverter
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in report over every official workflow template (not a pass/fail test). Skipped unless
 * COMFY_TEMPLATES_DIR points to an unzipped comfyui_workflow_templates_json/templates directory.
 * COMFY_OBJECT_INFO may point to a full /object_info dump; the trimmed corpus snapshot only knows
 * the fixtures' node types. Writes build/reports/all-templates.txt. See workflow-corpus/MANIFEST.md.
 */
class AllTemplatesReportTest {

    @Test
    fun `report violations across all templates`() {
        val dir = System.getenv("COMFY_TEMPLATES_DIR")?.let(::File)
        assumeTrue("COMFY_TEMPLATES_DIR not set", dir?.isDirectory == true)

        val objectInfoFile = System.getenv("COMFY_OBJECT_INFO")?.let(::File)
            ?: File(javaClass.classLoader!!.getResource("workflow-corpus/object_info.json")!!.toURI())
        val objectInfo = JsonParser.parseString(objectInfoFile.readText()).asJsonObject

        val perCheck = sortedMapOf<String, Int>()
        val perTemplate = mutableListOf<Pair<String, List<ApiPromptValidator.Violation>>>()
        var graphs = 0
        var clean = 0
        for (file in dir!!.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }) {
            val graph = runCatching { JsonParser.parseString(file.readText()) }.getOrNull()
                ?.takeIf { it.isJsonObject }?.asJsonObject
            if (graph?.get("nodes")?.isJsonArray != true) continue // index files and other non-graph JSON
            graphs++
            val violations = try {
                val api: JsonObject = JsonParser.parseString(
                    GraphToApiConverter.convert(file.readText(), ComfyObjectInfo(objectInfo)).json
                ).asJsonObject
                ApiPromptValidator.validate(api, objectInfo, graph)
            } catch (e: Exception) {
                listOf(ApiPromptValidator.Violation("C0", "-", "converter threw $e"))
            }
            if (violations.isEmpty()) clean++
            violations.forEach { perCheck.merge(it.check, 1, Int::plus) }
            if (violations.isNotEmpty()) perTemplate += file.name to violations
        }

        val report = buildString {
            appendLine("All-template report: ${dir.path}")
            appendLine("object_info: ${objectInfoFile.path}")
            appendLine("Graph workflows: $graphs, passing every check: $clean")
            perCheck.forEach { (check, n) -> appendLine("  $check: $n") }
            appendLine()
            perTemplate.sortedByDescending { it.second.size }.take(TOP).forEach { (name, v) ->
                appendLine("$name (${v.size})")
                v.take(5).forEach { appendLine("    $it") }
            }
        }
        // The converter logs to stdout; keep the report findable in the test output and on disk.
        println("=== ALL_TEMPLATES_REPORT ===\n$report=== END_REPORT ===")
        File("build/reports").apply { mkdirs() }.resolve("all-templates.txt").writeText(report)
    }

    private companion object {
        const val TOP = 40
    }
}
