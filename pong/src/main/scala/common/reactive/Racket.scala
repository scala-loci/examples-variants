package common
package reactive

import rescala.default._

case class Racket(x: Int, y: Signal[Int]) {
  val height = 80
  val width = 10

  val boundedYPos = Signal {
   math.min(maxY - height / 2,
     math.max(height / 2,  y()))
  }

  val area = Signal {
    Area(
      x - width / 2,
      boundedYPos() - height / 2,
      width,
      height)
  }
}
