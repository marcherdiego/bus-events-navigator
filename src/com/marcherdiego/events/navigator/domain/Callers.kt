package com.marcherdiego.events.navigator.domain

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter

data class Callers(val subscriberMethod: PsiMethod, val parameter: PsiParameter, val constructorReference: PsiMethod)
