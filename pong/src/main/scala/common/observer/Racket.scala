package common
package observer

case class Racket(x: Int, y: Int) {
  val height = 80
  val width = 10

  private def calcArea(pos: Int) = {
    val boundedYPos =
      math.min(maxY - height / 2,
        math.max(height / 2,  pos))

    Area(
      x - width / 2,
      boundedYPos - height / 2,
      width,
      height)
  }

  def updateYPos(pos: Int) =
    area set calcArea(pos)

  val area = Observable(calcArea(y))
}
