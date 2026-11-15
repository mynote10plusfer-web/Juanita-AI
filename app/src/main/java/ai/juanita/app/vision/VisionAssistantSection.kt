package ai.juanita.app.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream

/**
 * User-initiated photo understanding. Images are decoded in memory and are not
 * written to the app's archive, Avatar Studio, or cloud storage.
 */
@Composable
fun VisionAssistantSection(
    panel: Color,
    gold: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Scegli una fotografia dalla galleria e chiedimi cosa vuoi osservare.") }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            status = "Nessuna fotografia selezionata."
        } else {
            scope.launch {
                busy = true
                answer = ""
                status = "Apro e preparo la fotografia…"
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Formato immagine non riconosciuto." }
                            var sample = 1
                            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
                            val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                            BitmapFactory.decodeStream(input, null, options)
                        } ?: error("Impossibile aprire la fotografia selezionata.")
                    }
                    selectedBitmap = bitmap ?: error("Impossibile decodificare la fotografia.")
                    status = "Fotografia pronta. Puoi chiedermi di analizzarla."
                } catch (error: Exception) {
                    selectedBitmap = null
                    status = "Apertura non riuscita: ${error.localizedMessage ?: error.javaClass.simpleName}"
                } finally {
                    busy = false
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
            Text("OCCHI DI JUANITA · ANALISI FOTOGRAFIE", color = gold, fontSize = 12.sp)
            Text(
                "Puoi scegliere una foto dalla galleria. Juanita analizzerà ciò che è effettivamente visibile, senza dare per certi dettagli che la fotografia non permette di verificare.",
                color = Color(0xFFB9BBC1),
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !busy
                ) { Text("Scegli fotografia") }
                if (selectedBitmap != null) {
                    TextButton(onClick = {
                        selectedBitmap = null
                        answer = ""
                        status = "Fotografia rimossa."
                    }, enabled = !busy) { Text("Rimuovi") }
                }
            }
            selectedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Fotografia selezionata per l'analisi",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    contentScale = ContentScale.Fit
                )
            }
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cosa vuoi che osservi?") },
                placeholder = { Text("Per esempio: riconosci la moto e dimmi cosa noti…") },
                minLines = 2,
                maxLines = 4,
                enabled = !busy
            )
            Button(
                onClick = {
                    val image = selectedBitmap ?: return@Button
                    scope.launch {
                        busy = true
                        answer = ""
                        status = "Juanita sta osservando la fotografia…"
                        try {
                            val prompt = question.trim().ifBlank {
                                "Descrivi con precisione ciò che è visibile nell'immagine. Distingui le osservazioni certe dalle ipotesi e indica quali dettagli non sono verificabili."
                            }
                            val response = withTimeout(60_000L) {
                                withContext(Dispatchers.IO) {
                                    val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                                        .generativeModel("gemini-3.8-flash")
                                    model.generateContent(content {
                                        image(image)
                                        text(prompt)
                                    })
                                }
                            }
                            answer = response.text?.takeIf { it.isNotBlank() }
                                ?: "Il modello non ha restituito una descrizione testuale."
                            status = "Analisi completata. La fotografia non è stata salvata nell'archivio."
                        } catch (error: Exception) {
                            status = "Analisi non riuscita: ${error.localizedMessage ?: error.javaClass.simpleName}"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = selectedBitmap != null && !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "ATTENDI…" else "ANALIZZA FOTOGRAFIA") }
            Text(status, color = Color(0xFF9A9DA4), fontSize = 11.sp)
            if (answer.isNotBlank()) {
                Text(answer, color = Color.White, fontSize = 14.sp)
            }
            Text(
                "La foto viene inviata a Gemini solo quando premi Analizza. Non viene aggiunta automaticamente alla memoria o allo Studio Avatar.",
                color = Color(0xFF777D87),
                fontSize = 10.sp
            )
        }
    }
}
