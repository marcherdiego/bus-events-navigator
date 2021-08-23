package com.marcherdiego.events.navigator

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiMethod
import com.intellij.usages.ReadWriteAccessUsageInfo2UsageAdapter
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter

class FileFilter internal constructor(method: PsiMethod) : Filter {
    private val file: VirtualFile = method.containingFile.virtualFile

    override fun shouldShow(usage: Usage?): Boolean {
        return when (usage) {
            is ReadWriteAccessUsageInfo2UsageAdapter -> usage.file != file
            is UsageInfo2UsageAdapter -> usage.file != file
            else -> false
        }
    }
}
