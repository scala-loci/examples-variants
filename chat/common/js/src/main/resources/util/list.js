"use strict"


function List() { }

List.prototype.toArray = function() {
  var array = []
  var list = this
  while (list instanceof Cons) {
    array.push(list.head)
    list = list.tail
  }
  return array
}

List.prototype.map = function(callback) {
  return this instanceof Cons
    ? cons(callback(this.head), this.tail.map(callback))
    : nil
}

List.prototype.filter = function(callback) {
  return this instanceof Cons
    ? (callback(this.head)
      ? cons(this.head, this.tail.filter(callback))
      : this.tail.filter(callback))
    : nil
}

List.prototype.find = function(callback) {
  if (this instanceof Cons)
    return callback(this.head) ? this.head : this.tail.find(callback)
}


function Nil() { }

Nil.prototype = new List()
Nil.prototype.constructor = Nil


function Cons(head, tail) {
  this.head = head
  this.tail = tail
}

Cons.prototype = new List()
Cons.prototype.constructor = Cons


var nil = new Nil()

function cons(head, tail) { return new Cons(head, tail) }
