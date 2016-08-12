package chat
package util

import rescala._

import scala.scalajs.js.JSConverters._
import scala.scalajs.js.Array
import scala.scalajs.js.Function1
import scala.scalajs.js.Dynamic
import scala.scalajs.js.Dynamic.global
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.Dynamic.newInstance

class UI {
  private var _users: Signal[Seq[User]] = Signal { Seq.empty[User] }
  private var _chats: Signal[Seq[Chat]] = Signal { Seq.empty[Chat] }
  private var _messages: Signal[Seq[Message]] = Signal { Seq.empty[Message] }
  private var _clearMessage: Event[Unit] = Evt()

  private val nameVar = Var("Anonymous")
  private val chatRequestedEvent = Evt[User]
  private val chatSelectedEvent = Evt[Chat]
  private val chatClosedEvent = Evt[Chat]
  private val messageSentEvent = Evt[String]

  val name: Signal[String] = nameVar
  val chatRequested: Event[User] = chatRequestedEvent
  val chatSelected: Event[Chat] = chatSelectedEvent
  val chatClosed: Event[Chat] = chatClosedEvent
  val messageSent: Event[String] = messageSentEvent

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

  private def updateUsers(users: Seq[User]) = {
    ui.users.find("> li:not(:last)").remove()

    if (users.nonEmpty) {
      ui.nousers.hide()
      ui.users prepend (users map { case user @ User(_, name) =>
        $("""<li/>""") append (
          $("""<a href="#"/>""") click { event: Dynamic =>
            event.preventDefault()
            chatRequestedEvent(user)
          }
          text name)
      }).toJSArray
    }
    else
      ui.nousers.show()
  }

  private def updateChats(chats: Seq[Chat]) = {
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
          chatSelectedEvent(chat)
        }
        text name append (" ", badge, " ", button click { event: Dynamic =>
          event.preventDefault()
          chatClosedEvent(chat)
        }))
    }).toJSArray
  }

  private def updateMessages(messages: Seq[Message]) = {
    ui.chatlog.empty()

    ui.chatlog append (messages map { case Message(content, own) =>
      $("""<li/>""") addClass (if (own) "own" else "foreign") text content
    }).toJSArray
  }

  $ { () =>
    ui.username = global $ "#username"
    ui.chats = global $ "#chats"
    ui.users = global $ "#users"
    ui.nousers = global $ "#nousers"
    ui.chatlog = global $ "#chatlog"
    ui.message = global $ "#message"
    ui.send = global $ "#send"

    val placeholder = nameVar.now

    ui.username attr ("placeholder", placeholder)

    ui.username on ("input", { () =>
      nameVar() = ui.username.`val`().toString match {
        case username if username.trim == "" => placeholder
        case username => username
      }
    })

    ui.username trigger "input"

    ui.nousers on ("click", { event: Dynamic => event.preventDefault() })

    ui.send on ("click", { () => messageSentEvent(ui.message.`val`().toString) })

    ui.message on ("keyup", { event: Dynamic =>
      if (event.keyCode == 13) {
        event.preventDefault()
        ui.send trigger "click"
      }
    })
  }

  def users = _users
  
  def users_=(users: Signal[Seq[User]]) = $ { () =>
    _users = users
    users.changed += updateUsers
    updateUsers((users withDefault Seq.empty).now)
  }

  def chats = _chats
  
  def chats_=(chats: Signal[Seq[Chat]]) = $ { () =>
    _chats = chats
    chats.changed += updateChats
    updateChats((chats withDefault Seq.empty).now)
  }

  def messages = _messages
  
  def messages_=(messages: Signal[Seq[Message]]) = $ { () =>
    _messages = messages
    messages.changed += updateMessages
    updateMessages((messages withDefault Seq.empty).now)
  }

  def clearMessage = _clearMessage
  
  def clearMessage_=(clearMessage: Event[Unit]) = $ { () =>
    _clearMessage = clearMessage
    clearMessage += { _ => ui.message.`val`("") }
  }
}
