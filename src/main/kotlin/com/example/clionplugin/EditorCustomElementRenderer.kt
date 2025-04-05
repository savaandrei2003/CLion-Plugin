import com.example.clionplugin.showExecutionDetailsToolWindow
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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
                val logicalPos = editor.xyToLogicalPosition(point)
                val offsetClicked = editor.logicalPositionToOffset(logicalPos)

                if (offsetClicked in inlay.offset..(inlay.offset + text.length)) {
                    SwingUtilities.invokeLater {
                        showExecutionDetailsToolWindow(project, "Detalii pentru:\n$text")
                    }
                }
            }
        })
    }
}
