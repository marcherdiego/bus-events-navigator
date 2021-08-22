package com.marcherdiego.events.navigator.domain

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter

data class Callees(val subscriberMethod: PsiMethod, val parameter: PsiParameter, val references: MutableList<Callee>) {
    lateinit var constructorReference: PsiMethod
}
