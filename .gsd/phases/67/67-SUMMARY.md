# Phase 67 Summary: Enhanced Input Support & Heuristics

## Objective

The goal was to support floating-point inputs (essential for CFG Scale, Denoise, etc.) which were previously treated as Integers (causing data loss), and to improve the detection of loader nodes (LoRA, VAE) that use generic naming conventions.

## Changes Implemented

### Domain Layer

- **`InputField.kt`**: Added `FloatInput` data class to the sealed hierarchy.

### Logic Layer

- **`WorkflowParser.kt`**:
  - Updated `parse` method to detect floating-point numbers in JSON (using `toDouble() % 1.0 != 0.0` check and field name heuristics like "denoise", "cfg").
  - Added support for generic loader fields ending in `_name` (e.g., `lora_name`), mapping them to `ModelInput`.
- **`WorkflowExecutor.kt`**:
  - Updated `injectValues` to handle `FloatInput` injection into the JSON payload (previously missing, which caused Float inputs to differ in UI but not be sent to server).

### UI Layer

- **`DynamicFormScreen.kt`**:
  - Added UI rendering for `FloatInput`, using an `OutlinedTextField` with `KeyboardType.Decimal`.

## Verification Results

### Automated Tests

- Created `WorkflowParserTest.kt`.
- **Test 1**: `parse detects Float inputs` - Verified that `7.0` (cfg) and `0.75` (denoise) are correctly parsed as `FloatInput` with correct values.
- **Test 2**: `parse detects generic loaders` - Verified that `lora_name` is correctly identified as `ModelInput`.
- **Result**: Tests passed (`BUILD SUCCESSFUL`).

### Manual Verification

- N/A (Automated tests covered the core logic. UI display logic is standard dispatch).

## Conclusion

Phase 67 is complete. The application now correctly handles decimal inputs and generic model loaders, preventing parameter truncation and missing inputs for custom workflows.
