package com.marcherdiego.events.navigator

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.PsiUtilBase
import com.intellij.ui.awt.RelativePoint
import com.marcherdiego.events.navigator.domain.Callers
import java.awt.event.MouseEvent
import javax.swing.Icon

class LineMarker : LineMarkerProvider {

    private var initDone = false
    private lateinit var subscribeAnnotationClass: PsiClass
    private lateinit var javaPsiFacade: JavaPsiFacade
    private lateinit var psiShortNamesCache: PsiShortNamesCache
    private lateinit var allScope: GlobalSearchScope

    private fun init(psiElement: PsiElement) {
        val project = psiElement.project
        if (initDone.not()) {
            initDone = true

            javaPsiFacade = JavaPsiFacade.getInstance(project)
            psiShortNamesCache = PsiShortNamesCache.getInstance(project)
            allScope = GlobalSearchScope.allScope(project)
            subscribeAnnotationClass = javaPsiFacade.findClass("org.greenrobot.eventbus.Subscribe", allScope) ?: return
        }
    }

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        init(psiElement)
        PsiUtils.init(psiElement.project)
        return when {
            PsiUtils.isSubscriptionMethod(psiElement) -> {
                val subscriberMethod = findAnnotatedMethod(psiElement) ?: return null
                val methodParameter = subscriberMethod.parameterList.parameters.firstOrNull() ?: return null
                val eventName = (methodParameter.type as? PsiClassReferenceType)?.name
                buildLineMarkerInfo(psiElement, subscribersIcon, "Subscribes to $eventName event.\nClick here to navigate to the poster(s)") { ev, _ ->
                    val callers = buildDependenciesGraph(subscriberMethod) ?: return@buildLineMarkerInfo
                    val filter = FileFilter(callers.subscriberMethod.containingFile.virtualFile)
                    ShowUsagesAction(filter).startFindUsages(
                        callers.constructorReference,
                        RelativePoint(ev),
                        PsiUtilBase.findEditor(callers.subscriberMethod),
                        Constants.MAX_USAGES
                    )
                }
            }
            PsiUtils.isEventBusPost(psiElement) -> {
                val eventName = PsiUtils.getSubscribedEventName(psiElement)
                buildLineMarkerInfo(psiElement, postersIcon, "Posts $eventName event.\nClick here to navigate to its subscriber(s)") { ev, element ->
                    val elementName = element.text
                    val candidateClasses = psiShortNamesCache.getClassesByName(elementName, allScope)
                    val constructor = candidateClasses.first()
                    val filter = FileFilter(constructor.containingFile.virtualFile)
                    ShowUsagesAction(filter).startFindUsages(
                        candidateClasses.first(),
                        RelativePoint(ev),
                        PsiUtilBase.findEditor(constructor),
                        Constants.MAX_USAGES
                    )
                }
            }
            else -> null
        }
    }

    private fun buildLineMarkerInfo(
        psiElement: PsiElement,
        icon: Icon,
        tooltip: String,
        handler: (ev: MouseEvent, element: PsiElement) -> Unit
    ) = LineMarkerInfo(psiElement, psiElement.textRange, icon, Pass.LINE_MARKERS, { tooltip }, handler, GutterIconRenderer.Alignment.CENTER)

    private fun buildDependenciesGraph(subscriberMethod: PsiMethod): Callers? {
        val parameter = subscriberMethod.parameterList.parameters.first()
        val parameterClass = javaPsiFacade.findClass(parameter.type.canonicalText, allScope) ?: return null
        return Callers(subscriberMethod, parameterClass.constructors.first())
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

    companion object {
        @JvmField
        val postersIcon = IconLoader.getIcon("/icons/posters.svg")
        @JvmField
        val subscribersIcon = IconLoader.getIcon("/icons/subscribers.svg")
    }
}
