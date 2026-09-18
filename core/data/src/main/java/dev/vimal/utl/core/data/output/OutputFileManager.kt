package dev.vimal.utl.core.data.output

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages output file creation and MediaStore registration.
 * Target directory: Movies/ViMal (Scoped storage on Android 10+ / API 29+).
 */
object OutputFileManager {

    private const val OUTPUT_FOLDER = "ViMal"

    /**
     * Create a temporary working file in internal cache.
     */
    fun createTempFile(context: Context, suffix: String): File {
        val tempDir = File(context.cacheDir, "vimal_temp").apply { mkdirs() }
        return File.createTempFile("vimal_", suffix, tempDir)
    }

    /**
     * Register the final muxed output file into MediaStore Movies/ViMal.
     *
     * @param sourceName  Original file name (used to derive output name).
     * @param tempFile    Temp file containing the final muxed output.
     * @return Uri of the MediaStore entry (used for sharing / playback).
     */
    suspend fun publishToMediaStore(
        context: Context,
        sourceName: String,
        tempFile: File,
    ): Uri = withContext(Dispatchers.IO) {
        val outputName = buildOutputName(sourceName)

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, outputName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$OUTPUT_FOLDER")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collectionUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collectionUri, values)
            ?: error("Failed to create MediaStore entry for $outputName")

        resolver.openOutputStream(itemUri, "w")?.use { out ->
            tempFile.inputStream().use { input ->
                input.copyTo(out)
            }
            out.flush()
        } ?: error("Failed to open output stream for $itemUri")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        }

        // Trigger media scan so the file appears immediately in Gallery and file managers
        try {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val vimalDir = File(moviesDir, OUTPUT_FOLDER)
            val destFile = File(vimalDir, outputName)
            if (destFile.exists()) {
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("video/mp4"), null)
            }
        } catch (_: Exception) {}

        itemUri
    }

    /** Clean up temp files created during processing. */
    fun cleanupTempFiles(vararg files: File) {
        files.forEach {
            try {
                if (it.exists()) it.delete()
            } catch (_: Exception) {}
        }
    }

    private fun buildOutputName(sourceName: String): String {
        val base = sourceName.substringBeforeLast(".")
        val timestamp = System.currentTimeMillis()
        return "${base}_vimal_normalized_$timestamp.mp4"
    }
}
