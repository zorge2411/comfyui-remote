## 2024-05-23 - [Destructive Action Confirmation]
**Learning:** Users can accidentally delete workflows because the delete action was immediate.
**Action:** Always wrap destructive actions (like delete) in a confirmation dialog to prevent data loss.

## 2024-05-24 - [Image Selection Visibility]
**Learning:** Full-image overlays for "edit" actions obscure user content, which is critical for verification (e.g., img2img inputs).
**Action:** Use unobtrusive corner badges or floating action buttons for edit indicators on image previews.
