import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle

class ExecutionTimeRenderer(private val text: String) : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val fontMetrics = inlay.editor.component.getFontMetrics(JBFont.create(Font("Consolas", Font.ITALIC, 12)))
        return fontMetrics.stringWidth(text)
    }

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        g.color = JBColor.GRAY
        g.font = JBFont.create(Font("Consolas", Font.ITALIC, 12))
        g.drawString(text, targetRegion.x, targetRegion.y + g.fontMetrics.ascent)
    }
}
