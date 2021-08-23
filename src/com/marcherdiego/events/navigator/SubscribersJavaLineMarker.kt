package com.marcherdiego.events.navigator

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.PsiUtilBase
import com.intellij.ui.awt.RelativePoint
import com.marcherdiego.events.navigator.domain.Callees
import java.awt.event.MouseEvent

class SubscribersJavaLineMarker : LineMarkerProvider {

    private var initDone = false
    private lateinit var subscribeAnnotationClass: PsiClass
    private lateinit var javaPsiFacade: JavaPsiFacade
    private lateinit var allScope: GlobalSearchScope

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        init(psiElement)
        return if (isSubscriptionMethod(psiElement)) {
            LineMarkerInfo<PsiElement>(
                    psiElement,
                    psiElement.textRange,
                    icon,
                    Pass.LINE_MARKERS,
                    null,
                    GutterIconNavigationHandler<PsiElement> { e: MouseEvent, psiElement: PsiElement ->
                        val callees = buildDependenciesGraph(psiElement) ?: return@GutterIconNavigationHandler
                        val filter = FileFilter(callees.subscriberMethod)
                        ShowUsagesAction(filter).startFindUsages(
                                callees.constructorReference,
                                RelativePoint(e),
                                PsiUtilBase.findEditor(callees.subscriberMethod),
                                Constants.MAX_USAGES
                        )
                    },
                    GutterIconRenderer.Alignment.CENTER
            )
        } else {
            null
        }
    }

    private fun buildDependenciesGraph(psiElement: PsiElement): Callees? {
        val subscriberMethod = findAnnotatedMethod(psiElement) ?: return null
        val parameter = subscriberMethod.parameterList.parameters.first()
        val parameterClass = javaPsiFacade.findClass(parameter.type.canonicalText, allScope) ?: return null
        val elementCallees = Callees(subscriberMethod, parameter, mutableListOf())
        elementCallees.constructorReference = parameterClass.constructors.first()
        return elementCallees
    }

    private fun findAnnotatedMethod(psiElement: PsiElement): PsiMethod? {
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

    private fun isSubscriptionMethod(psiElement: PsiElement): Boolean {
        val isLeaf = psiElement is LeafPsiElement
        if (isLeaf.not()) {
            return false
        }
        val isSubscribe = psiElement.text == "Subscribe"
        if (isSubscribe.not()) {
            return false
        }
        val parentPreviousSibling = psiElement.parent.prevSibling ?: return false
        return parentPreviousSibling.text == "@"
    }

    override fun collectSlowLineMarkers(elements: MutableList<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
    }

    private fun init(psiElement: PsiElement) {
        if (initDone.not()) {
            initDone = true

            javaPsiFacade = JavaPsiFacade.getInstance(psiElement.project)
            allScope = GlobalSearchScope.allScope(psiElement.project)
            subscribeAnnotationClass = javaPsiFacade.findClass("org.greenrobot.eventbus.Subscribe", allScope) ?: return
        }
    }

    companion object {
        private val icon = IconLoader.getIcon("/icons/stateart.png")
    }
}
