package shapes

import loci.transmitter._
import upickle.default._

package object util {
  implicit val positionTransmittable: IdenticallyTransmittable[Position] = IdenticallyTransmittable()
  implicit val figureTransmittable: IdenticallyTransmittable[Figure] = IdenticallyTransmittable()

  implicit val positionPickler: ReadWriter[Position] = macroRW[Position]
  implicit val transformationPickler: ReadWriter[Transformation] = macroRW[Transformation]
  implicit val figurePickler: ReadWriter[Figure] = macroRW[Figure]

  implicit val rectPickler: ReadWriter[Rect] = macroRW[Rect]
  implicit val circlePickler: ReadWriter[Circle] = macroRW[Circle]
  implicit val trianglePickler: ReadWriter[Triangle] = macroRW[Triangle]
  implicit val shapePickler: ReadWriter[Shape] = macroRW[Shape]
}
