package com.mocharealm.accompanist.lyrics.ui.utils

expect fun Char.isCjk(): Boolean
expect fun Char.isArabic(): Boolean
expect fun Char.isDevanagari(): Boolean

fun String.isPureCjk(): Boolean {
    val cleanedStr = this.filter { it != ' ' && it != ',' && it != '\n' && it != '\r' }
    if (cleanedStr.isEmpty()) {
        return false
    }
    return cleanedStr.all { it.isCjk() }
}

fun String.isRtl(): Boolean {
    return any { it.isArabic() }
}

expect fun String.isPunctuation(): Boolean