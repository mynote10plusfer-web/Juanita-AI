package ai.juanita.app.presence

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads and atomically installs the pinned Spanish Sherpa KWS bundle.
 * Provisioning never starts microphone capture or enables wake listening.
 */
object WakeWordModelInstaller {
    const val MODEL_REVISION = "d4fa6bd"
    const val DEFAULT_PHRASE = WakeWordPhraseCatalog.DEFAULT_PHRASE

    private const val MODEL_ID = "jguerrisi/sherpa-onnx-kws-zipformer-es-3M-2026-08-08"
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 120_000
    private const val BUFFER_SIZE = 64 * 1024
    private val installMutex = Mutex()

    private data class Asset(val name: String, val bytes: Long, val sha256: String)

    // Pinned hashes and sizes were measured from the successful revision-pinned CI bundle.
    private val assets = listOf(
        Asset("encoder-epoch-20-avg-2-chunk-16-left-64.onnx", 12_533_711L, "b97a544b46e0e18fb4a9fc022c3f75256d4e60ab244cc7217dd01819e2d8145f"),
        Asset("decoder-epoch-20-avg-2-chunk-16-left-64.onnx", 1_063_189L, "61e75374f546c07b404f93509157a54b1d2f53d00a648bdd5de08599c711a7ae"),
        Asset("joiner-epoch-20-avg-2-chunk-16-left-64.onnx", 642_462L, "97f105dfa7bb1230f99abd153643106da73e5de7795b40e1a81a836c34eb5c60"),
        Asset("tokens.txt", 5_018L, "556298e4c8e3173076a285f9b9191e2aceb8d476aed02dcb87f25f9db2ac1142"),
        Asset("bpe.model", 247_347L, "21661e7892f33bd7495bea5f319f6edf45eed681c1f5afbc0a275b5d30625838")
    )

