package common
package reactive

import rescala._

object Benchmark {
  trait FrontEnd extends FrontEndHolder {
    def createFrontEnd(
        areas: Signal[List[Area]],
        ball: Signal[Point],
        score: Signal[String]) = new BenchmarkFrontEnd(areas, ball, score, arguments)

    lazy val mousePosition = Benchmark.mousePosition

    def arguments: Array[String]
  }

  val mousePosition = Var(Point(0, 0))
}

class BenchmarkFrontEnd(
    areas: Signal[List[Area]],
    ball: Signal[Point],
    score: Signal[String],
    args: Array[String]) extends FrontEnd {
  val benchmark = new BenchmarkRunner(args) {
    def mouePositionChanged(pos: Point) =
      Benchmark.mousePosition set pos
  }

  ball.changed += benchmark.updateBall // #CB

  areas.changed += benchmark.updateAreas // #CB
}
