package com.example.comfyui_remote.domain.corpus

import com.example.comfyui_remote.data.ComfyObjectInfo
import com.example.comfyui_remote.domain.GraphToApiConverter
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Converts every workflow in src/test/resources/workflow-corpus/workflows and checks the result
 * with ApiPromptValidator. Fixtures that fail today are listed in known-failures.json; the list
 * is enforced both ways, so a fix must also remove its entry. See workflow-corpus/MANIFEST.md.
 */
class WorkflowCorpusTest {

    private fun resource(path: String): File {
        val url = javaClass.classLoader!!.getResource("workflow-corpus/$path")
            ?: error("missing test resource workflow-corpus/$path")
        return File(url.toURI())
    }

    private fun readJson(file: File): JsonObject = JsonParser.parseString(file.readText()).asJsonObject

    @Test
    fun `every corpus workflow converts to a structurally valid prompt or is a known failure`() {
        val objectInfo = readJson(resource("object_info.json"))
        val known = readJson(resource("known-failures.json"))
        val fixtures = resource("workflows").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
        assertTrue("no corpus fixtures found", fixtures.isNotEmpty())

        val problems = mutableListOf<String>()
        for (file in fixtures) {
            val violations = try {
                val text = file.readText()
                val api = JsonParser.parseString(
                    GraphToApiConverter.convert(text, ComfyObjectInfo(objectInfo)).json
                ).asJsonObject
                ApiPromptValidator.validate(api, objectInfo, JsonParser.parseString(text).asJsonObject)
            } catch (e: Exception) {
                listOf(ApiPromptValidator.Violation("C0", "-", "converter threw $e"))
            }
            val failing = violations.map { it.check }.toSortedSet()
            val expected = known.getAsJsonObject(file.name)
                ?.getAsJsonArray("checks")?.map { it.asString }?.toSortedSet()
            val details = violations.take(5).joinToString("") { "\n      $it" } +
                if (violations.size > 5) "\n      ... ${violations.size - 5} more" else ""

            when {
                expected == null && failing.isNotEmpty() ->
                    problems += "REGRESSION ${file.name}: fails $failing$details"
                expected != null && failing != expected ->
                    problems += "STALE known-failures entry ${file.name}: expected $expected, now fails $failing " +
                        "(update known-failures.json)$details"
            }
        }
        val names = fixtures.map { it.name }.toSet()
        known.keySet().filter { it !in names }.forEach {
            problems += "known-failures.json lists $it, which is not in workflows/"
        }

        if (problems.isNotEmpty()) {
            fail("${problems.size} corpus problem(s) across ${fixtures.size} fixtures:\n" + problems.joinToString("\n"))
        }
    }
}
