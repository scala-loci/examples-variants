package common

import loci.transmitter._
import upickle.default._

package object multitier {
  implicit val pointTransmittable: IdenticallyTransmittable[Point] = IdenticallyTransmittable()
  implicit val areaTransmittable: IdenticallyTransmittable[Area] = IdenticallyTransmittable()

  implicit val pointPickler: ReadWriter[Point] = macroRW[Point]
  implicit val areaPickler: ReadWriter[Area] = macroRW[Area]
}
