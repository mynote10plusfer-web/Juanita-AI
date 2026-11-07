package ai.juanita.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun CameraVisionSection(
    panel: androidx.compose.ui.graphics.Color,
    gold: androidx.compose.ui.graphics.Color,
    voiceConnected: Boolean,
    visionStatus: String,
    onVideoFrame: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var cameraEnabled by remember { mutableStateOf(false) }
    var liveVision by remember { mutableStateOf(false) }
    var snapshotPending by remember { mutableStateOf(false) }
    var frontCamera by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        cameraEnabled = granted
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = panel)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("VISIONE DI JUANITA", color = gold)
            Text(
                "La videocamera si attiva solo quando la apri. Le immagini vengono inviate a Gemini in tempo reale e non vengono salvate nell'app.",
                color = androidx.compose.ui.graphics.Color(0xFF9A9DA4)
            )
            if (cameraEnabled && permissionGranted) {
                JuanitaCameraPreview(
                    frontCamera = frontCamera,
                    onCameraError = { cameraError = it },
                    onFrame = { frame ->
                        if (voiceConnected && liveVision) {
                            onVideoFrame(frame)
                        } else if (voiceConnected && snapshotPending) {
                            snapshotPending = false
                            onVideoFrame(frame)
                        }
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (frontCamera) "Fotocamera anteriore" else "Fotocamera posteriore", modifier = Modifier.weight(1f))
                    TextButton(onClick = { frontCamera = !frontCamera }) { Text("Inverti", color = gold) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = liveVision, onCheckedChange = {
                        liveVision = it
                        if (it) snapshotPending = false
                    })
                    Text("Visione Live · circa 1 fotogramma al secondo")
                }
                Button(
                    onClick = {
                        if (voiceConnected && !liveVision) snapshotPending = true
                    },
                    enabled = voiceConnected && !liveVision && !snapshotPending,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = androidx.compose.ui.graphics.Color.Black)
                ) {
                    Text(if (snapshotPending) "Acquisizione in corso…" else "Mostra questa immagine a Juanita")
                }
                TextButton(onClick = {
                    cameraEnabled = false
                    liveVision = false
                    snapshotPending = false
                }) { Text("Spegni videocamera", color = gold) }
            } else {
                Button(
                    onClick = {
                        if (permissionGranted) cameraEnabled = true
                        else permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = androidx.compose.ui.graphics.Color.Black)
                ) { Text("Attiva videocamera") }
            }
            Text(
                cameraError ?: if (!voiceConnected) "Per inviare immagini, avvia prima la conversazione vocale Gemini Live." else visionStatus,
                color = if (cameraError != null) androidx.compose.ui.graphics.Color(0xFFE6A0A0) else androidx.compose.ui.graphics.Color(0xFFB9BBC1)
            )
        }
    }
}

@Composable
private fun JuanitaCameraPreview(
    frontCamera: Boolean,
    onCameraError: (String?) -> Unit,
    onFrame: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrame)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner, frontCamera, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        var cameraProvider: ProcessCameraProvider? = null
        var previewUseCase: Preview? = null
        var analysisUseCase: ImageAnalysis? = null
        var disposed = false
        providerFuture.addListener({
            if (!disposed) {
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    previewUseCase = preview
                    analysisUseCase = analysis
                    var lastFrameAt = 0L
                    analysis.setAnalyzer(executor) { image: ImageProxy ->
                        try {
                            val now = System.currentTimeMillis()
                            if (now - lastFrameAt >= 1000L) {
                                lastFrameAt = now
                                runCatching { currentOnFrame(image.toBitmap()) }
                            }
                        } finally {
                            image.close()
                        }
                    }
                    val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    // Unbind only the use cases owned by this preview.
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                    if (!disposed) onCameraError(null)
                } catch (error: Exception) {
                    if (!disposed) onCameraError(error.localizedMessage ?: "Impossibile avviare la fotocamera")
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            runCatching {
                val preview = previewUseCase
                val analysis = analysisUseCase
                if (preview != null && analysis != null) cameraProvider?.unbind(preview, analysis)
            }
            executor.shutdownNow()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxWidth().height(250.dp)
    )
}
