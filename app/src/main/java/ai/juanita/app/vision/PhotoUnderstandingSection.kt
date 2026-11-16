package ai.juanita.app.vision

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream

/**
 * Optional, user-initiated photo understanding.
 *
 * The Android Photo Picker grants access only to the selected image. Nothing is uploaded
 * until the user presses "Invia a Juanita"; the image is held in memory for this view only.
 */
@OptIn(PublicPreviewAPI::class)
@Composable
fun PhotoUnderstandingSection(
    panel: Color,
    accent: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Puoi scegliere una foto dalla galleria e chiedermi cosa vuoi sapere.") }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedUri = uri
        selectedBitmap = null
        answer = ""
        if (uri == null) {
            status = "Nessuna foto selezionata."
        } else {
            status = "Caricamento della foto…"
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    runCatching { decodePhoto(context.contentResolver, uri) }.getOrNull()
                }
                selectedBitmap = decoded
                status = if (decoded == null) {
                    "Non riesco ad aprire questa foto. Prova con un'altra immagine."
                } else {
                    "Foto pronta. Scrivi una domanda oppure inviala così com'è."
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = panel)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("MOSTRA UNA FOTO A JUANITA", color = accent, fontSize = 13.sp)
            Text(
                "Scegli una foto dal telefono: potrò osservarla e rispondere alle tue domande.",
                color = Color(0xFFB9BBC1),
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = accent)
                ) { Text(if (selectedUri == null) "Scegli una foto" else "Cambia foto") }
                if (selectedUri != null) {
                    Button(
                        onClick = {
                            selectedUri = null
                            selectedBitmap = null
                            answer = ""
                            question = ""
                            status = "Foto rimossa."
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = Color.White)
                    ) { Text("Rimuovi") }
                }
            }
            selectedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Anteprima della foto selezionata",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    contentScale = ContentScale.Fit
                )
            }
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cosa vuoi chiedermi? (facoltativo)") },
                placeholder = { Text("Ad esempio: cosa noti in questa foto?") },
                enabled = !busy,
                minLines = 1,
                maxLines = 3
            )
            Button(
                onClick = {
                    val bitmap = selectedBitmap ?: return@Button
                    busy = true
                    answer = ""
                    status = "Sto osservando la foto…"
                    scope.launch {
                        try {
                            val result = withTimeoutOrNull(PHOTO_ANALYSIS_TIMEOUT_MS) {
                                withContext(Dispatchers.IO) {
                                    val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                                        .generativeModel("gemini-3.8-flash")
                                    val prompt = content {
                                        image(bitmap)
                                        text(
                                            buildString {
                                                append("Sei Juanita, l'assistente personale dell'utente. Analizza attentamente la fotografia. ")
                                                append("Descrivi solo ciò che è realmente visibile; distingui osservazioni e ipotesi, ")
                                                append("non inventare identità, dettagli tecnici o contesto non dimostrabili. ")
                                                append("Rispondi in italiano, in modo naturale e conversazionale. ")
                                                append("Domanda dell'utente: ")
                                                append(question.ifBlank { "Che cosa noti in questa fotografia?" })
                                            }
                                        )
                                    }
                                    model.generateContent(prompt).text?.takeIf { it.isNotBlank() }
                                        ?: "Non sono riuscita a ottenere una descrizione della foto."
                                }
                            }
                            if (result == null) {
                                status = "Gemini non ha risposto entro 45 secondi. Controlla la connessione e premi Riprova."
                            } else {
                                answer = result
                                status = "Analisi completata."
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            status = "Analisi non riuscita: ${error.localizedMessage ?: "controlla la connessione e riprova"}"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = selectedBitmap != null && !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
            ) {
                Text(if (busy) "Analisi in corso…" else if (status.contains("Riprova") || status.startsWith("Analisi non riuscita")) "Riprova analisi" else "Invia la foto a Juanita")
            }
            Text(status, color = Color(0xFF9A9DA4), fontSize = 11.sp)
            if (answer.isNotBlank()) {
                Text("JUANITA", color = accent, fontSize = 12.sp)
                Text(answer, color = Color.White, fontSize = 14.sp)
            }
            Text(
                "La foto viene inviata a Gemini solo quando premi il pulsante. Non viene salvata nella Memoria o nell'Avatar.",
                color = Color(0xFF777D87),
                fontSize = 10.sp
            )
        }
    }
}

private const val PHOTO_ANALYSIS_TIMEOUT_MS = 45_000L

private fun decodePhoto(resolver: ContentResolver, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val maxDimension = 1536
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return resolver.openInputStream(uri)?.use { input: InputStream ->
        BitmapFactory.decodeStream(input, null, options)
    }
}


@Composable
fun VisualSearchResultCard(
    result: VisualSearchResult?,
    status: String,
    accent: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    if (result == null && status.isBlank()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17191D))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("RICERCA VISIVA DI JUANITA", color = accent, fontSize = 13.sp)
            if (status.isNotBlank()) Text(status, color = Color(0xFFB9BBC1), fontSize = 12.sp)
            result?.let { found ->
                Text(found.title, color = Color.White, fontSize = 14.sp)
                Image(
                    bitmap = found.bitmap.asImageBitmap(),
                    contentDescription = "Fotografia trovata da Juanita",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    contentScale = ContentScale.Fit
                )
                Text(found.analysis, color = Color.White, fontSize = 14.sp)
                Text("${found.provider} · ${found.attribution}", color = Color(0xFF9A9DA4), fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(found.sourceUrl)))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                    ) { Text("Apri fonte originale") }
                    TextButton(onClick = onDismiss) { Text("Chiudi", color = accent) }
                }
            }
        }
    }
}
