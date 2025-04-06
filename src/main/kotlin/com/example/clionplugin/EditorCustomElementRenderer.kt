package com.example.clionplugin

import com.example.clionplugin.showExecutionDetailsToolWindow
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import org.codehaus.jettison.json.JSONObject
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.swing.SwingUtilities

class ExecutionTimeRenderer(
    private val text: String,
    private val project: Project
) : EditorCustomElementRenderer {

    private var listenerAdded = false

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        addClickListenerIfNeeded(inlay)
        val fontMetrics = inlay.editor.component.getFontMetrics(JBFont.create(Font("Consolas", Font.ITALIC, 12)))
        return fontMetrics.stringWidth(text)
    }

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        val font = JBFont.create(Font("Consolas", Font.ITALIC, 12))
        g.font = font

        val fm = g.fontMetrics
        val y = targetRegion.y + fm.ascent
        var x = targetRegion.x

        val hasHint = text.contains("Hint:")

        if (hasHint) {
            val regex = Regex("(Functia \\d+: )(CPU Time: .*?\\| )(Register: .*?)(\\| Hint: .*)")
            val match = regex.find(text)

            if (match != null) {
                val (functia, cpuTime, register, hint) = match.destructured

                g.color = JBColor.GRAY
                g.drawString(functia, x, y)
                x += fm.stringWidth(functia)

                g.color = JBColor.RED
                g.drawString(cpuTime, x, y)
                x += fm.stringWidth(cpuTime)

                g.color = JBColor.GRAY
                g.drawString(register, x, y)
                x += fm.stringWidth(register)

                g.color = JBColor.RED
                g.drawString(hint, x, y)
            } else {
                g.color = JBColor.GRAY
                g.drawString(text, x, y)
            }
        } else {
            g.color = JBColor.GRAY
            g.drawString(text, x, y)
        }
    }

    private fun addClickListenerIfNeeded(inlay: Inlay<*>) {
        if (listenerAdded) return
        listenerAdded = true

        val editor = inlay.editor
        val component = editor.contentComponent

        component.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val point = e.point
                val visualPos = editor.xyToVisualPosition(point)
                val clickedX = editor.visualPositionToXY(visualPos).x
                val inlayStartX = editor.visualPositionToXY(editor.offsetToVisualPosition(inlay.offset)).x

                val fontMetrics = editor.contentComponent.getFontMetrics(JBFont.create(Font("Consolas", Font.ITALIC, 12)))
                val fullWidth = fontMetrics.stringWidth(text)

                val hintIndex = text.indexOf("Hint:")
                if (hintIndex != -1) {
                    val hintStartOffset = fontMetrics.stringWidth(text.substring(0, hintIndex))
                    val hintEndOffset = fullWidth

                    val hintStartX = inlayStartX + hintStartOffset
                    val hintEndX = inlayStartX + hintEndOffset

                    if (clickedX in hintStartX..hintEndX) {
                        val response = fetchFunctionBodyFromServer()
                        showExecutionDetailsToolWindow(project, response?: "Eroare la preluarea detaliilor.")
                    }
                }
            }
        })
    }

    private fun fetchFunctionBodyFromServer(): String? {
        return try {
            val url = URL("http://172.20.10.2:5000/function_body")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }

            val json = JSONObject(responseText)
            json.getString("message")
        } catch (e: Exception) {
            null
        }
    }
}
