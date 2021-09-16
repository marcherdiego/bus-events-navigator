package com.marcherdiego.events.navigator

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.tree.ElementType
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.search.searches.MethodReferencesSearch
import kotlin.text.RegexOption.DOT_MATCHES_ALL

object PsiUtils {
    private const val SUBSCRIBE_CLASS_NAME = "org.greenrobot.eventbus.Subscribe"
    private val javaLanguage = Language.findLanguageByID("JAVA")
    private val kotlinLanguage = Language.findLanguageByID("kotlin")

    private var initDone = false
    private lateinit var psiShortNamesCache: PsiShortNamesCache
    private lateinit var allScope: GlobalSearchScope
    private lateinit var javaPsiFacade: JavaPsiFacade
    private lateinit var subscribeAnnotationClass: PsiClass
    private lateinit var fileIndex: ProjectFileIndex

    fun init(project: Project) {
        if (initDone.not()) {
            initDone = true

            psiShortNamesCache = PsiShortNamesCache.getInstance(project)
            allScope = GlobalSearchScope.allScope(project)
            javaPsiFacade = JavaPsiFacade.getInstance(project)
            subscribeAnnotationClass = javaPsiFacade.findClass(SUBSCRIBE_CLASS_NAME, allScope) ?: return
            fileIndex = ProjectRootManager.getInstance(project).fileIndex
        }
    }

    fun isSubscriptionMethod(psiElement: PsiElement): Boolean {
        if (psiElement !is LeafPsiElement || psiElement.text != Constants.SUBSCRIBE) {
            return false
        }
        return if (isKotlin(psiElement)) {
            psiElement.parent.prevSibling == null && psiElement.parent.nextSibling == null
        } else {
            psiElement.parent.prevSibling?.text == Constants.AT
        }
    }

    fun isEventBusPost(psiElement: PsiElement): Boolean {
        if (isLeafIdentifier(psiElement).not()) {
            return false
        }
        val elementName = psiElement.text
        val matchingRegex = when {
            isJava(psiElement) -> getJavaPostingRegex(elementName)
            isKotlin(psiElement) && psiElement.javaClass.toString().contains(Constants.PSI_PACKAGE) -> geKotlinPostingRegex(elementName)
            else -> return false
        }
        if (anyParentMatches(psiElement, matchingRegex).not()) {
            return false
        }
        return psiShortNamesCache.getClassesByName(elementName, allScope).isNotEmpty()
    }

    fun findAnnotatedMethod(psiElement: PsiElement): PsiMethod? {
        val elementContainingFile = psiElement.containingFile
        val fileScope = GlobalSearchScope.fileScope(elementContainingFile)
        val psiMethods = AnnotatedElementsSearch.searchPsiMethods(subscribeAnnotationClass, fileScope)
        val fileText = elementContainingFile.text
        val elementLine = StringUtil.offsetToLineNumber(fileText, psiElement.textOffset)
        return psiMethods.findAll().firstOrNull {
            val methodLine = StringUtil.offsetToLineNumber(fileText, it.textOffset)
            methodLine in elementLine..elementLine + 1
        }
    }

    fun getConstructor(subscriberMethod: PsiMethod): PsiMethod? {
        val parameter = subscriberMethod.parameterList.parameters.first()
        val parameterClass = javaPsiFacade.findClass(parameter.type.canonicalText, allScope) ?: return null
        return parameterClass.constructors.first()
    }

    fun getClassByName(elementName: String): PsiClass? {
        return psiShortNamesCache.getClassesByName(elementName, allScope).firstOrNull()
    }

    fun findMethodParameterUsages(method: PsiMethod): Set<PsiElement> {
        val usages = mutableSetOf<PsiElement>()
        val parameter = method.parameterList.parameters.firstOrNull() ?: return usages
        val parameterClass = javaPsiFacade.findClass(parameter.type.canonicalText, allScope) ?: return usages
        parameterClass.constructors.forEach {
            val parameterFile = it.containingFile.virtualFile
            fileIndex.getModuleForFile(parameterFile) ?: return@forEach
            MethodReferencesSearch.search(it, allScope, false).findAll().forEach { reference ->
                val referencedElement = (reference.element.references.firstOrNull() ?: return@forEach).element
                if (method.containingFile.name != referencedElement.containingFile.name) {
                    usages.add(referencedElement)
                }
            }
        }
        return usages
    }

    fun findUsages(psiClass: PsiClass): Set<PsiElement> {
        val usages = mutableSetOf<PsiElement>()
        psiClass.constructors.forEach {
            val parameterFile = it.containingFile.virtualFile
            fileIndex.getModuleForFile(parameterFile) ?: return@forEach
            MethodReferencesSearch.search(it, allScope, false).findAll().forEach { reference ->
                val referencedElement = (reference.element.references.firstOrNull() ?: return@forEach).element
                if (psiClass.containingFile.name != referencedElement.containingFile.name) {
                    usages.add(referencedElement)
                }
            }
        }
        return usages
    }

    private fun anyParentMatches(psiElement: PsiElement, regex: Regex): Boolean {
        val parent = psiElement.parent
        return when {
            parent == null ||
                    parent.text.contains(Constants.OPEN_BRACKET) ||
                    parent.text.contains(Constants.FUN) ||
                    parent.text.contains(Constants.IMPORT) -> false
            parent.text.matches(regex) -> true
            else -> anyParentMatches(parent, regex)
        }
    }

    private fun isKotlin(psiElement: PsiElement) = psiElement.language.`is`(kotlinLanguage)

    private fun isJava(psiElement: PsiElement) = psiElement.language.`is`(javaLanguage)

    private fun isLeafIdentifier(psiElement: PsiElement): Boolean {
        return psiElement is LeafPsiElement && psiElement.elementType.toString() == ElementType.IDENTIFIER.toString()
    }

    private fun geKotlinPostingRegex(elementName: String) = ".*\\.(post|postSticky).*($elementName(.*).*)".toRegex(DOT_MATCHES_ALL)

    private fun getJavaPostingRegex(elementName: String) = ".*\\.(post|postSticky).*(.*new .*$elementName(.*).*)".toRegex(DOT_MATCHES_ALL)
}
