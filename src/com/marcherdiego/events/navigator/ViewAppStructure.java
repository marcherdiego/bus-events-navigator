package com.marcherdiego.events.navigator;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

import javax.swing.*;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.marcherdiego.events.navigator.ProjectArchitectureGraph.StatusListener;

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
        JLabel resultLabel = new JLabel("Please wait...");
        contentPane.add(resultLabel, BorderLayout.NORTH);
        loadingFrame.setContentPane(contentPane);
        loadingFrame.pack();
        loadingFrame.setLocationRelativeTo(null);
        loadingFrame.setVisible(true);
        SwingUtilities.invokeLater(() -> {
            PsiUtils.INSTANCE.init(project);
            ProjectArchitectureGraph projectArchitectureGraph = new ProjectArchitectureGraph(new StatusListener() {
                @Override
                public void notifyStatusUpdate(@NotNull String module, float completed) {
                    resultLabel.setText("In module: " + module + ": completed " + completed + "%");
                }

                @Override
                public void onCompleted() {
                    loadingFrame.setVisible(false);
                }

                @Override
                public void onFailed() {
                    loadingFrame.setVisible(false);
                }
            });
            projectArchitectureGraph.show(project);
            EventQueue.invokeLater(() -> {
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
