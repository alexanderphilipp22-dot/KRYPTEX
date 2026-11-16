package com.kryptex.vault.ui.gesture

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Hosts a CameraX [PreviewView]. Merely composing it does not open the camera: it only hands out a
 * surface provider, which the GlanceEngine uses when (and only when) the user taps "Start camera".
 *
 * COMPATIBLE mode renders into a TextureView so the preview can be clipped to a circle. The front
 * camera preview is mirrored, like a mirror.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onSurfaceProvider: (Preview.SurfaceProvider) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                onSurfaceProvider(surfaceProvider)
            }
        },
    )
}
