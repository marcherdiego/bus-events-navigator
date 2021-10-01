package com.marcherdiego.events.navigator

import com.intellij.CommonBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.find.FindSettings
import com.intellij.find.actions.CompositeActiveComponent
import com.intellij.find.actions.FindUsagesInFileAction
import com.intellij.find.actions.UsageListCellRenderer
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.find.impl.FindManagerImpl
import com.intellij.icons.AllIcons.General
import com.intellij.icons.AllIcons.Toolwindows
import com.intellij.ide.util.gotoByName.ModelDiff
import com.intellij.ide.util.gotoByName.ModelDiff.Model
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.PopupAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.ui.ActiveComponent
import com.intellij.ui.InplaceButton
import com.intellij.ui.ScreenUtil
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SpeedSearchBase
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.usageView.UsageViewBundle
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageToPsiElementProvider
import com.intellij.usages.UsageView
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewPresentation
import com.intellij.usages.UsageViewSettings
import com.intellij.usages.impl.GroupNode
import com.intellij.usages.impl.NullUsage
import com.intellij.usages.impl.UsageNode
import com.intellij.usages.impl.UsageViewImpl
import com.intellij.usages.impl.UsageViewManagerImpl
import com.intellij.usages.rules.UsageFilteringRuleProvider
import com.intellij.util.Alarm
import com.intellij.util.PlatformIcons
import com.intellij.util.Processor
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.LinkedHashSet
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min

class ShowUsagesAction(private val filter: Filter) : AnAction(), PopupAction {
    private val myUsageViewSettings = UsageViewSettings.instance.apply {
        loadState(this)
        isGroupByFileStructure = false
        isGroupByModule = false
        isGroupByPackage = false
        isGroupByUsageType = false
        isGroupByScope = false
    }
    private var mySearchEverywhereRunnable: Runnable? = null
    private var myWidth = 0

