package ai.juanita.app.avatar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/**
 * Full-screen call-style shell. The portrait remains a still image in this
 * phase; the adaptive 3D renderer and PCM/viseme bridge are separate milestones.
 * Closing this window does not stop the independently owned Gemini Live service.
 */
@Composable
fun AvatarCallScreen(
    avatarName: String,
    portraitPath: String,
    connectionStatus: String,
    onDismiss: () -> Unit,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    var cameraEnabled by remember { mutableStateOf(false) }
    var frontCamera by remember { mutableStateOf(true) }
    var cameraError by remember { mutableStateOf<String?>(null) }
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
                    UserCameraPreview(frontCamera = frontCamera, onError = { cameraError = it })
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
                    "Microfono Gemini Live attivo · il ritratto è ancora statico in questa versione",
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
                    TextButton(onClick = onDismiss) { Text("Chiudi schermata", color = Color.White) }
                    Button(
                        onClick = onEndCall,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB83B42),
                            contentColor = Color.White
                        )
                    ) { Text("Termina chiamata") }
                }
                Text(
                    "Chiudere questa schermata non interrompe l'audio. Solo “Termina chiamata” chiude Gemini Live. La videocamera mostra l'anteprima sul dispositivo e non invia immagini a Gemini.",
                    color = Color(0xFFB9BBC1)
                )
            }
        }
    }
}

@Composable
private fun UserCameraPreview(
    frontCamera: Boolean,
    onError: (String?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(frontCamera, lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var boundPreview: Preview? = null
        var disposed = false
        providerFuture.addListener({
            if (!disposed) {
                try {
                    val cameraProvider = providerFuture.get()
                    provider = cameraProvider
                    val preview = Preview.Builder().build()
                    boundPreview = preview
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val selector = if (frontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    // Never call unbindAll(): the call preview owns only this use case.
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
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
                if (preview != null) provider?.unbind(preview)
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}
