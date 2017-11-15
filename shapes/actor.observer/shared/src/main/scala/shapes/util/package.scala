package shapes

import upickle.default._

package object util {
  implicit val modificationPickler: ReadWriter[Modification] = macroRW[Modification]
  implicit val updatePickler: ReadWriter[Update] = macroRW[Update]
}
