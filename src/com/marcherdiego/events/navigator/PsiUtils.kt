package com.marcherdiego.events.navigator

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.ElementType
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

object PsiUtils {
    private lateinit var psiShortNamesCache: PsiShortNamesCache
    private lateinit var allScope: GlobalSearchScope

    fun init(project: Project) {
        psiShortNamesCache = PsiShortNamesCache.getInstance(project)
        allScope = GlobalSearchScope.allScope(project)
    }

    fun isSubscriptionMethod(psiElement: PsiElement): Boolean {
        val isLeaf = psiElement is LeafPsiElement
        if (isLeaf.not()) {
            return false
        }
        val isSubscribe = psiElement.text == "Subscribe"
        if (isSubscribe.not()) {
            return false
        }
        return if (isKotlin(psiElement)) {
            psiElement.parent.prevSibling == null && psiElement.parent.nextSibling == null
        } else {
            psiElement.parent.prevSibling?.text == "@"
        }
    }

    fun isEventBusPost(psiElement: PsiElement): Boolean {
        if (isJava(psiElement)) {
            if (psiElement is LeafPsiElement && psiElement.elementType.toString() == ElementType.IDENTIFIER.toString()) {
                val elementName = psiElement.text
                val isPostingEvent = psiElement.parent.parent.parent.parent.parent.text.matches(".*post.*( .*new .*$elementName(.*).*)".toRegex())
                if (isPostingEvent.not()) {
                    return false
                }
                val candidateClasses = psiShortNamesCache.getClassesByName(elementName, allScope)
                if (candidateClasses.isNotEmpty()) {
                    return true
                }
            }
        } else if (isKotlin(psiElement)) {
            val clazz = psiElement.javaClass
            if (clazz.toString().contains("com.intellij.psi")) {
                if (psiElement is LeafPsiElement && psiElement.elementType.toString() == ElementType.IDENTIFIER.toString()) {
                    val elementName = psiElement.text
                    val isPostingEvent = psiElement.parent.parent.parent.parent.parent.text.matches(".*post.*($elementName(.*).*)".toRegex())
                    if (isPostingEvent.not()) {
                        return false
                    }
                    val candidateClasses = psiShortNamesCache.getClassesByName(elementName, allScope)
                    if (candidateClasses.isNotEmpty()) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun isEventBusClass(psiClass: PsiClass) = safeEquals(psiClass.name, Constants.FUN_EVENT_CLASS_NAME)

    private fun isSuperClassEventBus(psiClass: PsiClass): Boolean {
        val supers = psiClass.supers
        if (supers.isEmpty()) {
            return false
        }
        for (superClass in supers) {
            if (safeEquals(superClass.name, Constants.FUN_EVENT_CLASS_NAME)) {
                return true
            }
        }
        return false
    }

    private fun safeEquals(obj: String?, value: String) = obj != null && obj == value

    fun isKotlin(psiElement: PsiElement) = psiElement.language.`is`(Language.findLanguageByID("kotlin"))

    fun isJava(psiElement: PsiElement) = psiElement.language.`is`(Language.findLanguageByID("JAVA"))
}
