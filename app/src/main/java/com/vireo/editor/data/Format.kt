package com.vireo.editor.data

import java.util.Locale

fun Long.asTimecode(withMillis: Boolean = false): String {
    val totalSec = this / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return if (withMillis) {
        val cs = (this % 1000) / 100
        String.format(Locale.US, "%02d:%02d.%d", m, s, cs)
    } else String.format(Locale.US, "%02d:%02d", m, s)
}

fun Long.asFileSize(): String {
    val mb = this / 1_048_576.0
    return if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024)
    else String.format(Locale.US, "%.0f MB", mb)
}
