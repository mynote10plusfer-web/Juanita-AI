package ai.juanita.app.avatar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Full-screen call UI. Dismissing this window also ends the voice session so
 * the microphone cannot remain active behind a closed call screen. The portrait
 * currently has subtle idle motion; facial animation and true lip sync remain separate milestones.
 */
@Composable
fun AvatarCallScreen(
    avatarName: String,
    portraitPath: String,
    connectionStatus: String,
    voiceConnected: Boolean,
    onVideoFrame: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraEnabled by remember { mutableStateOf(false) }
    var frontCamera by remember { mutableStateOf(true) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var deviceDateTime by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                deviceDateTime = ZonedDateTime.now()
                delay(1_000L)
            }
        }
    }
    val cameraPermissionGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted.value = granted
        cameraEnabled = granted
    }
    val portrait: Bitmap? = remember(portraitPath) {
        val file = File(portraitPath)
        if (!file.exists()) null else runCatching {
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }.getOrNull()
    }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF090A0C))
        ) {
            if (portrait != null) {
                // Gentle idle motion makes the portrait feel present without pretending
                // that this is facial animation or true audio lip-sync.
                val idleMotion = rememberInfiniteTransition(label = "juanita-idle")
                val idleScale by idleMotion.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.018f,
                    animationSpec = infiniteRepeatable(tween(2600), repeatMode = RepeatMode.Reverse),
                    label = "breathing-scale"
                )
                val idleTilt by idleMotion.animateFloat(
                    initialValue = -0.25f,
                    targetValue = 0.25f,
                    animationSpec = infiniteRepeatable(tween(3400), repeatMode = RepeatMode.Reverse),
                    label = "idle-tilt"
                )
                Image(
                    bitmap = portrait.asImageBitmap(),
                    contentDescription = "Ritratto di $avatarName",
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = idleScale
                        scaleY = idleScale
                        rotationZ = idleTilt
                    },
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
            } else {
                Box(
                    Modifier.fillMaxSize().background(Color(0xFF20242B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Importa il ritratto di Juanita dalla schermata principale", color = Color.White)
                }
            }

            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(avatarName.uppercase(), color = Color.White)
                Text("MODALITÀ CHIAMATA · ANTEPRIMA", color = Color(0xFFE4BD69))
                Text(deviceDateTime.format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm:ss", Locale.getDefault())) + " · " + deviceDateTime.zone.id, color = Color.White)
                Text(connectionStatus, color = Color.White)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 86.dp, end = 16.dp)
                    .size(width = 116.dp, height = 174.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
                    .background(Color(0xFF242830))
            ) {
                if (cameraEnabled) {
                    UserCameraPreview(
                        frontCamera = frontCamera,
                        voiceConnected = voiceConnected,
                        onError = { cameraError = it },
                        onFrame = onVideoFrame
                    )
                    if (cameraError != null) {
                        Text(
                            cameraError.orEmpty(),
                            color = Color.White,
                            modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xCC8B2028)).padding(4.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("La tua\nvideocamera\nè spenta", color = Color.White)
                        TextButton(
                            onClick = {
                                if (cameraPermissionGranted.value) cameraEnabled = true
                                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        ) { Text("Attiva", color = Color(0xFFE4BD69)) }
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (voiceConnected) "Gemini Live attivo · invio video a Juanita (1 fotogramma/sec)" else "Avvia prima la conversazione vocale per farti vedere da Juanita",
                    color = Color.White
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (cameraEnabled) cameraEnabled = false
                            else if (cameraPermissionGranted.value) cameraEnabled = true
                            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF30343B),
                            contentColor = Color.White
                        )
                    ) { Text(if (cameraEnabled) "Spegni camera" else "Attiva camera") }

                    if (cameraEnabled) {
                        Button(
                            onClick = { frontCamera = !frontCamera },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF30343B),
                                contentColor = Color.White
                            )
                        ) { Text("Inverti") }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                ) {
                    TextButton(onClick = onDismiss) { Text("Termina e chiudi", color = Color.White) }
                    Button(
                        onClick = onEndCall,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB83B42),
                            contentColor = Color.White
                        )
                    ) { Text("Termina chiamata") }
                }
                Text(
                    "La fotocamera invia a Gemini un fotogramma al secondo mentre la chiamata Live è attiva. Le immagini non vengono salvate nell'app.",
                    color = Color(0xFFB9BBC1)
                )
            }
        }
    }
}

@Composable
private fun UserCameraPreview(
    frontCamera: Boolean,
    voiceConnected: Boolean,
    onError: (String?) -> Unit,
    onFrame: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentVoiceConnected by rememberUpdatedState(voiceConnected)
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(frontCamera, lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        var provider: ProcessCameraProvider? = null
        var boundPreview: Preview? = null
        var boundAnalysis: ImageAnalysis? = null
        var disposed = false
        providerFuture.addListener({
            if (!disposed) {
                try {
                    val cameraProvider = providerFuture.get()
                    provider = cameraProvider
                    val preview = Preview.Builder().build()
                    boundPreview = preview
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    boundAnalysis = analysis
                    var lastFrameAt = 0L
                    analysis.setAnalyzer(executor) { image: ImageProxy ->
                        try {
                            val now = System.currentTimeMillis()
                            if (currentVoiceConnected && now - lastFrameAt >= 1000L) {
                                lastFrameAt = now
                                runCatching {
                                    // ImageAnalysis buffers are not automatically rotated like PreviewView.
                                    // Apply CameraX's per-frame rotation before sending the image to Gemini.
                                    val rawBitmap = image.toBitmap()
                                    val rotationDegrees = image.imageInfo.rotationDegrees
                                    val uprightBitmap = if (rotationDegrees == 0) {
                                        rawBitmap
                                    } else {
                                        val matrix = Matrix().apply {
                                            postRotate(rotationDegrees.toFloat())
                                        }
                                        Bitmap.createBitmap(
                                            rawBitmap,
                                            0,
                                            0,
                                            rawBitmap.width,
                                            rawBitmap.height,
                                            matrix,
                                            true
                                        ).also { rotated ->
                                            if (rotated !== rawBitmap) rawBitmap.recycle()
                                        }
                                    }
                                    currentOnFrame(uprightBitmap)
                                }.onFailure {
                                    if (!disposed) onError("Invio video non riuscito")
                                }
                            }
                        } finally {
                            image.close()
                        }
                    }
                    val selector = if (frontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                    if (!disposed) onError(null)
                } catch (error: Exception) {
                    if (!disposed) onError(
                        error.localizedMessage ?: "Impossibile avviare la fotocamera"
                    )
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            runCatching {
                val preview = boundPreview
                val analysis = boundAnalysis
                if (preview != null && analysis != null) provider?.unbind(preview, analysis)
            }
            executor.shutdownNow()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}
