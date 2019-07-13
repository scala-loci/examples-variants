package shapes
package util

import upickle.implicits.key


case class Position(x: Double, y: Double)

case class Transformation(scaleX: Double, scaleY: Double, angle: Double)

case class Figure(id: Int, shape: Shape, color: String,
  position: Position, transformation: Transformation)


sealed trait Shape
@key("Rect") case class Rect(width: Double, height: Double) extends Shape
@key("Circle") case class Circle(radius: Double) extends Shape
@key("Triangle") case class Triangle(width: Double, height: Double) extends Shape


sealed trait Modification
@key("Create") final case class Create(figure: Figure) extends Modification
@key("Change") final case class Change(figure: Figure) extends Modification
@key("Remove") final case class Remove(figure: Figure) extends Modification


sealed trait Update
@key("InitialPosition") final case class InitialPosition(position: Position) extends Update
@key("Figures") final case class Figures(figures: Seq[Figure]) extends Update
