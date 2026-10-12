package com.example.comfyui_remote.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Marks an image as a shared element for navigation transitions (Phase 106). Nothing happens without
 * a [key] or the scopes, so screens work unchanged where no transition is set up.
 *
 * Keys in use: "image-<mediaId>" (gallery grid, viewer pages, form result preview) and
 * "workflow-thumb-<workflowId>" (workflow card thumbnail, form result preview).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedImage(
    key: String?,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
): Modifier {
    if (key == null || sharedTransitionScope == null || animatedVisibilityScope == null) return this
    return with(sharedTransitionScope) {
        this@sharedImage.sharedElement(
            state = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}
