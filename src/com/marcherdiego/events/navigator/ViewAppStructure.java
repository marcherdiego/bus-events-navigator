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
        final JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel innerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.weightx = 1;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.gridwidth = GridBagConstraints.REMAINDER;
        JLabel resultLabel = new JLabel("Please wait...", SwingConstants.CENTER);
        innerPanel.add(resultLabel, gridBagConstraints);
        contentPane.add(innerPanel, BorderLayout.CENTER);

        loadingFrame.setContentPane(contentPane);
        loadingFrame.pack();
        loadingFrame.setSize(500, 100);
        loadingFrame.setLocationRelativeTo(null);
        loadingFrame.setVisible(true);

        PsiUtils.INSTANCE.init(project);
        ProjectArchitectureGraph projectArchitectureGraph = new ProjectArchitectureGraph(new StatusListener() {
            @Override
            public void notifyStatusUpdate(@NotNull String module, int moduleIndex, int modulesCount, float completed) {
                SwingUtilities.invokeLater(() -> {
                    String percentage = String.format("%.0f", 100f * completed);
                    resultLabel.setText("In module " + moduleIndex + " of " + modulesCount + ": " + module + ": completed " + percentage + "%");
                });
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
        new Thread(() -> projectArchitectureGraph.show(project)).start();
        EventQueue.invokeLater(() -> {
            loadingFrame.toFront();
            loadingFrame.repaint();
        });
    }

    @Override
    public void update(final AnActionEvent actionEvent) {
        final Project project = actionEvent.getData(CommonDataKeys.PROJECT);
        actionEvent.getPresentation().setVisible(project != null);
    }
}
