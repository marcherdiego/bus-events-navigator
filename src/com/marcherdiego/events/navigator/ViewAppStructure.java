package com.marcherdiego.events.navigator;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

import javax.swing.*;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;

public class ViewAppStructure extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent actionEvent) {
        Project project = getEventProject(actionEvent);
        if (project == null) {
            return;
        }

        JFrame loadingFrame = new JFrame("Building dependencies graph...");
        final JPanel contentPane = new JPanel();
        contentPane.setBorder(BorderFactory.createEmptyBorder(20, 120, 20, 120));
        contentPane.setLayout(new BorderLayout());
        contentPane.add(new JLabel("Please wait..."), BorderLayout.NORTH);
        loadingFrame.setContentPane(contentPane);
        loadingFrame.pack();
        loadingFrame.setLocationRelativeTo(null);
        loadingFrame.setVisible(true);
        SwingUtilities.invokeLater(() -> {
            PsiUtils.INSTANCE.init(project);
            ProjectArchitectureGraph.INSTANCE.show(project);
            loadingFrame.setVisible(false);
            java.awt.EventQueue.invokeLater(() -> {
                loadingFrame.toFront();
                loadingFrame.repaint();
            });
        });
    }

    @Override
    public void update(final AnActionEvent actionEvent) {
        final Project project = actionEvent.getData(CommonDataKeys.PROJECT);
        actionEvent.getPresentation().setVisible(project != null);
    }
}
