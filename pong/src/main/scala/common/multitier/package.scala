package common

import upickle.default._

package object multitier {
  implicit val pointPickler: ReadWriter[Point] = macroRW[Point]
  implicit val areaPickler: ReadWriter[Area] = macroRW[Area]
}
