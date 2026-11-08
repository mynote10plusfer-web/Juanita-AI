package ai.juanita.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A short introduction, not a questionnaire. The first meeting happens in voice. */
@Composable
fun FirstMeetingOnboarding(onComplete: (List<String>) -> Unit, onSkip: () -> Unit) {
    val gold = Color(0xFFE4BD69)
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            color = Color(0xFF17191D),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("PIACERE DI CONOSCERTI", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Cominciamo con calma.", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Non devi compilare nessun questionario. Quando parleremo, ti chiederò soltanto le informazioni fondamentali, una alla volta. Il resto lo scoprirò poco a poco, ascoltandoti e ricordando ciò che conta. Se non vorrai rispondere a qualcosa, andrà bene lo stesso.",
                    color = Color(0xFFB9BBC1), fontSize = 14.sp, lineHeight = 21.sp
                )
                Text("Potrai correggere o chiedere a Juanita di dimenticare un'informazione in qualsiasi momento.", color = Color(0xFF8F949D), fontSize = 12.sp)
                Button(onClick = { onComplete(emptyList()) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)) {
                    Text("Va bene, iniziamo")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onSkip) { Text("Più tardi", color = Color(0xFFB9BBC1)) }
                }
            }
        }
    }
}
