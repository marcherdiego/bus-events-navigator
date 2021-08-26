package com.marcherdiego.events.navigator

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.usages.ReadWriteAccessUsageInfo2UsageAdapter
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter

class FileFilter internal constructor(virtualFile: VirtualFile) : Filter {
    private val file: VirtualFile = virtualFile

    override fun shouldShow(usage: Usage?): Boolean {
        if (usage == null) {
            return false
        }
        return when (usage) {
            is ReadWriteAccessUsageInfo2UsageAdapter -> usage.file != file && isNotImport(usage)
            is UsageInfo2UsageAdapter -> usage.file != file && isNotImport(usage)
            else -> false
        }
    }

    private fun isNotImport(usage: Usage) = usage.toString().contains("|import|").not()
}
