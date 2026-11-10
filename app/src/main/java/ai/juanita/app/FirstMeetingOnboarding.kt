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

/** A short introduction and assistant identity choice, not a questionnaire. */
@Composable
fun FirstMeetingOnboarding(
    initialGender: String,
    onComplete: (String) -> Unit,
    onSkip: () -> Unit
) {
    val gold = Color(0xFFE4BD69)
    var selectedGender by remember {
        mutableStateOf(if (initialGender == "MASCHILE") "MASCHILE" else "FEMMINILE")
    }
    val selectedName = if (selectedGender == "MASCHILE") "José" else "Juanita"
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
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("PIACERE DI CONOSCERTI", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Chi vuoi al tuo fianco?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Scegli l'identità del tuo assistente. Potrai cambiare idea in qualsiasi momento dalle impostazioni: ricordi, conversazioni e sveglie resteranno intatti.",
                    color = Color(0xFFB9BBC1), fontSize = 14.sp, lineHeight = 21.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { selectedGender = "FEMMINILE" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedGender == "FEMMINILE") gold else Color(0xFF30343B),
                            contentColor = if (selectedGender == "FEMMINILE") Color.Black else Color.White
                        )
                    ) { Text("Juanita\nFemminile") }
                    Button(
                        onClick = { selectedGender = "MASCHILE" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedGender == "MASCHILE") gold else Color(0xFF30343B),
                            contentColor = if (selectedGender == "MASCHILE") Color.Black else Color.White
                        )
                    ) { Text("José\nMaschile") }
                }
                Text(
                    "$selectedName userà un'identità coerente con il nome e una voce Gemini adatta. Potrai personalizzare la voce in seguito.",
                    color = Color(0xFF8F949D), fontSize = 12.sp
                )
                Text(
                    "Non devi compilare nessun questionario. Al primo dialogo ti chiederò come preferisci essere chiamato. Il resto lo scoprirò poco a poco, ascoltandoti e ricordando ciò che conta.",
                    color = Color(0xFFB9BBC1), fontSize = 14.sp, lineHeight = 21.sp
                )
                Button(
                    onClick = { onComplete(selectedGender) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
                ) { Text("Va bene, iniziamo") }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onSkip) { Text("Più tardi", color = Color(0xFFB9BBC1)) }
                }
            }
        }
    }
}
