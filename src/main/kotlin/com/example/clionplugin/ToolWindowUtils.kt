package com.example.clionplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.ui.content.ContentFactory
import javax.swing.*
import java.awt.*

fun showExecutionDetailsToolWindow(project: Project, message: String, recomendation: String) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Execution Details") ?: return
    toolWindow.show()

    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BorderLayout()
    panel.border = JBUI.Borders.empty(16)

    // Titlu
    val titleLabel = JLabel("\uD83D\uDCCA Execution Details")
    titleLabel.font = Font("Arial", Font.BOLD, 16)
    titleLabel.foreground = Color(60, 63, 65)
    panel.add(titleLabel, BorderLayout.NORTH)

    // Conținut HTML (cod C++ + recomandări)
    val textPane = JTextPane()
    textPane.isEditable = false
    textPane.contentType = "text/html"
    textPane.font = Font("Consolas", Font.PLAIN, 14)
    textPane.background = Color(250, 250, 250)
    textPane.border = JBUI.Borders.empty()
    val cleanedMessage = message
        .removePrefix("```cpp\n")
        .removeSuffix("\n```")

    val htmlContent = """
    <html>
        <body style="font-family: Consolas; font-size: 12px; color: #2b2b2b;">
            <div>
                <b>Funcția analizată:</b><br/>
                <div style="background-color:#f5f5f5; padding:8px; border-radius:5px; border:1px solid #ddd; white-space:pre-wrap; font-family:Consolas;">
                    ${escapeHtml(cleanedMessage)}
                </div>
            </div>
            <br/>
            <div>
                <b>Recomandări de optimizare:</b>
                <br/>
                ${escapeHtml(recomendation)}
                <br/>
            </div>
        </body>
    </html>
""".trimIndent()

    textPane.text = htmlContent

    val scrollPane = JBScrollPane(textPane)
    scrollPane.border = JBUI.Borders.empty()

    panel.add(scrollPane, BorderLayout.CENTER)

    val content = ContentFactory.getInstance().createContent(panel, "", false)
    toolWindow.contentManager.removeAllContents(true)
    toolWindow.contentManager.addContent(content)
}

private fun escapeHtml(code: String): String {
    return code.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace(" ", "&nbsp;")
        .replace("\n", "<br/>")
}