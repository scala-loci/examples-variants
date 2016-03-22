function createChat(id, name, unread, active) {
  return { id: id, name: name, unread: unread, active: active }
}

function createChatLog(id, name, unread, log) {
  return { id: id, name: name, unread: unread, log: log }
}

function createMessage(content, own) {
  return { content: content, own: own }
}
