package com.example.comfyui_remote.ui.form

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.example.comfyui_remote.domain.InputField
import com.example.comfyui_remote.domain.LabelledField
import com.example.comfyui_remote.ui.components.Dimens
import com.example.comfyui_remote.ui.theme.ComfyUI_front_endTheme
import kotlin.random.Random

/** The prompt: multi-line, with Copy and Clear (Phase 101). */
@Composable
fun PromptField(field: LabelledField, onChange: (InputField) -> Unit, modifier: Modifier = Modifier) {
    val input = field.field as? InputField.StringInput ?: return
    val clipboard = LocalClipboardManager.current
    OutlinedTextField(
        value = input.value,
        onValueChange = { onChange(input.copy(value = it)) },
        label = { Text(field.label) },
        minLines = 3,
        maxLines = 10,
        trailingIcon = if (input.value.isNotEmpty()) {
            {
                Column {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(input.value)) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy text")
                    }
                    IconButton(onClick = { onChange(input.copy(value = "")) }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear text")
                    }
                }
            }
        } else null,
        modifier = modifier.fillMaxWidth()
    )
}

/** Any other text input: up to three lines, with Clear. */
@Composable
fun TextInputField(field: LabelledField, onChange: (InputField) -> Unit, modifier: Modifier = Modifier) {
    val input = field.field as? InputField.StringInput ?: return
    OutlinedTextField(
        value = input.value,
        onValueChange = { onChange(input.copy(value = it)) },
        label = { Text(field.label) },
        maxLines = 3,
        trailingIcon = if (input.value.isNotEmpty()) {
            { IconButton(onClick = { onChange(input.copy(value = "")) }) { Icon(Icons.Filled.Clear, contentDescription = "Clear text") } }
        } else null,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * An Int or Float input that keeps what you type: "1." or an empty field are allowed while editing, and the
 * value only changes when the text is a number (the field shows an error until then).
 */
@Composable
fun NumberField(field: LabelledField, onChange: (InputField) -> Unit, modifier: Modifier = Modifier) {
    val input = field.field
    val current = when (input) {
        is InputField.IntInput -> input.value.toString()
        is InputField.FloatInput -> input.value.toString()
        else -> return
    }
    var text by rememberSaveable(field.key) { mutableStateOf(current) }
    // Follow outside changes (reset, remembered values) unless they match what is being typed
    LaunchedEffect(current) {
        val typed = when (input) {
            is InputField.IntInput -> text.toIntOrNull()?.toString()
            is InputField.FloatInput -> text.toFloatOrNull()?.toString()
            else -> null
        }
        if (typed != current) text = current
    }
    val valid = when (input) {
        is InputField.IntInput -> text.toIntOrNull() != null
        else -> text.toFloatOrNull() != null
    }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            when (input) {
                is InputField.IntInput -> new.toIntOrNull()?.let { onChange(input.copy(value = it)) }
                is InputField.FloatInput -> new.toFloatOrNull()?.let { onChange(input.copy(value = it)) }
                else -> Unit
            }
        },
        label = { Text(field.label) },
        isError = !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (input is InputField.IntInput) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Next
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * A seed with a Random / Fixed choice (Phase 101). Random gets a new value on every run; Fixed is sent as
 * typed. [lastUsed] is the seed the latest run sent, which "Use" pins.
 */
@Composable
fun SeedField(field: LabelledField, lastUsed: Long?, onChange: (InputField) -> Unit, modifier: Modifier = Modifier) {
    val input = field.field as? InputField.SeedInput ?: return
    val fixed = input.fixed == true
    var text by rememberSaveable(field.key) { mutableStateOf(input.value.toString()) }
    LaunchedEffect(input.value) {
        if (text.toLongOrNull() != input.value) text = input.value.toString()
    }
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                text = new
                new.toLongOrNull()?.let { onChange(input.copy(value = it)) }
            },
            label = { Text(field.label) },
            isError = text.toLongOrNull() == null,
            singleLine = true,
            enabled = fixed,
            supportingText = if (!fixed) {
                { Text("A new random seed is used for every run") }
            } else null,
            trailingIcon = {
                IconButton(onClick = { onChange(input.copy(value = Random.nextLong(1, Long.MAX_VALUE), fixed = true)) }) {
                    Icon(Icons.Filled.Casino, contentDescription = "New random seed")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Dimens.xs)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(
                    selected = !fixed,
                    onClick = { onChange(input.copy(fixed = false)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Random") }
                SegmentedButton(
                    selected = fixed,
                    onClick = { onChange(input.copy(fixed = true)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Fixed") }
            }
        }
        if (lastUsed != null && !(fixed && lastUsed == input.value)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Last used: $lastUsed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onChange(input.copy(value = lastUsed, fixed = true)) }) { Text("Use") }
            }
        }
    }
}

/** A dropdown for combo inputs; [options] overrides the field's own list (legacy ModelInput). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionField(
    field: LabelledField,
    options: List<String>,
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(field.label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}

@Preview(name = "Seed, random")
@Composable
private fun SeedFieldPreview() = ComfyUI_front_endTheme(themeMode = 1) {
    Column(modifier = Modifier.padding(Dimens.l)) {
        SeedField(
            LabelledField("3/seed", InputField.SeedInput("3", "seed", 42, "KSampler"), "Seed"),
            lastUsed = 5366767094581782405,
            onChange = {}
        )
    }
}

@Preview(name = "Seed, fixed, dark")
@Composable
private fun SeedFieldFixedPreview() = ComfyUI_front_endTheme(themeMode = 2) {
    Column(modifier = Modifier.padding(Dimens.l)) {
        SeedField(
            LabelledField("3/seed", InputField.SeedInput("3", "seed", 42, "KSampler", fixed = true), "Seed"),
            lastUsed = null,
            onChange = {}
        )
    }
}
