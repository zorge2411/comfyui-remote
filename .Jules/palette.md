## 2024-05-23 - [Destructive Action Confirmation]
**Learning:** Users can accidentally delete workflows because the delete action was immediate.
**Action:** Always wrap destructive actions (like delete) in a confirmation dialog to prevent data loss.

## 2024-05-24 - [Form Input Usability]
**Learning:** Text inputs for generative AI prompts are typically long. Single-line inputs frustrate users who can't see their full prompt.
**Action:** Default to multiline (`minLines=3`) and provide a 'Clear' button for text inputs in prompt forms.