    init {
        setInjectedContext(true)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT) ?: return
        mySearchEverywhereRunnable?.let {
            it.run()
            mySearchEverywhereRunnable = null
            return
        }
        hideHints()
        val popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(e.dataContext)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.usages")
        val usageTargets = e.getData(UsageView.USAGE_TARGETS_KEY)
        val editor = e.getData(PlatformDataKeys.EDITOR)
        if (usageTargets == null) {
            chooseAmbiguousTargetAndPerform(project, editor) { element: PsiElement ->
                startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE)
                false
            }
        } else {
            val element = (usageTargets[0] as PsiElementUsageTarget).element
            element?.let {
                startFindUsages(it, popupPosition, editor, USAGES_PAGE_SIZE)
            }
        }
    }

    fun startFindUsages(element: PsiElement, popupPosition: RelativePoint, editor: Editor?, maxUsages: Int) {
        val project = element.project
        val findUsagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
        val handler = findUsagesManager.getNewFindUsagesHandler(element, false) ?: return
        showElementUsages(handler, editor, popupPosition, maxUsages, getDefaultOptions(handler))
    }

    private fun showElementUsages(
        handler: FindUsagesHandler, editor: Editor?, popupPosition: RelativePoint, maxUsages: Int, options: FindUsagesOptions
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val usageViewSettings: UsageViewSettings = UsageViewSettings.instance
        val savedGlobalSettings = UsageViewSettings()
        savedGlobalSettings.loadState(usageViewSettings)
        usageViewSettings.loadState(myUsageViewSettings)
        val project = handler.project
        val manager = UsageViewManager.getInstance(project)
        val findUsagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
        val presentation = findUsagesManager.createPresentation(handler, options)
        presentation.isDetachedMode = true
        val usageView = manager.createUsageView(UsageTarget.EMPTY_ARRAY, Usage.EMPTY_ARRAY, presentation, null) as UsageViewImpl
        Disposer.register(usageView, {
            myUsageViewSettings.loadState(usageViewSettings)
            usageViewSettings.loadState(savedGlobalSettings)
        })
        val usages: MutableList<Usage> = ArrayList()
        val visibleNodes: MutableSet<UsageNode> = LinkedHashSet()
        val table = MyTable()
        val processIcon = AsyncProcessIcon("xxx")
        val hadMoreSeparator = visibleNodes.remove(MORE_USAGES_SEPARATOR_NODE)
        if (hadMoreSeparator) {
            usages.add(MORE_USAGES_SEPARATOR)
            visibleNodes.add(MORE_USAGES_SEPARATOR_NODE)
        }
        addUsageNodes(usageView.root, usageView, ArrayList())
        ScrollingUtil.installActions(table)
        val data = collectData(usages, visibleNodes, usageView, presentation)
        setTableModel(table, usageView, data)
        val speedSearch = MySpeedSearch(table)
        speedSearch.comparator = SpeedSearchComparator(false)
        val popup = createUsagePopup(
            usages, visibleNodes, handler, editor, popupPosition, maxUsages,
            usageView, options, table, presentation, processIcon, hadMoreSeparator
        )
        Disposer.register(popup, usageView)

        // show popup only if find usages takes more than 300ms, otherwise it would flicker needlessly
        val alarm = Alarm(usageView)
        alarm.addRequest({ showPopupIfNeedTo(popup, popupPosition) }, 300)
        val pingEDT = PingEDT(
            { popup.isDisposed },
            100,
            Runnable {
                if (popup.isDisposed) {
                    return@Runnable
                }
                val nodes = ArrayList<UsageNode>()
                var copy: List<Usage>
                synchronized(usages) {
                    // open up popup as soon as several usages 've been found
                    if (!popup.isVisible && (usages.size <= 1 || !showPopupIfNeedTo(popup, popupPosition))) {
                        return@Runnable
                    }
                    addUsageNodes(usageView.root, usageView, nodes)
                    copy = ArrayList(usages)
                }
                rebuildPopup(usageView, copy, nodes, table, popup, presentation, popupPosition, !processIcon.isDisposed)
            }
        )
        val messageBusConnection = project.messageBus.connect(usageView)
        messageBusConnection.subscribe(UsageFilteringRuleProvider.RULES_CHANGED, Runnable { pingEDT.ping() })
        val collect = object : Processor<Usage> {
            private val myUsageTarget = arrayOf<UsageTarget>(PsiElement2UsageTargetAdapter(handler.psiElement))

            override fun process(usage: Usage): Boolean {
                synchronized(usages) {
                    if (filter.shouldShow(usage).not()) {
                        return true
                    }
                    if (visibleNodes.size >= maxUsages) {
                        return false
                    }
                    if (UsageViewManager.isSelfUsage(usage, myUsageTarget)) {
                        return true
                    }
                    val usageToAdd = transform(usage)
                    val node = usageView.doAppendUsage(usageToAdd)
                    usages.add(usageToAdd)
                    if (node != null) {
                        visibleNodes.add(node)
                        var continueSearch = true
                        if (visibleNodes.size == maxUsages) {
                            visibleNodes.add(MORE_USAGES_SEPARATOR_NODE)
                            usages.add(MORE_USAGES_SEPARATOR)
                            continueSearch = false
                        }
                        pingEDT.ping()
                        return continueSearch
                    }
                    return true
                }
            }
        }
        val indicator = FindUsagesManager.startProcessUsages(
            handler,
            handler.primaryElements,
            handler.secondaryElements,
            collect,
            options,
            {
                ApplicationManager.getApplication().invokeLater(
                    {
                        Disposer.dispose(processIcon)
                        val parent = processIcon.parent
                        parent.remove(processIcon)
                        parent.repaint()
                        pingEDT.ping()
                        synchronized(usages) {
                            if (visibleNodes.isEmpty()) {
                                if (usages.isEmpty()) {
                                    val text = UsageViewBundle.message("no.usages.found.in", searchScopePresentableName(options))
                                    showHint(text, editor, popupPosition, handler, maxUsages, options)
                                    popup.cancel()
                                }
                            } else if (visibleNodes.size == 1) {
                                if (usages.size == 1) {
                                    val usage = visibleNodes.iterator().next().usage
                                    usage.navigate(true)
                                    popup.cancel()
                                } else {
                                    assert(usages.size > 1) { usages }
                                    // usage view can filter usages down to one
                                    val visibleUsage = visibleNodes.iterator().next().usage
                                    if (areAllUsagesInOneLine(visibleUsage, usages)) {
                                        val hint = UsageViewBundle.message(
                                            "all.usages.are.in.this.line", usages.size, searchScopePresentableName(options)
                                        )
                                        navigateAndHint(visibleUsage, hint, handler, popupPosition, maxUsages, options)
                                        popup.cancel()
                                    }
                                }
                            } else {
                                val title = presentation.tabText
                                val shouldShowMoreSeparator = visibleNodes.contains(MORE_USAGES_SEPARATOR_NODE)
                                val fullTitle = getFullTitle(
                                    usages,
                                    title,
                                    shouldShowMoreSeparator,
                                    visibleNodes.size - if (shouldShowMoreSeparator) {
                                        1
                                    } else {
                                        0
                                    },
                                    false
                                )
                                popup.setCaption(fullTitle)
                            }
                        }
                    },
                    project.disposed
                )
            }
        )
        Disposer.register(popup, { indicator.cancel() })
    }

    private fun transform(usage: Usage) = usage

    private class MyModel(data: List<UsageNode>, cols: Int) : ListTableModel<UsageNode?>(cols(cols), data, 0), Model<Any> {
        override fun addToModel(idx: Int, element: Any) {
            val node = if (element is UsageNode) {
                element
            } else {
                createStringNode(element)
            }
            if (idx < rowCount) {
                insertRow(idx, node)
            } else {
                addRow(node)
            }
        }

        override fun removeRangeFromModel(start: Int, end: Int) {
            for (i in end downTo start) {
                removeRow(i)
            }
        }

        companion object {
            private fun cols(cols: Int): Array<ColumnInfo<*, *>> {
                val o: ColumnInfo<UsageNode, UsageNode> = object : ColumnInfo<UsageNode, UsageNode>("") {
                    override fun valueOf(node: UsageNode?) = node
                }
                val list = Collections.nCopies(cols, o)
                return list.toTypedArray()
            }
        }
    }

    private fun showHint(
        text: String, editor: Editor?, popupPosition: RelativePoint, handler: FindUsagesHandler, maxUsages: Int, options: FindUsagesOptions
    ) {
        val label = createHintComponent(text, handler, popupPosition, editor, HIDE_HINTS_ACTION, maxUsages, options)
        if (editor == null || editor.isDisposed) {
            HintManager.getInstance().showHint(
                label, popupPosition, HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING, 0
            )
        } else {
            HintManager.getInstance().showInformationHint(editor, label)
        }
    }

    private fun createHintComponent(
        text: String, handler: FindUsagesHandler, popupPosition: RelativePoint, editor: Editor?,
        cancelAction: Runnable, maxUsages: Int, options: FindUsagesOptions
    ): JComponent {
        val label = HintUtil.createInformationLabel(suggestSecondInvocation(options, handler, "$text&nbsp;"))
        val button = createSettingsButton(handler, popupPosition, editor, maxUsages, cancelAction)
        val panel: JPanel = object : JPanel(BorderLayout()) {
            override fun addNotify() {
                mySearchEverywhereRunnable = Runnable { searchEverywhere(options, handler, editor, popupPosition, maxUsages) }
                super.addNotify()
            }

            override fun removeNotify() {
                mySearchEverywhereRunnable = null
                super.removeNotify()
            }
        }
        button.background = label.background
        panel.background = label.background
        label.isOpaque = false
        label.border = null
        panel.border = HintUtil.createHintBorder()
        panel.add(label, BorderLayout.CENTER)
        panel.add(button, BorderLayout.EAST)
        return panel
    }

    private fun createSettingsButton(
        handler: FindUsagesHandler, popupPosition: RelativePoint, editor: Editor?, maxUsages: Int, cancelAction: Runnable
    ): InplaceButton {
        var shortcutText = ""
        val shortcut = UsageViewImpl.getShowUsagesWithSettingsShortcut()
        if (shortcut != null) {
            shortcutText = "(" + KeymapUtil.getShortcutText(shortcut) + ")"
        }
        return InplaceButton("Settings...$shortcutText", General.Settings) {
            SwingUtilities.invokeLater {
                showDialogAndFindUsages(handler, popupPosition, editor, maxUsages)
            }
            cancelAction.run()
        }
    }

    private fun showDialogAndFindUsages(handler: FindUsagesHandler, popupPosition: RelativePoint, editor: Editor?, maxUsages: Int) {
        val dialog = handler.getFindUsagesDialog(false, false, false)
        dialog.show()
        if (dialog.isOK) {
            dialog.calcFindUsagesOptions()
            showElementUsages(handler, editor, popupPosition, maxUsages, getDefaultOptions(handler))
        }
    }

    private fun createUsagePopup(
        usages: List<Usage>, visibleNodes: Set<UsageNode>, handler: FindUsagesHandler, editor: Editor?,
        popupPosition: RelativePoint, maxUsages: Int, usageView: UsageViewImpl, options: FindUsagesOptions,
        table: JTable, presentation: UsageViewPresentation, processIcon: AsyncProcessIcon, hadMoreSeparator: Boolean
    ): JBPopup {
        table.rowHeight = PlatformIcons.CLASS_ICON.iconHeight + 2
        table.setShowGrid(false)
        table.showVerticalLines = false
        table.showHorizontalLines = false
        table.tableHeader = null
        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        table.intercellSpacing = Dimension(0, 0)
        val builder: PopupChooserBuilder<*> = PopupChooserBuilder<Any?>(table)
        val title = presentation.tabText
        if (title != null) {
            val result = getFullTitle(usages, title, hadMoreSeparator, visibleNodes.size - 1, true)
            builder.setTitle(result)
            builder.setAdText(getSecondInvocationTitle(options, handler))
        }
        builder.setMovable(true).setResizable(true)
        builder.setItemChoosenCallback {
            val selected = table.selectedRows
            for (i in selected) {
                val value = table.getValueAt(i, 0)
                if (value is UsageNode) {
                    val usage = value.usage
                    if (usage === MORE_USAGES_SEPARATOR) {
                        appendMoreUsages(editor, popupPosition, handler, maxUsages)
                        return@setItemChoosenCallback
                    }
                    navigateAndHint(usage, null, handler, popupPosition, maxUsages, options)
                }
            }
        }
        val popup = mutableListOf<JBPopup>()
        UsageViewImpl.getShowUsagesWithSettingsShortcut()?.let {
            object : DumbAwareAction() {
                override fun actionPerformed(e: AnActionEvent) {
                    popup.firstOrNull()?.cancel()
                    showDialogAndFindUsages(handler, popupPosition, editor, maxUsages)
                }
            }.registerCustomShortcutSet(CustomShortcutSet(it.firstKeyStroke), table)
        }
        showUsagesShortcut?.let {
            object : DumbAwareAction() {
                override fun actionPerformed(e: AnActionEvent) {
                    popup.firstOrNull()?.cancel()
                    searchEverywhere(options, handler, editor, popupPosition, maxUsages)
                }
            }.registerCustomShortcutSet(CustomShortcutSet(it.firstKeyStroke), table)
        }
        val settingsButton = createSettingsButton(handler, popupPosition, editor, maxUsages) { popup.first().cancel() }
        val spinningProgress: ActiveComponent = object : ActiveComponent {
            override fun setActive(active: Boolean) {}
            override fun getComponent() = processIcon
        }
        builder.setCommandButton(CompositeActiveComponent(spinningProgress, settingsButton))
        val toolbar = DefaultActionGroup()
        usageView.addFilteringActions(toolbar)
        toolbar.add(ActionManager.getInstance().getAction("UsageGrouping.FileStructure"))
        toolbar.add(object : AnAction(
            "Open Find Usages Toolwindow", "Show all usages in a separate toolwindow", Toolwindows.ToolWindowFind
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                hideHints()
                popup.firstOrNull()?.cancel()
                val findUsagesManager = (FindManager.getInstance(usageView.project) as FindManagerImpl).findUsagesManager
                findUsagesManager.findUsages(
                    handler.primaryElements, handler.secondaryElements, handler, options,
                    FindSettings.getInstance().isSkipResultsWithOneUsage
                )
            }

            init {
                val action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES)
                shortcutSet = action.shortcutSet
            }
        })
        val actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR, toolbar, true)
        actionToolbar.setReservePlaceAutoPopupIcon(false)
        val toolBar = actionToolbar.component
        toolBar.isOpaque = false
        builder.setSettingButton(toolBar)
        popup.add(builder.createPopup())
        val content = popup.first().content
        myWidth = (toolBar.preferredSize.getWidth() +
                JLabel(getFullTitle(usages, title, hadMoreSeparator, visibleNodes.size - 1, true)).preferredSize.getWidth() +
                settingsButton.preferredSize.getWidth()).toInt()
        myWidth = -1
        for (action in toolbar.getChildren(null)) {
            action.unregisterCustomShortcutSet(usageView.component)
            action.registerCustomShortcutSet(action.shortcutSet, content)
        }
        return popup.first()
    }

    private fun searchEverywhere(
        options: FindUsagesOptions, handler: FindUsagesHandler, editor: Editor?, popupPosition: RelativePoint, maxUsages: Int
    ) {
        val cloned = options.clone()
        cloned.searchScope = FindUsagesManager.getMaximalScope(handler)
        showElementUsages(handler, editor, popupPosition, maxUsages, cloned)
    }

    private fun rebuildPopup(
        usageView: UsageViewImpl, usages: List<Usage>, nodes: MutableList<UsageNode>, table: JTable, popup: JBPopup,
        presentation: UsageViewPresentation, popupPosition: RelativePoint, findUsagesInProgress: Boolean
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val shouldShowMoreSeparator = usages.contains(MORE_USAGES_SEPARATOR)
        if (shouldShowMoreSeparator) {
            nodes.add(MORE_USAGES_SEPARATOR_NODE)
        }
        val title = presentation.tabText
        val fullTitle = getFullTitle(
            usages, title, shouldShowMoreSeparator, nodes.size - if (shouldShowMoreSeparator) 1 else 0, findUsagesInProgress
        )
        popup.setCaption(fullTitle)
        val data = collectData(usages, nodes, usageView, presentation)
        val tableModel = setTableModel(table, usageView, data)
        val existingData = tableModel.items as List<UsageNode>
        val row = table.selectedRow
        var newSelection = updateModel(tableModel, existingData, data, if (row == -1) 0 else row)
        if (newSelection < 0 || newSelection >= tableModel.rowCount) {
            ScrollingUtil.ensureSelectionExists(table)
            newSelection = table.selectedRow
        } else {
            table.selectionModel.setSelectionInterval(newSelection, newSelection)
        }
        ScrollingUtil.ensureIndexIsVisible(table, newSelection, 0)
        setSizeAndDimensions(table, popup, popupPosition, data)
    }

    private fun setSizeAndDimensions(table: JTable, popup: JBPopup, popupPosition: RelativePoint, data: List<UsageNode>) {
        val content = popup.content
        val window = SwingUtilities.windowForComponent(content)
        val d = window.size
        var width = calcMaxWidth(table)
        width = max(d.getWidth(), width.toDouble()).toInt()
        val headerSize = (popup as AbstractPopup).headerPreferredSize
        width = max(headerSize.getWidth().toInt(), width)
        width = max(myWidth, width)
        if (myWidth == -1) myWidth = width
        val newWidth = max(width, d.width + width - myWidth)
        myWidth = newWidth
        val rowsToShow = min(30, data.size)
        var dimension = Dimension(newWidth, table.rowHeight * rowsToShow)
        val rectangle = fitToScreen(dimension, popupPosition, table)
        dimension = rectangle.size
        val location = window.location
        if (location != rectangle.location) {
            window.location = rectangle.location
        }
        if (data.isNotEmpty()) {
            ScrollingUtil.ensureSelectionExists(table)
        }
        table.size = dimension
        val footerSize = popup.footerPreferredSize
        val newHeight = (dimension.height + headerSize.getHeight() + footerSize.getHeight()).toInt() + 4
        val newDim = Dimension(dimension.width, newHeight)
        window.size = newDim
        window.minimumSize = newDim
        window.maximumSize = newDim
        window.validate()
        window.repaint()
        table.revalidate()
        table.repaint()
    }

    private fun appendMoreUsages(editor: Editor?, popupPosition: RelativePoint, handler: FindUsagesHandler, maxUsages: Int) {
        showElementUsages(handler, editor, popupPosition, maxUsages + USAGES_PAGE_SIZE, getDefaultOptions(handler))
    }

    private fun addUsageNodes(root: GroupNode, usageView: UsageViewImpl, outNodes: MutableList<UsageNode>) {
        root.usageNodes.forEach { node ->
            val usage = node.usage
            if (usageView.isVisible(usage)) {
                node.setParent(root)
                outNodes.add(node)
            }
        }
        root.subGroups.forEach { groupNode ->
            groupNode.setParent(root)
            addUsageNodes(groupNode, usageView, outNodes)
        }
    }

    override fun update(e: AnActionEvent) {
        FindUsagesInFileAction.updateFindUsagesAction(e)
    }

    private fun navigateAndHint(
        usage: Usage, hint: String?, handler: FindUsagesHandler, popupPosition: RelativePoint, maxUsages: Int, options: FindUsagesOptions
    ) {
        usage.navigate(true)
        if (hint == null) {
            return
        }
        val newEditor = getEditorFor(usage) ?: return
        val project = handler.project
        //opening editor is performing in invokeLater
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown {
            newEditor.scrollingModel.runActionOnScrollingFinished {
                // after new editor created, some editor resizing events are still bubbling. To prevent hiding hint, invokeLater this
                IdeFocusManager.getInstance(project).doWhenFocusSettlesDown {
                    if (newEditor.component.isShowing) {
                        showHint(hint, newEditor, popupPosition, handler, maxUsages, options)
                    }
                }
            }
        }
    }

    private class MyTable : JTable(), DataProvider {
        override fun getScrollableTracksViewportWidth() = true

        override fun getData(@NonNls dataId: String): Any? {
            if (LangDataKeys.PSI_ELEMENT.`is`(dataId)) {
                val selected = selectedRows
                if (selected.size == 1) {
                    return getPsiElementForHint(getValueAt(selected[0], 0))
                }
            }
            return null
        }

        fun getPsiElementForHint(selectedValue: Any?): PsiElement? {
            if (selectedValue is UsageNode) {
                val usage = selectedValue.usage
                if (usage is UsageInfo2UsageAdapter) {
                    val element = usage.element
                    if (element != null) {
                        val view = UsageToPsiElementProvider.findAppropriateParentFrom(element)
                        return view ?: element
                    }
                }
            }
            return null
        }
    }

    internal class StringNode(private val myString: Any) : UsageNode(null, NullUsage.INSTANCE) {
        override fun toString() = myString.toString()
    }

    private class MySpeedSearch(table: MyTable) : SpeedSearchBase<JTable?>(table) {
        override fun getSelectedIndex() = table.selectedRow

        override fun convertIndexToModel(viewIndex: Int) = table.convertRowIndexToModel(viewIndex)

        override fun getAllElements() = arrayOf((table.model as MyModel).items)

        override fun getElementText(element: Any): String? {
            if (element !is UsageNode) {
                return element.toString()
            }
            if (element is StringNode) {
                return ""
            }
            val usage = element.usage
            if (usage === MORE_USAGES_SEPARATOR) {
                return ""
            }
            val group = element.parent as GroupNode
            return usage.presentation.plainText + group
        }

        override fun selectElement(element: Any, selectedText: String) {
            val data = (table.model as MyModel).items
            val i = data.indexOf(element as? UsageNode? ?: return)
            if (i == -1) {
                return
            }
            val viewRow = table.convertRowIndexToView(i)
            table.selectionModel.setSelectionInterval(viewRow, viewRow)
            TableUtil.scrollSelectionToVisible(table)
        }

        private val table: MyTable
            get() = myComponent as MyTable
    }

    companion object {
        private const val USAGES_PAGE_SIZE = 100
        val MORE_USAGES_SEPARATOR: NullUsage = NullUsage.INSTANCE
        private val MORE_USAGES_SEPARATOR_NODE = UsageViewImpl.NULL_NODE

        private val USAGE_NODE_COMPARATOR = Comparator { c1: UsageNode, c2: UsageNode ->
            if (c1 is StringNode) {
                return@Comparator 1
            }
            if (c2 is StringNode) {
                return@Comparator -1
            }
            val o1 = c1.usage
            val o2 = c2.usage
            if (o1 === MORE_USAGES_SEPARATOR) {
                return@Comparator 1
            }
            if (o2 === MORE_USAGES_SEPARATOR) {
                return@Comparator -1
            }
            val v1 = UsageListCellRenderer.getVirtualFile(o1)
            val v2 = UsageListCellRenderer.getVirtualFile(o2)
            val name1 = v1?.name
            val name2 = v2?.name
            val i = Comparing.compare(name1, name2)
            if (i != 0) {
                return@Comparator i
            }
            val loc1 = o1.location
            val loc2 = o2.location
            Comparing.compare(loc1, loc2)
        }
        private val HIDE_HINTS_ACTION = Runnable { hideHints() }
        private fun chooseAmbiguousTargetAndPerform(project: Project, editor: Editor?, processor: PsiElementProcessor<PsiElement>) {
            if (editor == null) {
                Messages.showMessageDialog(
                    project, FindBundle.message("find.no.usages.at.cursor.error"),
                    CommonBundle.getErrorTitle(), Messages.getErrorIcon()
                )
            } else {
                val offset = editor.caretModel.offset
                val chosen = GotoDeclarationAction.chooseAmbiguousTarget(
                    project,
                    editor,
                    offset,
                    processor,
                    FindBundle.message("find.usages.ambiguous.title", "crap"),
                    null
                )
                if (!chosen) {
                    ApplicationManager.getApplication().invokeLater(Runnable {
                        if (editor.isDisposed || !editor.component.isShowing) {
                            return@Runnable
                        }
                        HintManager.getInstance().showErrorHint(editor, FindBundle.message("find.no.usages.at.cursor.error"))
                    }, project.disposed)
                }
            }
        }

        private fun hideHints() {
            HintManager.getInstance().hideHints(HintManager.HIDE_BY_ANY_KEY, false, false)
        }

        private fun getDefaultOptions(handler: FindUsagesHandler): FindUsagesOptions {
            val options = handler.findUsagesOptions
            // by default, scope in FindUsagesOptions is copied from the FindSettings, but we need a default one
            options.searchScope = FindUsagesManager.getMaximalScope(handler)
            return options
        }

        private fun createStringNode(string: Any) = StringNode(string)

        private fun showPopupIfNeedTo(popup: JBPopup, popupPosition: RelativePoint): Boolean {
            return if (!popup.isDisposed && !popup.isVisible) {
                popup.show(popupPosition)
                true
            } else {
                false
            }
        }

        private fun searchScopePresentableName(options: FindUsagesOptions) = notNullizeScope(options).displayName

        private fun notNullizeScope(options: FindUsagesOptions) = options.searchScope

        private fun getFullTitle(
            usages: List<Usage>, title: String, hadMoreSeparator: Boolean, visibleNodesCount: Int, findUsagesInProgress: Boolean
        ): String {
            val s = if (hadMoreSeparator) {
                "<b>Some</b> $title <b>(Only $visibleNodesCount usages shown" + if (findUsagesInProgress) {
                    " so far"
                } else {
                    ""
                } + ")</b>"
            } else {
                title + " (" + UsageViewBundle.message("usages.n", usages.size) + if (findUsagesInProgress) {
                    " so far"
                } else {
                    ""
                } + ")"
            }
            return "<html><nobr>$s</nobr></html>"
        }

        private fun suggestSecondInvocation(options: FindUsagesOptions, handler: FindUsagesHandler, text: String): String {
            val title = getSecondInvocationTitle(options, handler)
            return if (title == null) {
                "<html><body>$text</body></html>"
            } else {
                "$text<br><small>Press $title</small>"
            }
        }

        private fun getSecondInvocationTitle(options: FindUsagesOptions, handler: FindUsagesHandler): String? {
            if (showUsagesShortcut != null) {
                val maximalScope = FindUsagesManager.getMaximalScope(handler)
                if (notNullizeScope(options) != maximalScope) {
                    return "Press " + KeymapUtil.getShortcutText(
                        showUsagesShortcut ?: return null
                    ) + " again to search in " + maximalScope.displayName
                }
            }
            return null
        }

        private val showUsagesShortcut: KeyboardShortcut?
            get() = ActionManager.getInstance().getKeyboardShortcut("ShowUsages")

        private fun filtered(usages: List<Usage>, usageView: UsageViewImpl): Int {
            var count = 0
            for (usage in usages) {
                if (!usageView.isVisible(usage)) count++
            }
            return count
        }

        private fun getUsageOffset(usage: Usage): Int {
            if (usage !is UsageInfo2UsageAdapter) {
                return -1
            }
            val element = usage.element ?: return -1
            return element.textRange.startOffset
        }

        private fun areAllUsagesInOneLine(visibleUsage: Usage, usages: List<Usage>): Boolean {
            val editor = getEditorFor(visibleUsage) ?: return false
            val offset = getUsageOffset(visibleUsage)
            if (offset == -1) {
                return false
            }
            val lineNumber = editor.document.getLineNumber(offset)
            for (other in usages) {
                val otherEditor = getEditorFor(other)
                if (otherEditor !== editor) {
                    return false
                }
                val otherOffset = getUsageOffset(other)
                if (otherOffset == -1) {
                    return false
                }
                val otherLine = otherEditor.document.getLineNumber(otherOffset)
                if (otherLine != lineNumber) {
                    return false
                }
            }
            return true
        }

        private fun setTableModel(table: JTable, usageView: UsageViewImpl, data: List<UsageNode>): MyModel {
            ApplicationManager.getApplication().assertIsDispatchThread()
            val columnCount = calcColumnCount(data)
            var model = if (table.model is MyModel) {
                table.model as MyModel
            } else {
                null
            }
            if (model == null || model.columnCount != columnCount) {
                model = MyModel(data, columnCount)
                table.model = model
                val renderer = ShowUsagesTableCellRenderer(usageView)
                for (i in 0 until table.columnModel.columnCount) {
                    val column = table.columnModel.getColumn(i)
                    column.cellRenderer = renderer
                }
            }
            return model
        }

        private fun calcColumnCount(data: List<UsageNode>): Int {
            return if (data.isEmpty() || data[0] is StringNode) {
                1
            } else {
                3
            }
        }

        private fun collectData(
            usages: List<Usage>,
            visibleNodes: Collection<UsageNode>,
            usageView: UsageViewImpl,
            presentation: UsageViewPresentation
        ): List<UsageNode> {
            val data: MutableList<UsageNode> = ArrayList()
            val filtered = filtered(usages, usageView)
            if (filtered != 0) {
                data.add(createStringNode(UsageViewBundle.message("usages.were.filtered.out", filtered)))
            }
            data.addAll(visibleNodes)
            if (data.isEmpty()) {
                val progressText = UsageViewManagerImpl.getProgressTitle(presentation)
                data.add(createStringNode(progressText))
            }
            data.sortWith(USAGE_NODE_COMPARATOR)
            return data
        }

        private fun calcMaxWidth(table: JTable): Int {
            val colsNum = table.columnModel.columnCount
            var totalWidth = 0
            for (col in 0 until colsNum - 1) {
                val column = table.columnModel.getColumn(col)
                val preferred = column.preferredWidth
                val width = max(preferred, columnMaxWidth(table, col))
                totalWidth += width
                column.minWidth = width
                column.maxWidth = width
                column.width = width
                column.preferredWidth = width
            }
            totalWidth += columnMaxWidth(table, colsNum - 1)
            return totalWidth
        }

        private fun columnMaxWidth(table: JTable, col: Int): Int {
            val column = table.columnModel.getColumn(col)
            var width = 0
            for (row in 0 until table.rowCount) {
                val component = table.prepareRenderer(column.cellRenderer, row, col)
                val rendererWidth = component.preferredSize.width
                width = max(width, rendererWidth + table.intercellSpacing.width)
            }
            return width
        }

        // returns new selection
        private fun updateModel(tableModel: MyModel, listOld: List<UsageNode>, listNew: List<UsageNode>, oldSelection: Int): Int {
            val cmds = ModelDiff.createDiffCmds(tableModel, listOld.toTypedArray(), listNew.toTypedArray())
            var selection = oldSelection
            if (cmds != null) {
                for (cmd in cmds) {
                    selection = cmd.translateSelection(selection)
                    cmd.apply()
                }
            }
            return selection
        }

        private fun fitToScreen(newDim: Dimension, popupPosition: RelativePoint, table: JTable): Rectangle {
            val rectangle = Rectangle(popupPosition.screenPoint, newDim)
            ScreenUtil.fitToScreen(rectangle)
            if (rectangle.getHeight() != newDim.getHeight()) {
                val newHeight = rectangle.getHeight().toInt()
                val roundedHeight = newHeight - newHeight % table.rowHeight
                rectangle.setSize(rectangle.getWidth().toInt(), max(roundedHeight, table.rowHeight))
            }
            return rectangle
        }

        private fun getEditorFor(usage: Usage): Editor? {
            val location = usage.location
            val newFileEditor = location?.editor
            return if (newFileEditor is TextEditor) {
                newFileEditor.editor
            } else {
                null
            }
        }
    }
}
