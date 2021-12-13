package common

import scala.util.Random
import scala.annotation.nowarn
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class BenchmarkCommandLine(args: Array[String]) {
  case object racket
  case object moves
  case object iterations

  val arguments =
    try {
      (args.sliding(2, 1) collect {
        case Array("--racket", count) => racket -> count.toInt
        case Array("--moves", count) => moves -> count.toInt
        case Array("--iterations", count) => iterations -> count.toInt
      }).toMap
    }
    catch {
      case _: NumberFormatException =>
        throw new IllegalArgumentException("integer expected for argument")
    }

  val racketIndex = arguments getOrElse (racket,
    throw new IllegalArgumentException("racket argument missing"))

  val totalIterations = arguments getOrElse (iterations,
    throw new IllegalArgumentException("iterations argument missing"))

  val totalMovesPerIteration = arguments getOrElse (moves,
    throw new IllegalArgumentException("moves argument missing"))
}

@nowarn("msg=early initializers") abstract class BenchmarkRunner(args: Array[String]) extends {
  val commandLine = new BenchmarkCommandLine(args)
  val racketIndex = commandLine.racketIndex
  val totalIterations = commandLine.totalIterations
  val totalMovesPerIteration = commandLine.totalMovesPerIteration
  val warmupIterations = 10 * math.max(1, (5000 / totalMovesPerIteration))
} with Benchmark


trait Benchmark {
  val racketIndex: Int
  val totalIterations: Int
  val totalMovesPerIteration: Int
  val warmupIterations: Int

  private[this] var iteration = -warmupIterations
  private[this] var previousPos = 0
  private[this] val random = new Random(racketIndex)

  private[this] var count: Int = _
  private[this] var started: Boolean = _
  private[this] var running: Boolean = _
  private[this] var time: Long = _
  private[this] var area: Area = _

  private[this] val results = new Array[Long](totalIterations)

  def updateBall(ball: Point) =
    if (!started) {
      println(
        s"$totalIterations iterations with $totalMovesPerIteration mouse moves each " +
        s"(plus $warmupIterations warm-up iterations)")

      started = true
      moveMouse()
    }

  private def moveMouse(): Unit = Future {
    if (!running) {
      mouePositionChanged(Point(100, 0))
      Thread.sleep(10)
      moveMouse()
    }
    else synchronized {
      time = System.nanoTime
      for (i <- 1 to totalMovesPerIteration) {
        var currentPos = 0
        do currentPos = (random nextInt 200) + 100 while (currentPos == previousPos)
        previousPos = currentPos
        mouePositionChanged(Point(100, currentPos))
      }
    }
  }

  protected def mouePositionChanged(pos: Point): Unit

  def updateAreas(areas: List[Area]) = synchronized {
    if (racketIndex < areas.size && areas(racketIndex) != area) {
      area = areas(racketIndex)

      if (area.y == 0)
        running = true
      else if (running) {
        count += 1
        if (count >= totalMovesPerIteration) {
          iteration += 1

          if (iteration < totalIterations) {
            if (iteration >= 0)
              results(iteration) = System.nanoTime - time

            count = 0
            moveMouse()
          }
          else if (iteration == totalIterations) {
            val min = results.min / totalMovesPerIteration / 1000
            val max = results.max / totalMovesPerIteration / 1000
            val mean = results.sum.toDouble / results.size / totalMovesPerIteration / 1000
            val variance = (results.foldLeft(0.0) { (variance, element) =>
              val diff = element / totalMovesPerIteration / 1000 - mean
              variance + diff * diff
            }) / (results.size - 1)
            val standardDeviation = math.sqrt(variance)
            val standardErrorMean = standardDeviation / math.sqrt(results.size)

            println(s"MIN: ${min}μs")
            println(s"MAX: ${max}μs")
            println(s"AVG: ${mean.toLong}μs")
            println(s"SEM: ${standardErrorMean.toLong}μs")
            println(s"SD:  ${standardDeviation.toLong}μs")
          }
        }
      }
    }
  }
}
