package shapes
package util

import upickle.Js
import upickle.default._


final case class Position(x: Double, y: Double)

final case class Transformation(scaleX: Double, scaleY: Double, angle: Double)

final case class Figure(id: Int, shape: Shape, color: String,
  position: Position, transformation: Transformation)


sealed trait Shape

@key("Rect") final case class Rect(width: Double, height: Double) extends Shape

@key("Circle") final case class Circle(radius: Double) extends Shape

@key("Triangle") final case class Triangle(width: Double, height: Double) extends Shape
