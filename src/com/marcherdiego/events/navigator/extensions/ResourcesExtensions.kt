package com.marcherdiego.events.navigator.extensions

import java.io.BufferedReader
import java.io.InputStreamReader

fun Any.getResourceAsString(path: String): String {
    val fileContents = StringBuilder()
    BufferedReader(InputStreamReader(javaClass.getResourceAsStream(path)))
        .lines()
        .forEach { line ->
            fileContents
                .append(line)
                .append(System.lineSeparator())
        }
    return fileContents.toString()
}
