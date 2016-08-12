package shapes
package util

import upickle.default._

object shapes {
  case class Position(x: Double, y: Double)

  case class Transformation(scaleX: Double, scaleY: Double, angle: Double)

  case class Figure(id: Int, shape: Shape, color: String,
    position: Position, transformation: Transformation)


  sealed trait Shape

  @key("Rect") case class Rect(width: Double, height: Double) extends Shape

  @key("Circle") case class Circle(radius: Double) extends Shape

  @key("Triangle") case class Triangle(width: Double, height: Double) extends Shape


  implicit val positionPickler: ReadWriter[Position] = macroRW[Position]
  implicit val transformationPickler: ReadWriter[Transformation] = macroRW[Transformation]
  implicit val figurePickler: ReadWriter[Figure] = macroRW[Figure]
  implicit val shapePickler: ReadWriter[Shape] = macroRW[Shape]
}