    /** Call only after explicit user setup consent. */
    suspend fun install(
        context: Context,
        phrase: String = DEFAULT_PHRASE,
        onProgress: ((fileName: String, downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        installMutex.withLock { installBlocking(context.applicationContext, phrase, onProgress) }
    }

    fun isInstalled(context: Context, phrase: String = DEFAULT_PHRASE): Boolean {
        val definition = WakeWordPhraseCatalog.definition(phrase) ?: return false
        val directory = modelDirectory(context.applicationContext)
        return isValidDirectory(directory) && isKeywordBundleValid(directory, definition)
    }

    private fun installBlocking(
        context: Context,
        phrase: String,
        onProgress: ((String, Long, Long) -> Unit)?
    ): File {
        val definition = WakeWordPhraseCatalog.definition(phrase)
            ?: error("Frase di attivazione non supportata dal modello offline")
        check(WakeWordPhraseCatalog.keywordHashIsValid(definition)) {
            "Pinned SentencePiece keyword definition failed its SHA-256 check"
        }
        val destination = modelDirectory(context)
        if (isValidDirectory(destination)) {
            writeKeywordBundle(destination, definition)
            return destination
        }

        val staging = File(context.filesDir, "wake-models/.staging-$MODEL_REVISION")
        val backup = File(context.filesDir, "wake-models/.previous-$MODEL_REVISION")
        staging.deleteRecursively()
        check(staging.mkdirs() || staging.isDirectory) {
            "Unable to create private wake-model staging directory"
        }

        try {
            val totalBytes = assets.sumOf { it.bytes }
            var completedBytes = 0L
            assets.forEach { asset ->
                downloadAndVerify(asset, File(staging, asset.name)) { downloaded ->
                    onProgress?.invoke(asset.name, completedBytes + downloaded, totalBytes)
                }
                completedBytes += asset.bytes
                onProgress?.invoke(asset.name, completedBytes, totalBytes)
            }

            File(staging, "model-revision.txt").writeText("$MODEL_REVISION\n", Charsets.UTF_8)
            writeKeywordBundle(staging, definition)

            backup.deleteRecursively()
            if (destination.exists()) {
                check(destination.renameTo(backup)) {
                    "Unable to preserve the previous wake-model installation"
                }
            }
            if (!staging.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                error("Unable to atomically activate the verified wake-model bundle")
            }
            check(isValidDirectory(destination)) {
                "Installed wake-model bundle failed its final integrity check"
            }
            backup.deleteRecursively()
            return destination
        } catch (error: Throwable) {
            staging.deleteRecursively()
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            throw error
        }
    }

    private fun downloadAndVerify(
        asset: Asset,
        destination: File,
        onBytes: (Long) -> Unit
    ) {
        val url = URL("https://huggingface.co/$MODEL_ID/resolve/$MODEL_REVISION/${asset.name}")
        val temporary = File(destination.parentFile, "${asset.name}.part")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "JuanitaAI-WakeModel/$MODEL_REVISION")
        }

        try {
            val response = connection.responseCode
            check(response == HttpURLConnection.HTTP_OK) {
                "Model download failed for ${asset.name}: HTTP $response"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        check(count <= asset.bytes) {
                            "Downloaded file exceeds pinned size: ${asset.name}"
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        onBytes(count)
                    }
                    output.fd.sync()
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            check(count == asset.bytes) {
                "Wrong file size for ${asset.name}: expected ${asset.bytes}, received $count"
            }
            check(actualHash == asset.sha256) { "SHA-256 mismatch for ${asset.name}" }
            check(temporary.renameTo(destination)) {
                "Unable to finalize downloaded model file ${asset.name}"
            }
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    private fun isValidDirectory(directory: File): Boolean {
        if (!directory.isDirectory) return false
        val filesValid = assets.all { asset ->
            val file = File(directory, asset.name)
            file.isFile && file.length() == asset.bytes &&
                runCatching { sha256(file) == asset.sha256 }.getOrDefault(false)
        }
        val revisionValid = runCatching {
            File(directory, "model-revision.txt").readText(Charsets.UTF_8).trim() == MODEL_REVISION
        }.getOrDefault(false)
        return filesValid && revisionValid
    }

    private fun isKeywordBundleValid(directory: File, definition: WakeWordPhraseCatalog.Definition): Boolean =
        runCatching {
            val keywordFile = File(directory, SherpaOfflineWakeWordDetector.KEYWORDS_FILE)
            val phraseFile = File(directory, SherpaOfflineWakeWordDetector.KEYWORD_MANIFEST_FILE)
            keywordFile.isFile &&
                keywordFile.readText(Charsets.UTF_8) == definition.keywordLine &&
                sha256(keywordFile) == definition.keywordSha256 &&
                phraseFile.isFile &&
                phraseFile.readText(Charsets.UTF_8).trim() == definition.phrase
        }.getOrDefault(false)

    private fun writeKeywordBundle(
        directory: File,
        definition: WakeWordPhraseCatalog.Definition
    ) {
        val keywordFile = File(directory, SherpaOfflineWakeWordDetector.KEYWORDS_FILE)
        val phraseFile = File(directory, SherpaOfflineWakeWordDetector.KEYWORD_MANIFEST_FILE)
        replaceAtomically(keywordFile, definition.keywordLine)
        replaceAtomically(phraseFile, "${definition.phrase}\n")
        check(isKeywordBundleValid(directory, definition)) {
            "Generated offline keyword bundle failed its integrity check"
        }
    }

    private fun replaceAtomically(target: File, content: String) {
        val temporary = File(target.parentFile, "${target.name}.part")
        val backup = File(target.parentFile, "${target.name}.previous")
        temporary.delete()
        backup.delete()
        FileOutputStream(temporary).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (target.exists()) {
            check(target.renameTo(backup)) { "Unable to preserve previous ${target.name}" }
        }
        if (!temporary.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            error("Unable to atomically activate ${target.name}")
        }
        backup.delete()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun modelDirectory(context: Context): File =
        File(context.filesDir, "wake-models/es-zipformer-$MODEL_REVISION")
}
