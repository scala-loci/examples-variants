package shapes
package util

import upickle.default._
import retier.rescalaTransmitter._


final case class Position(x: Double, y: Double)

final case class Transformation(scaleX: Double, scaleY: Double, angle: Double)

final case class Figure(id: Int, shape: Shape, color: String,
  position: Position, transformation: Transformation)


sealed trait Shape

@key("Rect") final case class Rect(width: Double, height: Double) extends Shape

@key("Circle") final case class Circle(radius: Double) extends Shape

@key("Triangle") final case class Triangle(width: Double, height: Double) extends Shape


object Position {
  implicit def default[T] = SignalDefaultValue(Position(0, 0))
}

object Transformation {
  implicit def default[T] = SignalDefaultValue(Transformation(1, 1, 0))
}

object Figure {
  implicit val reader: Reader[Figure] = {
    implicit val shadow: Reader[Figure] = null
    macroR[Figure]
  }
  implicit val writer: Writer[Figure] = {
    implicit val shadow: Writer[Figure] = null
    macroW[Figure]
  }
}

object Shape {
  implicit val reader: Reader[Shape] = {
    implicit val shadow: Reader[Shape] = null
    macroR[Shape]
  }
  implicit val writer: Writer[Shape] = {
    implicit val shadow: Writer[Shape] = null
    macroW[Shape]
  }
}
