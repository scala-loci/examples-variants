package chat

import upickle.default._

package object util {
  implicit val userPickler: ReadWriter[User] = macroRW[User]
  implicit val changeNamePickler: ReadWriter[ChangeName] = macroRW[ChangeName]
  implicit val connectPickler: ReadWriter[Connect] = macroRW[Connect]
  implicit val messagePickler: ReadWriter[Message] = macroRW[Message]
}
