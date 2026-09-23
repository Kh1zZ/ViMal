package dev.vimal.utl.core.domain.model

import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoInfoTest {

    private fun createVideoInfo(
        width: Int,
        height: Int,
        durationMs: Long = 30_000L,
        fileSizeBytes: Long = 40_000_000L,
    ): VideoInfo {
        val uri = mockk<Uri>(relaxed = true)
        return VideoInfo(
            uri = uri,
            fileName = "sample.mp4",
            durationMs = durationMs,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            mimeType = "video/mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            measuredLufs = null,
        )
    }

    @Test
    fun `portrait 9-16 1080x1920 scales to 720x1280 for P720`() {
        val info = createVideoInfo(width = 1080, height = 1920)
        val (w, h) = info.getTargetResolution(VideoResolutionPreset.P720)
        assertEquals(720, w)
        assertEquals(1280, h)
        assertTrue(info.isCompressionNeeded(VideoResolutionPreset.P720))
    }

    @Test
    fun `portrait 9-16 1080x1920 scales to 540x960 for P540`() {
        val info = createVideoInfo(width = 1080, height = 1920)
        val (w, h) = info.getTargetResolution(VideoResolutionPreset.P540)
        assertEquals(540, w)
        assertEquals(960, h)
        assertTrue(info.isCompressionNeeded(VideoResolutionPreset.P540))
    }

    @Test
    fun `landscape 16-9 1920x1080 scales to 1280x720 for P720`() {
        val info = createVideoInfo(width = 1920, height = 1080)
        val (w, h) = info.getTargetResolution(VideoResolutionPreset.P720)
        assertEquals(1280, w)
        assertEquals(720, h)
        assertTrue(info.isCompressionNeeded(VideoResolutionPreset.P720))
    }

    @Test
    fun `square 1080x1080 scales to 720x720 for P720 and 540x540 for P540`() {
        val info = createVideoInfo(width = 1080, height = 1080)
        val (w720, h720) = info.getTargetResolution(VideoResolutionPreset.P720)
        assertEquals(720, w720)
        assertEquals(720, h720)

        val (w540, h540) = info.getTargetResolution(VideoResolutionPreset.P540)
        assertEquals(540, w540)
        assertEquals(540, h540)
    }

    @Test
    fun `odd dimensions are always rounded to even numbers for H264`() {
        val info = createVideoInfo(width = 1081, height = 1923)
        val (w, h) = info.getTargetResolution(VideoResolutionPreset.P720)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
    }

    @Test
    fun `video smaller than target is not upscaled`() {
        val info = createVideoInfo(width = 480, height = 854)
        val (w, h) = info.getTargetResolution(VideoResolutionPreset.P720)
        assertEquals(480, w)
        assertEquals(854, h)
        assertFalse(info.isCompressionNeeded(VideoResolutionPreset.P720))
    }

    @Test
    fun `estimated size is calculated proportionally based on duration and bitrate`() {
        // 30 seconds at P720: (2000 + 128) kbps * 30s / 8 = 7,980,000 bytes (~7.98 MB)
        val info = createVideoInfo(width = 1080, height = 1920, durationMs = 30_000L)
        val estimatedBytes = info.getEstimatedSizeBytes(VideoResolutionPreset.P720)
        val expected = (30.0 * 2128 * 1000 / 8).toLong()
        assertEquals(expected, estimatedBytes)
    }
}
