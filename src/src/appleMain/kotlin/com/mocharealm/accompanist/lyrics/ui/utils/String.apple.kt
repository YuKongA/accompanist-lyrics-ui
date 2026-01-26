package com.mocharealm.accompanist.lyrics.ui.utils

import platform.Foundation.NSCharacterSet

actual fun String.isPunctuation(): Boolean {
    if (this.isEmpty()) return false
    val customPunctuation = ".,!?;:\"'()[]{}…—–-、。，！？；：\"\"''（）【】《》～·"
    if (customPunctuation.contains(this)) return true

    this.firstOrNull()?.code?.toUInt()?.let { utf32Code ->
        return NSCharacterSet.punctuationCharacterSet.longCharacterIsMember(utf32Code)
    }
    return false
}