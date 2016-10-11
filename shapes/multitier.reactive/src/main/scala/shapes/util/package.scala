package shapes

import upickle.default._

package object util {
  implicit val positionPickler: ReadWriter[Position] = macroRW[Position]
  implicit val figurePickler: ReadWriter[Figure] = macroRW[Figure]
}
