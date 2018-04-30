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

  implicit val createPickler: ReadWriter[Create] = macroRW[Create]
  implicit val changePickler: ReadWriter[Change] = macroRW[Change]
  implicit val removePickler: ReadWriter[Remove] = macroRW[Remove]
  implicit val modificationPickler: ReadWriter[Modification] = macroRW[Modification]
}
