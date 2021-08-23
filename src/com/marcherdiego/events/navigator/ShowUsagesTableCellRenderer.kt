package com.marcherdiego.events.navigator

import com.intellij.psi.PsiManager
import com.intellij.ui.FileColorManager
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usages.Usage
import com.intellij.usages.impl.GroupNode
import com.intellij.usages.impl.NullUsage
import com.intellij.usages.impl.UsageNode
import com.intellij.usages.impl.UsageViewImpl
import com.intellij.usages.rules.UsageInFile
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.TableCellRenderer

internal class ShowUsagesTableCellRenderer(private val myUsageView: UsageViewImpl) : TableCellRenderer {

    override fun getTableCellRendererComponent(list: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int,
                                               column: Int): Component {
        val usageNode = if (value is UsageNode) {
            value
        } else {
            null
        }
        val usage = usageNode?.usage
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        val fileBgColor = getBackgroundColor(isSelected, usage)
        val bg = UIUtil.getListSelectionBackground()
        val fg = UIUtil.getListSelectionForeground()
        panel.background = if (isSelected) {
            bg
        } else {
            fileBgColor ?: list.background
        }
        panel.foreground = if (isSelected) {
            fg
        } else {
            list.foreground
        }
        if (usage == null || usageNode is StringNode) {
            panel.layout = BorderLayout()
            if (column == 0) {
                panel.add(JLabel("<html><body><b>$value</b></body></html>", SwingConstants.CENTER))
            }
            return panel
        }
        val textChunks = SimpleColoredComponent()
        textChunks.ipad = Insets(0, 0, 0, 0)
        textChunks.border = null
        if (column == 0) {
            val parent = usageNode.parent as GroupNode
            appendGroupText(parent, panel, fileBgColor)
            if (usage === MORE_USAGES_SEPARATOR) {
                textChunks.append("...<")
                textChunks.append("more usages", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                textChunks.append(">...")
            }
        } else if (usage !== MORE_USAGES_SEPARATOR) {
            val presentation = usage.presentation
            val text = presentation.text
            if (column == 1) {
                val icon = presentation.icon
                textChunks.icon = icon ?: EmptyIcon.ICON_16
                if (text.isNotEmpty()) {
                    val attributes = if (isSelected) {
                        SimpleTextAttributes(bg, fg, fg, SimpleTextAttributes.STYLE_ITALIC)
                    } else {
                        deriveAttributesWithColor(text[0].simpleAttributesIgnoreBackground, fileBgColor)
                    }
                    textChunks.append(text[0].text, attributes)
                }
            } else if (column == 2) {
                for (i in 1 until text.size) {
                    val textChunk = text[i]
                    val attrs = textChunk.simpleAttributesIgnoreBackground
                    val attributes = if (isSelected) {
                        SimpleTextAttributes(bg, fg, fg, attrs.style)
                    } else {
                        deriveAttributesWithColor(attrs, fileBgColor)
                    }
                    textChunks.append(textChunk.text, attributes)
                }
            } else {
                assert(false) {
                    column
                }
            }
        }
        panel.add(textChunks)
        return panel
    }

    private fun getBackgroundColor(isSelected: Boolean, usage: Usage?): Color? {
        var fileBgColor: Color? = null
        if (isSelected) {
            fileBgColor = UIUtil.getListSelectionBackground()
        } else {
            val virtualFile = if (usage is UsageInFile) {
                usage.file
            } else {
                null
            }
            if (virtualFile != null) {
                val project = myUsageView.project
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                if (psiFile != null && psiFile.isValid) {
                    val color = FileColorManager.getInstance(project).getRendererBackground(psiFile)
                    if (color != null) {
                        fileBgColor = color
                    }
                }
            }
        }
        return fileBgColor
    }

    private fun appendGroupText(node: GroupNode?, panel: JPanel, fileBgColor: Color?) {
        val group = node?.group ?: return
        val parentGroup = node.parent as GroupNode
        appendGroupText(parentGroup, panel, fileBgColor)
        if (node.canNavigateToSource()) {
            val renderer = SimpleColoredComponent()
            renderer.icon = group.getIcon(false)
            val attributes = deriveAttributesWithColor(SimpleTextAttributes.REGULAR_ATTRIBUTES, fileBgColor)
            renderer.append(group.getText(myUsageView), attributes)
            renderer.append(" ", attributes)
            renderer.ipad = Insets(0, 0, 0, 0)
            renderer.border = null
            panel.add(renderer)
        }
    }

    internal class StringNode(private val myString: Any) : UsageNode(null, NullUsage.INSTANCE) {
        override fun toString(): String {
            return myString.toString()
        }
    }

    companion object {
        private val MORE_USAGES_SEPARATOR = NullUsage.INSTANCE
        private fun deriveAttributesWithColor(attributes: SimpleTextAttributes, fileBgColor: Color?): SimpleTextAttributes {
            var attributes = attributes
            if (fileBgColor != null) {
                attributes = attributes.derive(-1, null, fileBgColor, null)
            }
            return attributes
        }
    }
}