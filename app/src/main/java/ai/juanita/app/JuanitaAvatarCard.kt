package ai.juanita.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Local portrait slot for Juanita. The user selects the previously generated
 * portrait once; the app copies it into private internal storage. No cloud
 * upload, external storage permission, or paid image service is used.
 */
@Composable
fun JuanitaAvatarCard() {
    val context = LocalContext.current
    val portraitFile = remember { File(context.filesDir, "juanita_portrait.jpg") }
    var portrait by remember {
        mutableStateOf<Bitmap?>(if (portraitFile.exists()) BitmapFactory.decodeFile(portraitFile.absolutePath) else null)
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    portraitFile.outputStream().use { output -> input.copyTo(output) }
                }
                BitmapFactory.decodeFile(portraitFile.absolutePath)
            }.onSuccess { bitmap -> portrait = bitmap }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF17191D))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("IL VOLTO DI JUANITA", color = Color(0xFFE4BD69), fontWeight = FontWeight.Bold)
        if (portrait != null) {
            Image(
                bitmap = portrait!!.asImageBitmap(),
                contentDescription = "Ritratto di Juanita",
                modifier = Modifier.size(width = 224.dp, height = 276.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )
            Text("Ritratto salvato in modo privato sul telefono", color = Color(0xFF9A9DA4))
        } else {
            Box(
                modifier = Modifier.size(width = 224.dp, height = 276.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF242830)),
                contentAlignment = Alignment.Center
            ) {
                Text("Seleziona il ritratto originale", color = Color(0xFFB9BBC1))
            }
        }
        Button(
            onClick = { imagePicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE4BD69),
                contentColor = Color.Black
            )
        ) {
            Text(if (portrait == null) "IMPORTA IL VOLTO DI JUANITA" else "CAMBIA RITRATTO")
        }
        Text(
            "La foto resta sul dispositivo: non viene caricata su Firebase né inviata a Gemini.",
            color = Color(0xFF9A9DA4)
        )
    }
}
