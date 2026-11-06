package ai.juanita.app.avatar

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AvatarStudioSection(
    panel: Color,
    gold: Color,
    initialProfiles: List<AvatarProfile>,
    initialActiveProfileId: String?,
    onSaveProfile: (AvatarProfile) -> AvatarProfile,
    onSetActiveProfile: (String) -> Boolean,
    onDeleteProfile: (String) -> Unit
) {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(initialProfiles) }
    var activeProfileId by remember { mutableStateOf(initialActiveProfileId) }
    var displayName by remember { mutableStateOf("Juanita") }
    var description by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf(AvatarCreationSource.DESCRIPTION) }
    var style by remember { mutableStateOf(AvatarRenderStyle.REALISTIC_3D) }
    var motion by remember { mutableStateOf(AvatarMotionLevel.NATURAL) }
    var sourceMenu by remember { mutableStateOf(false) }
    var styleMenu by remember { mutableStateOf(false) }
    var motionMenu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Scegli come vuoi creare il tuo avatar.") }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            photoUri = uri.toString()
            source = AvatarCreationSource.PHOTO_REFERENCE
            status = "Fotografia selezionata come riferimento. Rimane sul dispositivo."
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = panel),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("AVATAR STUDIO", color = gold)
            Text(
                "Crea e salva i profili del tuo assistente. In questa prima fase salviamo descrizione, stile e riferimento fotografico; il motore che genera il modello animato verrà collegato in una fase successiva.",
                color = Color(0xFF9A9DA4)
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it.take(AvatarProfile.MAX_NAME_LENGTH) },
                label = { Text("Nome dell'avatar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Creazione", color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = { sourceMenu = true }) {
                    Text(source.label(), color = gold)
                }
                DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                    AvatarCreationSource.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label()) },
                            onClick = {
                                source = item
                                sourceMenu = false
                                if (item != AvatarCreationSource.PHOTO_REFERENCE) photoUri = null
                            }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aspetto", color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = { styleMenu = true }) { Text(style.label(), color = gold) }
                DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                    AvatarRenderStyle.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label()) },
                            onClick = { style = item; styleMenu = false }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Movimenti", color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = { motionMenu = true }) { Text(motion.label(), color = gold) }
                DropdownMenu(expanded = motionMenu, onDismissRequest = { motionMenu = false }) {
                    AvatarMotionLevel.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label()) },
                            onClick = { motion = item; motionMenu = false }
                        )
                    }
                }
            }

            if (source == AvatarCreationSource.PHOTO_REFERENCE) {
                Button(
                    onClick = { photoPicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30343B), contentColor = gold)
                ) { Text(if (photoUri == null) "Scegli una fotografia" else "Cambia fotografia") }
                Text(
                    if (photoUri == null) "Seleziona una fotografia dal dispositivo."
                    else "Fotografia di riferimento selezionata.",
                    color = Color(0xFF9A9DA4)
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(AvatarProfile.MAX_PROMPT_LENGTH) },
                label = { Text("Descrivi l'aspetto dell'avatar") },
                supportingText = { Text("${description.length}/${AvatarProfile.MAX_PROMPT_LENGTH}") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        source = AvatarCreationSource.RANDOM
                        photoUri = null
                        val hair = listOf("capelli lunghi e mossi", "capelli corti e ricci", "capelli lisci alle spalle", "capelli raccolti")
                        val eyes = listOf("occhi verdi", "occhi castani", "occhi azzurri", "occhi color nocciola")
                        val look = listOf("lineamenti mediterranei", "lineamenti nord-europei", "lineamenti ispano-europei", "lineamenti delicati e morbidi")
                        description = "${hair.random()}, ${eyes.random()}, ${look.random()}, abbigliamento elegante e naturale."
                        status = "Aspetto casuale creato. Puoi modificarlo prima di salvarlo."
                    }
                ) { Text("Genera casuale", color = gold) }
                TextButton(onClick = {
                    source = AvatarCreationSource.DESCRIPTION
                    photoUri = null
                    description = ""
                    status = "Descrizione libera selezionata."
                }) { Text("Nuova descrizione", color = gold) }
            }

            Button(
                onClick = {
                    val profile = runCatching {
                        AvatarProfile(
                            displayName = displayName.ifBlank { "Avatar" },
                            creationSource = source,
                            renderStyle = style,
                            motionLevel = motion,
                            appearancePrompt = description,
                            referencePhotoUri = photoUri
                        ).validated()
                    }.getOrNull()
                    if (profile == null) {
                        status = "Controlla il nome e la descrizione prima di salvare."
                    } else if (source == AvatarCreationSource.PHOTO_REFERENCE && photoUri.isNullOrBlank()) {
                        status = "Seleziona prima una fotografia."
                    } else {
                        val saved = onSaveProfile(profile)
                        profiles = (profiles.filterNot { it.id == saved.id } + saved)
                        activeProfileId = saved.id
                        status = "Profilo salvato sul dispositivo. Il modello animato non è ancora generato."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color.Black)
            ) { Text("Salva profilo avatar") }

            Text(status, color = Color(0xFFB9BBC1))

            if (profiles.isNotEmpty()) {
                Text("PROFILI SALVATI", color = gold)
                profiles.sortedByDescending { it.updatedAtEpochMillis }.forEach { profile ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.displayName, color = Color.White)
                            Text(
                                "${profile.renderStyle.label()} · ${profile.creationSource.label()}",
                                color = Color(0xFF9A9DA4)
                            )
                        }
                        if (activeProfileId == profile.id) {
                            Text("Attivo", color = gold)
                        } else {
                            TextButton(onClick = {
                                if (onSetActiveProfile(profile.id)) {
                                    activeProfileId = profile.id
                                    status = "Profilo ${profile.displayName} selezionato."
                                }
                            }) { Text("Usa", color = gold) }
                        }
                        TextButton(onClick = {
                            onDeleteProfile(profile.id)
                            profiles = profiles.filterNot { it.id == profile.id }
                            if (activeProfileId == profile.id) activeProfileId = null
                            status = "Profilo eliminato."
                        }) { Text("Elimina", color = Color(0xFFE58A82)) }
                    }
                }
            }
        }
    }
}

private fun AvatarCreationSource.label(): String = when (this) {
    AvatarCreationSource.DESCRIPTION -> "Da descrizione"
    AvatarCreationSource.PHOTO_REFERENCE -> "Da fotografia"
    AvatarCreationSource.RANDOM -> "Casuale"
}

private fun AvatarRenderStyle.label(): String = when (this) {
    AvatarRenderStyle.PHOTOREALISTIC -> "Fotorealistico"
    AvatarRenderStyle.REALISTIC_3D -> "3D realistico"
    AvatarRenderStyle.STYLIZED_3D -> "3D stilizzato"
    AvatarRenderStyle.CARTOON -> "Cartone animato"
}

private fun AvatarMotionLevel.label(): String = when (this) {
    AvatarMotionLevel.SUBTLE -> "Discreto"
    AvatarMotionLevel.NATURAL -> "Naturale"
    AvatarMotionLevel.EXPRESSIVE -> "Espressivo"
}
