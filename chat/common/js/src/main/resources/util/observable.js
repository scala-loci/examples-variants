"use strict"

function Observable(init) {
  this._value = init
  this._handlers = []
}

Observable.prototype.addObserver = function(handler) {
  this._handlers.push(handler)
}

Observable.prototype.set = function(value) {
  this._value = value
  this._handlers.forEach(function(handler) { handler(value) })
}

Observable.prototype.get = function() {
  return this._value
}
