package com.marcherdiego.events.navigator

import com.intellij.usages.Usage

interface Filter {
    fun shouldShow(usage: Usage?): Boolean
}
