package com.example.comfyui_remote.ui.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.comfyui_remote.domain.InputField
import com.example.comfyui_remote.domain.LabelledField
import com.example.comfyui_remote.domain.NodeGroup
import com.example.comfyui_remote.ui.components.AppCard
import com.example.comfyui_remote.ui.components.Dimens
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme

/** Number fields that sit side by side in Main settings. */
private val PAIRS = listOf("Width" to "Height", "Steps" to "CFG")

/**
 * Main settings: width/height and steps/CFG side by side when both are there, everything else full width.
 * [field] renders one field.
 */
@Composable
fun MainSettings(fields: List<LabelledField>, field: @Composable (LabelledField, Modifier) -> Unit) {
    val byLabel = fields.associateBy { it.label }
    val paired = mutableSetOf<String>()
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.s)) {
        for (f in fields) {
            if (f.label in paired) continue
            val pair = PAIRS.firstOrNull { it.first == f.label }
            val partner = pair?.let { byLabel[it.second] }
            if (partner != null && f.field.isNumber() && partner.field.isNumber()) {
                paired += partner.label
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
                    field(f, Modifier.weight(1f))
                    field(partner, Modifier.weight(1f))
                }
            } else {
                field(f, Modifier)
            }
        }
    }
}

/**
 * A display-only item for the text a display node produced (for example the Ollama prompt): not editable,
 * selectable, with Copy and Show more.
 */
@Composable
fun ResultTextItem(text: String?, modifier: Modifier = Modifier) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(text) { androidx.compose.runtime.mutableStateOf(false) }
    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Text result",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (text != null) {
                IconButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy text result")
                }
            }
        }
        if (text == null) {
            Text(
                "Appears here after a run.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 8,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            if (!expanded && text.lines().size + text.length / 45 > 8) {
                androidx.compose.material3.TextButton(onClick = { expanded = true }) { Text("Show more") }
            }
        }
    }
}

private fun InputField.isNumber() = this is InputField.IntInput || this is InputField.FloatInput

/**
 * A field with a pin beside it (Phase 106): pinned fields show under the prompt. The pin sits outside the
 * field, so it doesn't compete with the field's own trailing icons.
 */
@Composable
fun PinnableField(pinned: Boolean, label: String, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) { content() }
        IconButton(onClick = onToggle) {
            Icon(
                if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (pinned) "Unpin $label" else "Pin $label",
                tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The inputs of one node, collapsed to a header with the node title and input count until tapped. */
@Composable
fun NodeSection(
    group: NodeGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    field: @Composable (LabelledField) -> Unit
) {
    AppCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch).clickable(onClick = onToggle)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(group.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (group.fields.size == 1) "1 input" else "${group.fields.size} inputs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse ${group.title}" else "Expand ${group.title}"
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.s), modifier = Modifier.padding(top = Dimens.s)) {
                group.fields.forEach { field(it) }
            }
        }
    }
}

@Preview
@Composable
private fun NodeSectionPreview() = ComfyUI_front_endTheme(themeMode = 1) {
    val group = NodeGroup(
        "63", "Load CLIP", listOf(
            LabelledField("63/clip_name", InputField.StringInput("63", "clip_name", "qwen_3_4b.safetensors", "Load CLIP"), "Clip name"),
            LabelledField("63/type", InputField.StringInput("63", "type", "lumina2", "Load CLIP"), "Type")
        )
    )
    Column(modifier = Modifier.padding(Dimens.l), verticalArrangement = Arrangement.spacedBy(Dimens.s)) {
        NodeSection(group, expanded = false, onToggle = {}) { Text(it.label) }
        NodeSection(group, expanded = true, onToggle = {}) { Text(it.label) }
    }
}
