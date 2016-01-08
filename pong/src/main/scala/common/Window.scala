package common

import swing.Panel
import swing.MainFrame
import java.awt.Font
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Dimension

class Window(
    var areas: List[Area],
    var ball: Point,
    var score: String) {
  val panel = new Panel {
    val scoreFont = new Font("Tahoma", Font.PLAIN, 32)
    preferredSize = new Dimension(maxX, maxY)

    override def paintComponent(g: Graphics2D) {
      super.paintComponent(g)

      g.setColor(Color.DARK_GRAY)
      g.fillOval(ball.x - ballSize / 2, ball.y - ballSize / 2, ballSize, ballSize)

      areas foreach { area =>
        g.fillRect(area.x, area.y, area.width, area.height)
      }

      g.setColor(new Color(200, 100, 50))
      g.setFont(scoreFont)
      g.drawString(score, maxX / 2 - 50, 40)
    }
  }

  val frame = new MainFrame {
    title = "Pong"
    resizable = false
    contents = panel
  }
}
