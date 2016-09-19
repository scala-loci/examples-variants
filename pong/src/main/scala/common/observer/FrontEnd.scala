package common
package observer

trait FrontEnd {
  def updateAreas(areas: List[Area]): Unit
  def updateBall(ball: Point): Unit
  def updateScore(score: String): Unit
}

trait FrontEndHolder {
  def createFrontEnd(areas: List[Area], ball: Point, score: String): FrontEnd

  def createFrontEnd: FrontEnd

  val mousePosition: Observable[Point]
}
