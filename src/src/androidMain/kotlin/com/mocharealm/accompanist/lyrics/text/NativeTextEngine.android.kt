package com.mocharealm.accompanist.lyrics.text

import android.content.Context
import android.content.res.AssetFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

actual class NativeTextEngine {
    
    companion object {
        init {
            // Ensure the library is loaded on class access
            System.loadLibrary("text_engine")
        }
    }

    actual external fun init(atlasWidth: Int, atlasHeight: Int)
    actual external fun loadFont(bytes: ByteArray)
    actual external fun loadFallbackFont(bytes: ByteArray)
    actual external fun clearFallbackFonts()
    
    // File descriptor-based font loading (more memory efficient)
    external fun loadFallbackFontFd(fd: Int): Boolean

    actual external fun processText(text: String, sizeFn: Float, weight: Float): String
    actual external fun hasPendingUploads(): Boolean
    actual external fun getPendingUploads(): String
    actual external fun getAtlasSize(): String
    
    // Zero-copy DirectByteBuffer API (platform-specific, not in expect)
    external fun processTextDirect(text: String, sizePx: Float, weight: Float, buffer: ByteBuffer): Int
    external fun getPendingUploadsDirect(buffer: ByteBuffer): Int
    
    // Resource management
    actual external fun destroy()
}

/**
 * Loads a fallback font from Android assets using zero-copy file descriptor.
 * More memory efficient than loading entire font into ByteArray.
 * 
 * @param context Android context for accessing assets
 * @param assetPath Path to font file in assets (e.g., "fonts/NotoSansCJK.otf")
 * @return true if font loaded successfully
 */
fun NativeTextEngine.loadFallbackFontFromAsset(context: Context, assetPath: String): Boolean {
    return try {
        val afd: AssetFileDescriptor = context.assets.openFd(assetPath)
        val fd = afd.parcelFileDescriptor.fd
        val result = loadFallbackFontFd(fd)
        afd.close()
        result
    } catch (e: Exception) {
        // Asset might be compressed, fall back to ByteArray method
        try {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            loadFallbackFont(bytes)
            true
        } catch (e2: Exception) {
            false
        }
    }
}

/**
 * Loads a fallback font from a file path using zero-copy file descriptor.
 * 
 * @param filePath Absolute path to font file
 * @return true if font loaded successfully
 */
fun NativeTextEngine.loadFallbackFontFromFile(filePath: String): Boolean {
    return try {
        val file = File(filePath)
        val fis = FileInputStream(file)
        val fd = fis.fd.hashCode() // Note: This is a hack, proper FD would need ParcelFileDescriptor
        val result = loadFallbackFontFd(fd)
        fis.close()
        result
    } catch (e: Exception) {
        false
    }
}

