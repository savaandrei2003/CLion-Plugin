// ToolWindowUtils.kt
package com.example.clionplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import javax.swing.JLabel
import javax.swing.JPanel

fun showExecutionDetailsToolWindow(project: Project, message: String) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Execution Details") ?: return
    toolWindow.show()

    val panel = JPanel()
    panel.add(JLabel(message))

    val content = ContentFactory.getInstance().createContent(panel, "", false)
    toolWindow.contentManager.removeAllContents(true)
    toolWindow.contentManager.addContent(content)
}
