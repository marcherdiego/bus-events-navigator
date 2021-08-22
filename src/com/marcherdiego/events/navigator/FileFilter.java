package com.marcherdiego.events.navigator;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.usages.ReadWriteAccessUsageInfo2UsageAdapter;
import com.intellij.usages.Usage;

public class FileFilter implements Filter {

    private final VirtualFile file;

    FileFilter(PsiMethod method) {
        this.file = method.getContainingFile().getVirtualFile();
    }

    @Override
    public boolean shouldShow(Usage usage) {
        VirtualFile usageFile = ((ReadWriteAccessUsageInfo2UsageAdapter) usage).getFile();
        return !usageFile.equals(file);
    }
}
