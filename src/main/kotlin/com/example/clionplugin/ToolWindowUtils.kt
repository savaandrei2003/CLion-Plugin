package com.example.clionplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.ui.content.ContentFactory
import org.json.JSONObject
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.swing.*

fun showExecutionDetailsToolWindow(project: Project, message: String, recomendation: String) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Execution Details") ?: return
    toolWindow.show()

    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BorderLayout()
    panel.border = JBUI.Borders.empty(16)

    val titleLabel = JLabel("📊 Execution Details")
    titleLabel.font = Font("Arial", Font.BOLD, 16)
    titleLabel.foreground = Color(60, 63, 65)
    panel.add(titleLabel, BorderLayout.NORTH)

    val textPane = JTextPane()
    textPane.isEditable = false
    textPane.contentType = "text/html"
    textPane.font = Font("Consolas", Font.PLAIN, 14)
    textPane.background = Color(250, 250, 250)
    textPane.border = JBUI.Borders.empty()

    val cleanedMessage = message.removePrefix("```cpp\n").removeSuffix("\n```")

    var htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <body style="font-family: 'Segoe UI', sans-serif; font-size: 13px; color: #2b2b2b; background-color: #f4f4f4; padding: 16px;">
            <div style="background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 6px rgba(0,0,0,0.1); padding: 16px;">
                <h2 style="margin-top: 0; text-align: center; color: #3c3f41;">📊 Funcția analizată</h2>
                <pre style="background-color:#2b2b2b; color: #f1f1f1; padding:12px; border-radius:6px; overflow-x:auto; font-family: Consolas, monospace; font-size: 13px;">
${escapeHtml(cleanedMessage)}
                </pre>
                <h3 style="color: #3c3f41;">💡 Recomandări de optimizare</h3>
                <div style="background-color: #eef5ff; border-left: 4px solid #3c78d8; padding: 10px; border-radius: 6px; font-size: 11px;">
                    ${escapeHtml(recomendation)}
                </div>
                <div id="fullSolution"></div>
            </div>
        </body>
        </html>
    """.trimIndent())

    textPane.text = htmlBuilder.toString()

    val scrollPane = JBScrollPane(textPane)
    scrollPane.border = JBUI.Borders.empty()
    panel.add(scrollPane, BorderLayout.CENTER)

    val button = JButton("View entire solution")
    button.addActionListener {
        ApplicationManager.getApplication().executeOnPooledThread {
            val fullHtmlMessage = fetchFullSolutionHtml()
            SwingUtilities.invokeLater {
                if (fullHtmlMessage != null) {
                    val cleanedFull = fullHtmlMessage.removePrefix("```cpp\n").removeSuffix("\n```")

                    // Recompunem tot HTML-ul
                    val finalHtml = """
                        <html>
                        <body style="font-family: 'Segoe UI', sans-serif; font-size: 13px; color: #2b2b2b; background-color: #f4f4f4; padding: 16px;">
                            <div style="background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 6px rgba(0,0,0,0.1); padding: 16px;">
                                <h2 style="margin-top: 0; text-align: center; color: #3c3f41;">📊 Funcția analizată</h2>
                                <pre style="background-color:#2b2b2b; color: #f1f1f1; padding:12px; border-radius:6px; overflow-x:auto; font-family: Consolas, monospace; font-size: 13px;">
    ${escapeHtml(cleanedMessage)}
                                </pre>
                                <h3 style="color: #3c3f41;">💡 Recomandări de optimizare</h3>
                                <div style="background-color: #eef5ff; border-left: 4px solid #3c78d8; padding: 10px; border-radius: 6px; font-size: 11px;">
                                    ${escapeHtml(recomendation)}
                                </div>
                                <br/>
                                <h3 style="color: #3c3f41;">🧩 Soluția completă</h3>
                                <pre style="background-color:#2b2b2b; color: #f1f1f1; padding:12px; border-radius:6px; overflow-x:auto; font-family: Consolas, monospace; font-size: 13px;">
    ${escapeHtml(cleanedFull)}
                                </pre>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()

                    textPane.text = finalHtml
                    textPane.caretPosition = textPane.document.length
                } else {
                    textPane.text = "<html><body><p style='color:red;'>Eroare la încărcarea soluției complete.</p></body></html>"
                }
            }
        }
    }

    val buttonPanel = JPanel().apply {
        layout = BorderLayout()
        border = JBUI.Borders.emptyTop(8)
        add(button, BorderLayout.CENTER)
    }

    panel.add(buttonPanel, BorderLayout.SOUTH)

    val content = ContentFactory.getInstance().createContent(panel, "", false)
    toolWindow.contentManager.removeAllContents(true)
    toolWindow.contentManager.addContent(content)
}

private fun fetchFullSolutionHtml(): String? {
    return try {
        val url = URL("http://172.20.10.2:5000/new_source")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
        connection.disconnect()
        val json = JSONObject(response)
        json.getString("message")
    } catch (e: Exception) {
        null
    }
}

private fun escapeHtml(code: String): String {
    return code.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace(" ", "&nbsp;")
        .replace("\n", "<br/>")
}
