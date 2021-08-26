package com.marcherdiego.events.navigator

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.ElementType
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import kotlin.text.RegexOption.DOT_MATCHES_ALL

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
                val isPostingEvent =
                    anyParentMatches(psiElement, ".*\\.(post|postSticky).*(.*new .*$elementName(.*).*)".toRegex(DOT_MATCHES_ALL))
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
                    val isPostingEvent =
                        anyParentMatches(psiElement, ".*\\.(post|postSticky).*($elementName(.*).*)".toRegex(DOT_MATCHES_ALL))
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

    private fun anyParentMatches(psiElement: PsiElement, regex: Regex): Boolean {
        val parent = psiElement.parent
        return when {
            parent == null -> false
            parent.text.contains("{").not() && parent.text.matches(regex) -> true
            else -> anyParentMatches(parent, regex)
        }
    }

    fun isKotlin(psiElement: PsiElement) = psiElement.language.`is`(Language.findLanguageByID("kotlin"))

    fun isJava(psiElement: PsiElement) = psiElement.language.`is`(Language.findLanguageByID("JAVA"))
}
