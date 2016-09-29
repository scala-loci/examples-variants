package chat
package common

import util._

import scala.scalajs.js.JSConverters._
import scala.scalajs.js.Dynamic
import scala.scalajs.js.Dynamic.global

class UI(
    name: String,
    nameChanged: String => Unit,
    chatRequested: User => Unit,
    chatSelected: Chat => Unit,
    chatClosed: Chat => Unit,
    messageSent: String => Unit) {
  private object ui {
    var username: Dynamic = _
    var chats: Dynamic = _
    var users: Dynamic = _
    var nousers: Dynamic = _
    var chatlog: Dynamic = _
    var message: Dynamic = _
    var send: Dynamic = _
  }

  private val $ = global.$

  def updateUsers(users: Seq[User]): Unit = $ { () =>
    ui.users.find("> li:not(:last)").remove()

    if (users.nonEmpty) {
      ui.nousers.hide()
      ui.users prepend (users map { case user @ User(_, name) =>
        $("""<li/>""") append (
          $("""<a href="#"/>""") click { event: Dynamic =>
            event.preventDefault()
            chatRequested(user)
          }
          text name)
      }).toJSArray
    }
    else
      ui.nousers.show()
  }

  def updateChats(chats: Seq[Chat]): Unit = $ { () =>
    ui.chats.find("> li:not(:last)").remove()

    ui.chats prepend (chats map { case chat @ Chat(_, name, unread, active) =>
      val button = $("""<button type="button" class="close">×</button>""")
      val badge = $("""<span class="badge"/>""")
      if (unread > 0)
        badge text unread
      val item = $("""<li/>""")
      if (active)
        item addClass "active"
      item append (
        $("""<a href="#"/>""") click { event: Dynamic =>
          event.preventDefault()
          chatSelected(chat)
        }
        text name append (" ", badge, " ", button click { event: Dynamic =>
          event.preventDefault()
          chatClosed(chat)
        }))
    }).toJSArray
  }

  def updateMessages(messages: Seq[Message]): Unit = $ { () =>
    ui.chatlog.empty()

    ui.chatlog append (messages map { case Message(content, own) =>
      $("""<li/>""") addClass (if (own) "own" else "foreign") text content
    }).toJSArray
  }

  def clearMessage = $ { () =>
    ui.message.`val`("")
  }

  $ { () =>
    ui.username = global $ "#username"
    ui.chats = global $ "#chats"
    ui.users = global $ "#users"
    ui.nousers = global $ "#nousers"
    ui.chatlog = global $ "#chatlog"
    ui.message = global $ "#message"
    ui.send = global $ "#send"

    val placeholder = name

    ui.username attr ("placeholder", placeholder)

    ui.username on ("input", { () =>
      nameChanged(ui.username.`val`().toString match {
        case username if username.trim == "" => placeholder
        case username => username
      })
    })

    ui.username trigger "input"

    ui.nousers on ("click", { event: Dynamic => event.preventDefault() })

    ui.send on ("click", { () => messageSent(ui.message.`val`().toString) })

    ui.message on ("keyup", { event: Dynamic =>
      if (event.keyCode == 13) {
        event.preventDefault()
        ui.send trigger "click"
      }
    })
  }
}
