package dev.vimal.utl.core.domain.model

import android.net.Uri

/**
 * Abstraction for the video input source.
 * LocalUri covers MVP (file picker). DownloadedCache is reserved for Phase 2 downloader.
 */
sealed class MediaSource {
    data class LocalUri(val uri: Uri) : MediaSource()
    // Phase 2: data class DownloadedCache(val path: String) : MediaSource()
}
