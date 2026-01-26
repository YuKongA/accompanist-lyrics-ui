package com.mocharealm.accompanist.lyrics.text

import java.nio.ByteBuffer

actual class NativeTextEngine {
    
    companion object {
        init {
            try {
                // On JVM/Desktop, standard loading. 
                // Ensuring "text_engine" matches the library name (libtext_engine.so/dll/dylib) on java.library.path
                System.loadLibrary("text_engine")
            } catch (e: UnsatisfiedLinkError) {
                // Log or handle. For now just print to stderr
                System.err.println("NativeTextEngine: Failed to load 'text_engine' library. Make sure it is in java.library.path. Error: ${e.message}")
            }
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
