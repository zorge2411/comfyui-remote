package com.example.comfyui_remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.comfyui_remote.data.GallerySyncFilter
import com.example.comfyui_remote.ui.components.DateRangePickerDialog
import com.example.comfyui_remote.ui.components.Dimens
import com.example.comfyui_remote.ui.components.SectionHeader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMM")

/** "28 Sep" in the device's time zone. */
internal fun formatDay(millis: Long): String =
    DAY_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun startOfDay(daysAgo: Long): Long =
    LocalDate.now().minusDays(daysAgo).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Quick date choices; a range that matches none of them is shown as custom. */
private enum class DatePreset(val label: String, val daysAgo: Long?) {
    ANY("Any time", null), TODAY("Today", 0), WEEK("Last 7 days", 6), MONTH("Last 30 days", 29)
}

private fun presetOf(start: Long?, end: Long?): DatePreset? = when {
    start == null && end == null -> DatePreset.ANY
    end != null -> null
    else -> DatePreset.entries.firstOrNull { it.daysAgo != null && startOfDay(it.daysAgo) == start }
}

/** "Last 7 days", "Since 21 Sep", "21 Sep – 28 Sep" or "Until 28 Sep". */
internal fun dateRangeLabel(start: Long?, end: Long?): String? {
    presetOf(start, end)?.let { return if (it == DatePreset.ANY) null else it.label }
    return when {
        start != null && end != null -> "${formatDay(start)} – ${formatDay(end)}"
        start != null -> "Since ${formatDay(start)}"
        end != null -> "Until ${formatDay(end)}"
        else -> null
    }
}

/** The filter's parts as shown on chips: "Last 7 days", "Workflow: krea*", "Videos", "Oldest first". */
internal fun filterParts(filter: GallerySyncFilter): List<String> = listOfNotNull(
    dateRangeLabel(filter.startDate, filter.endDate),
    filter.workflowNameFilter?.takeIf { it.isNotBlank() }?.let { "Workflow: $it" },
    filter.fileNameFilter?.takeIf { it.isNotBlank() }?.let { "File: $it" },
    when (filter.mediaType) {
        GallerySyncFilter.MediaType.IMAGE -> "Images"
        GallerySyncFilter.MediaType.VIDEO -> "Videos"
        null -> null
    },
    when (filter.sortOrder) {
        GallerySyncFilter.SortOrder.OLDEST_FIRST -> "Oldest first"
        GallerySyncFilter.SortOrder.NAME_ASC -> "By name"
        GallerySyncFilter.SortOrder.NEWEST_FIRST -> null
    }
)

/** One line for a saved list or the save dialog. */
internal fun filterSummary(filter: GallerySyncFilter): String =
    filterParts(filter).joinToString(", ").ifBlank { "Everything, newest first" }

/**
 * The gallery's filter (Phase 104): date, workflow and file name, type and sort, applied on the phone.
 * [onSaveAsList] receives the filter as edited here, so an unapplied edit can be saved too.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GalleryFilterSheet(
    current: GallerySyncFilter,
    onApply: (GallerySyncFilter) -> Unit,
    onSaveAsList: (GallerySyncFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var startDate by remember { mutableStateOf(current.startDate) }
    var endDate by remember { mutableStateOf(current.endDate) }
    var workflow by remember { mutableStateOf(current.workflowNameFilter.orEmpty()) }
    var fileName by remember { mutableStateOf(current.fileNameFilter.orEmpty()) }
    var mediaType by remember { mutableStateOf(current.mediaType) }
    var sortOrder by remember { mutableStateOf(current.sortOrder) }
    var pickingRange by remember { mutableStateOf(false) }

    fun edited() = current.copy(
        startDate = startDate,
        endDate = endDate,
        workflowNameFilter = workflow.trim().ifBlank { null },
        fileNameFilter = fileName.trim().ifBlank { null },
        mediaType = mediaType,
        sortOrder = sortOrder
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Dimens.s)
        ) {
            Text("Filter", style = MaterialTheme.typography.titleLarge)

            SectionHeader("Date")
            val preset = presetOf(startDate, endDate)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
                DatePreset.entries.forEach { p ->
                    FilterChip(
                        selected = preset == p,
                        onClick = {
                            startDate = p.daysAgo?.let(::startOfDay)
                            endDate = null
                        },
                        label = { Text(p.label) }
                    )
                }
                FilterChip(
                    selected = preset == null,
                    onClick = { pickingRange = true },
                    label = { Text(if (preset == null) dateRangeLabel(startDate, endDate) ?: "Custom range" else "Custom range…") }
                )
            }

            SectionHeader("Name")
            OutlinedTextField(
                value = workflow,
                onValueChange = { workflow = it },
                label = { Text("Workflow") },
                singleLine = true,
                supportingText = { Text("Use * as a wildcard, - to exclude, ; for several") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("File name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
                listOf(null to "All", GallerySyncFilter.MediaType.IMAGE to "Images", GallerySyncFilter.MediaType.VIDEO to "Videos")
                    .forEach { (type, label) ->
                        FilterChip(selected = mediaType == type, onClick = { mediaType = type }, label = { Text(label) })
                    }
            }

            SectionHeader("Sort")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
                listOf(
                    GallerySyncFilter.SortOrder.NEWEST_FIRST to "Newest",
                    GallerySyncFilter.SortOrder.OLDEST_FIRST to "Oldest",
                    GallerySyncFilter.SortOrder.NAME_ASC to "Name"
                ).forEach { (order, label) ->
                    FilterChip(selected = sortOrder == order, onClick = { sortOrder = order }, label = { Text(label) })
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.m)
            ) {
                TextButton(onClick = {
                    startDate = null
                    endDate = null
                    workflow = ""
                    fileName = ""
                    mediaType = null
                    sortOrder = GallerySyncFilter.SortOrder.NEWEST_FIRST
                }) { Text("Reset") }
                TextButton(onClick = { onSaveAsList(edited()) }) { Text("Save as list…") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onApply(edited()) }) { Text("Apply") }
            }
        }
    }

    if (pickingRange) {
        DateRangePickerDialog(
            onDismiss = { pickingRange = false },
            onDateRangeSelected = { start, end ->
                startDate = start
                endDate = end
                pickingRange = false
            },
            initialStartDate = startDate,
            initialEndDate = endDate
        )
    }
}
