package com.marcherdiego.events.navigator.extensions

import com.intellij.openapi.application.ApplicationManager

fun <T> Any.readAction(block: () -> T): T? {
    var result: T? = null
    ApplicationManager.getApplication().runReadAction {
        result = block()
    }
    return result
}
