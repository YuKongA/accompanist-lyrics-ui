package com.mocharealm.accompanist.lyrics.text

expect class NativeTextEngine() {
    fun init(atlasWidth: Int, atlasHeight: Int)
    fun loadFont(bytes: ByteArray)
    fun processText(text: String, sizeFn: Float, weight: Float = 400f): String
    fun hasPendingUploads(): Boolean
    fun getPendingUploads(): String
    fun getAtlasSize(): String
    
    // Resource management
    fun destroy()
}
// Note: Zero-copy DirectByteBuffer API (processTextDirect, getPendingUploadsDirect) 
// is available only on JVM/Android platforms via platform-specific extensions.
