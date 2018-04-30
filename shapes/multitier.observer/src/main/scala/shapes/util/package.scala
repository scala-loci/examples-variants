package shapes

import upickle.default._

package object util {
  implicit val positionPickler: ReadWriter[Position] = macroRW[Position]
  implicit val transformationPickler: ReadWriter[Transformation] = macroRW[Transformation]
  implicit val figurePickler: ReadWriter[Figure] = macroRW[Figure]

  implicit val rectPickler: ReadWriter[Rect] = macroRW[Rect]
  implicit val circlePickler: ReadWriter[Circle] = macroRW[Circle]
  implicit val trianglePickler: ReadWriter[Triangle] = macroRW[Triangle]
  implicit val shapePickler: ReadWriter[Shape] = macroRW[Shape]
}
