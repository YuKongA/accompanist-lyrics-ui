package com.mocharealm.accompanist.lyrics.text

actual class NativeTextEngine {
    actual fun init(atlasWidth: Int, atlasHeight: Int) {
        // TODO: iOS/macOS native implementation via cinterop
    }
    
    actual fun loadFont(bytes: ByteArray) {
        // TODO: iOS/macOS native implementation via cinterop
    }
    
    actual fun processText(text: String, sizeFn: Float, weight: Float): String {
        // TODO: iOS/macOS native implementation via cinterop
        return "{}"
    }
    
    actual fun hasPendingUploads(): Boolean {
        // TODO: iOS/macOS native implementation via cinterop
        return false
    }
    
    actual fun getPendingUploads(): String {
        // TODO: iOS/macOS native implementation via cinterop
        return "[]"
    }
    
    actual fun getAtlasSize(): String {
        // TODO: iOS/macOS native implementation via cinterop
        return """{"width":2048,"height":2048}"""
    }
    
    // Resource management
    actual fun destroy() {
        // TODO: iOS/macOS native implementation via cinterop
    }
}
// Note: Zero-copy DirectByteBuffer API not available on Apple platforms.
// Use processText/getPendingUploads JSON API instead.
