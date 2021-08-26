package com.marcherdiego.events.navigator.domain

import com.intellij.psi.PsiMethod

data class Callers(val subscriberMethod: PsiMethod, val constructorReference: PsiMethod)
