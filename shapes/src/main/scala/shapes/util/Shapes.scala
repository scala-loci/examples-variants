package shapes
package util

import upickle.Js
import upickle.default._
import retier.rescalaTransmitter._


final case class Position(x: Double, y: Double)

final case class Transformation(scaleX: Double, scaleY: Double, angle: Double)

final case class Figure(id: Long, shape: Shape, color: String,
  position: Position, transformation: Transformation)


sealed trait Shape

final case class Rect(width: Double, height: Double) extends Shape

final case class Circle(radius: Double) extends Shape

final case class Triangle(width: Double, height: Double) extends Shape


/*
 * the code commented out below should suffice but fails at runtime
 * so we need to use some workarounds
 * this should probably be fixed in the upickle library
 */

object Position {
  implicit def defaultPosition[T] = SignalDefaultValue(Position(0, 0))

//  implicit val reader: Reader[Position] = implicitly[Reader[Position]]
//  implicit val writer: Writer[Position] = implicitly[Writer[Position]]

  implicit val reader: Reader[Position] = Reader { case value =>
    val (left, top) = readJs[(Double, Double)](value)
    Position(left, top)
  }
  implicit val writer: Writer[Position] = Writer {
    case Position(left, top) => writeJs((left, top))
  }
}

object Transformation {
  implicit def defaultTransformation[T] = SignalDefaultValue(Transformation(1, 1, 0))

//  implicit val reader: Reader[Transformation] = implicitly[Reader[Transformation]]
//  implicit val writer: Writer[Transformation] = implicitly[Writer[Transformation]]

  implicit val reader: Reader[Transformation] = Reader { case value =>
    val (scaleX, scaleY, angle) = readJs[(Double, Double, Double)](value)
    Transformation(scaleX, scaleY, angle)
  }
  implicit val writer: Writer[Transformation] = Writer {
    case Transformation(scaleX, scaleY, angle) => writeJs((scaleX, scaleY, angle))
  }
}

object Figure {
//  implicit val reader: Reader[Figure] = implicitly[Reader[Figure]]
//  implicit val writer: Writer[Figure] = implicitly[Writer[Figure]]

  implicit val reader: Reader[Figure] = Reader { case value =>
    val (id, shape, color, position, transformation) =
      readJs[(Long, Shape, String, Position, Transformation)](value)
    Figure(id, shape, color, position, transformation)
  }
  implicit val writer: Writer[Figure] = Writer {
    case Figure(id, shape, color, position, transformation) =>
      writeJs((id, shape, color, position, transformation))
  }

}

object Shape {
//  implicit val reader: Reader[Shape] = implicitly[Reader[Shape]]
//  implicit val writer: Writer[Shape] = implicitly[Writer[Shape]]

  implicit val reader: Reader[Shape] = Reader {
    case value: Js.Obj if value("$type").value == "shapes.util.Rect" =>
      readJs[Rect](value)
    case value: Js.Obj if value("$type").value == "shapes.util.Circle" =>
      readJs[Circle](value)
    case value: Js.Obj if value("$type").value == "shapes.util.Triangle" =>
      readJs[Triangle](value)
  }
  implicit val writer: Writer[Shape] = Writer[Shape] {
    case shape: Rect => writeJs(shape)
    case shape: Circle => writeJs(shape)
    case shape: Triangle => writeJs(shape)
  }
}
