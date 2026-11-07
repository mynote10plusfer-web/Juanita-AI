package ai.juanita.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Optional first-meeting profile. Only answers explicitly submitted by the user
 * are passed to the encrypted personal-memory store. Every field may be left blank.
 */
@Composable
fun FirstMeetingOnboarding(
    onComplete: (List<String>) -> Unit,
    onSkip: () -> Unit
) {
    var preferredName by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var languages by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var lifestyle by remember { mutableStateOf("") }
    var goals by remember { mutableStateOf("") }
    var support by remember { mutableStateOf("") }
    val gold = Color(0xFFE4BD69)
    val scroll = rememberScrollState()

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            color = Color(0xFF17191D),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("PIACERE DI CONOSCERTI", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Cominciamo da te.", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Queste informazioni aiutano Juanita a conoscerti e a ricordare ciò che conta. Compila solo quello che vuoi: puoi saltare tutto, correggere o cancellare i ricordi in seguito.",
                    color = Color(0xFFB9BBC1), fontSize = 13.sp
                )

                ProfileField("Come preferisci essere chiamato?", preferredName, { preferredName = it })
                ProfileField("Dove vivi? (facoltativo)", location, { location = it })
                ProfileField("Quali lingue parli?", languages, { languages = it })
                ProfileField("Passioni e interessi", interests, { interests = it }, minLines = 2)
                ProfileField("Com'è la tua vita quotidiana? Cosa è importante per te?", lifestyle, { lifestyle = it }, minLines = 3)
                ProfileField("Progetti e obiettivi che vuoi raggiungere", goals, { goals = it }, minLines = 2)
                ProfileField("In cosa vorresti che Juanita ti aiutasse più spesso?", support, { support = it }, minLines = 2)

                Text(
                    "La compilazione è facoltativa. I dati inviati vengono salvati nella memoria personale cifrata sul dispositivo; non inserire password o dati che non vuoi conservare.",
                    color = Color(0xFF8F949D), fontSize = 11.sp
                )
                Button(
                    onClick = {
                        val entries = buildList {
                            preferredName.trim().takeIf { it.isNotBlank() }?.let { add("Il nome con cui l'utente preferisce essere chiamato è $it.") }
                            location.trim().takeIf { it.isNotBlank() }?.let { add("Luogo di residenza dichiarato dall'utente: $it.") }
                            languages.trim().takeIf { it.isNotBlank() }?.let { add("Lingue dichiarate dall'utente: $it.") }
                            interests.trim().takeIf { it.isNotBlank() }?.let { add("Passioni e interessi dichiarati: $it.") }
                            lifestyle.trim().takeIf { it.isNotBlank() }?.let { add("Informazioni sulla vita quotidiana e sulle priorità dichiarate: $it.") }
                            goals.trim().takeIf { it.isNotBlank() }?.let { add("Progetti e obiettivi dichiarati: $it.") }
                            support.trim().takeIf { it.isNotBlank() }?.let { add("Ambiti in cui l'utente desidera supporto da Juanita: $it.") }
                        }
                        onComplete(entries)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
                ) { Text("Salva e conosciamoci") }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onSkip) { Text("Lo farò più tardi", color = Color(0xFFB9BBC1)) }
                }
            }
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        maxLines = if (minLines == 1) 2 else 5,
        singleLine = minLines == 1
    )
}
