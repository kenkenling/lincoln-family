package com.practicepartner.music

import java.io.OutputStream

fun interface ProfileArchiveDestination {
    fun open(uriText: String): OutputStream
}
