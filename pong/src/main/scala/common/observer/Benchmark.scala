package common
package observer

object Benchmark {
  trait FrontEnd extends FrontEndHolder {
    def createFrontEnd(areas: List[Area], ball: Point, score: String) =
      createFrontEnd

    def createFrontEnd = new BenchmarkFrontEnd(arguments)

    lazy val mousePosition = Benchmark.mousePosition

    def arguments: Array[String]
  }

  val mousePosition = Observable(Point(0, 0))
}

class BenchmarkFrontEnd(args: Array[String]) extends FrontEnd {
  val benchmark = new BenchmarkRunner(args) {
    def mouePositionChanged(pos: Point) =
      Benchmark.mousePosition.set(pos)
  }

  def updateBall(ball: Point) = benchmark.updateBall(ball)

  def updateAreas(areas: List[Area]) = benchmark.updateAreas(areas)

  def updateScore(score: String) = { }
}
