package chat

import loci.transmitter._
import upickle.default._

package object util {
  implicit val userTransmittable: IdenticallyTransmittable[User] = IdenticallyTransmittable()
  implicit val userPickler: ReadWriter[User] = macroRW[User]
}
