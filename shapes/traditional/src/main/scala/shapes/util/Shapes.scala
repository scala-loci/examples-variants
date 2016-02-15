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


/*
 * the code commented out below should suffice but fails at runtime
 * we need to use some workarounds
 * this should probably be fixed in the upickle library
 */

object Shape {
//  implicit val reader: Reader[Shape] = implicitly[Reader[Shape]]
//  implicit val writer: Writer[Shape] = implicitly[Writer[Shape]]

  implicit val reader: Reader[Shape] = Reader {
    case value: Js.Obj if value("$type").value == "Rect" =>
      readJs[Rect](value)
    case value: Js.Obj if value("$type").value == "Circle" =>
      readJs[Circle](value)
    case value: Js.Obj if value("$type").value == "Triangle" =>
      readJs[Triangle](value)
  }
  implicit val writer: Writer[Shape] = Writer[Shape] {
    case shape: Rect => writeJs(shape)
    case shape: Circle => writeJs(shape)
    case shape: Triangle => writeJs(shape)
  }
}
