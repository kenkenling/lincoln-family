package com.lkeducation.lkpractice

import java.io.OutputStream

fun interface ProfileArchiveDestination {
    fun open(uriText: String): OutputStream
}
