package chat

import upickle.default._

package object util {
  implicit val userPickler: ReadWriter[User] = macroRW[User]
}
