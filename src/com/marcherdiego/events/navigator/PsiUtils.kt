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
    private var initDone = false
    private lateinit var psiShortNamesCache: PsiShortNamesCache
    private lateinit var allScope: GlobalSearchScope

    fun init(project: Project) {
        if (initDone.not()) {
            initDone = true

            psiShortNamesCache = PsiShortNamesCache.getInstance(project)
            allScope = GlobalSearchScope.allScope(project)
        }
    }

    fun isSubscriptionMethod(psiElement: PsiElement): Boolean {
        if (psiElement !is LeafPsiElement || psiElement.text != "Subscribe") {
            return false
        }
        return if (isKotlin(psiElement)) {
            psiElement.parent.prevSibling == null && psiElement.parent.nextSibling == null
        } else {
            psiElement.parent.prevSibling?.text == "@"
        }
    }

    fun getSubscribedEventName(psiElement: PsiElement): String {
        return if (isKotlin(psiElement)) {
            psiElement.text
        } else {
            psiElement.text
        }
    }

    fun isEventBusPost(psiElement: PsiElement): Boolean {
        if (isLeafIdentifier(psiElement).not()) {
            return false
        }
        val elementName = psiElement.text
        val matchingRegex = when {
            isJava(psiElement) -> getJavaPostingRegex(elementName)
            isKotlin(psiElement) && psiElement.javaClass.toString().contains("com.intellij.psi") -> geKotlinPostingRegex(elementName)
            else -> return false
        }
        if (anyParentMatches(psiElement, matchingRegex).not()) {
            return false
        }
        return psiShortNamesCache.getClassesByName(elementName, allScope).isNotEmpty()
    }

    private fun anyParentMatches(psiElement: PsiElement, regex: Regex): Boolean {
        val parent = psiElement.parent
        return when {
            parent == null -> false
            parent.text.contains("{").not() && parent.text.contains("import").not() && parent.text.matches(regex) -> true
            else -> anyParentMatches(parent, regex)
        }
    }

    private fun isKotlin(psiElement: PsiElement) = psiElement.language.`is`(Language.findLanguageByID("kotlin"))

    private fun isJava(psiElement: PsiElement) = psiElement.language.`is`(Language.findLanguageByID("JAVA"))

    private fun isLeafIdentifier(psiElement: PsiElement): Boolean {
        return psiElement is LeafPsiElement && psiElement.elementType.toString() == ElementType.IDENTIFIER.toString()
    }

    private fun geKotlinPostingRegex(elementName: String) = ".*\\.(post|postSticky).*($elementName(.*).*)".toRegex(DOT_MATCHES_ALL)

    private fun getJavaPostingRegex(elementName: String) = ".*\\.(post|postSticky).*(.*new .*$elementName(.*).*)".toRegex(DOT_MATCHES_ALL)
}
