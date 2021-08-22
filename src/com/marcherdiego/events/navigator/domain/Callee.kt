package com.marcherdiego.events.navigator.domain

import com.intellij.psi.PsiFile

data class Callee(val referencingElementFile: PsiFile, val referenceLine: Int)
