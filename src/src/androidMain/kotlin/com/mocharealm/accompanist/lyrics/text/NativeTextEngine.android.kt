package com.mocharealm.accompanist.lyrics.text

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
