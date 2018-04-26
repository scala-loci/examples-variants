$(function() {
  "use strict"

  function create(figure) { return { $type: "Create", figure: figure } }
  function change(figure) { return { $type: "Change", figure: figure } }
  function remove(figure) { return { $type: "Remove", figure: figure } }

  var socket = new WebSocket("ws://localhost:8080")
  var figureInitialPosition = createPosition(0, 0)

  var ui = new UI(
    figureTransformed, figureSelected, removeFigure, colorChanged,
    addRectangle, addCircle, addTriangle)

  socket.onmessage = function(event) { // #REMOTE-RECV #CB
    var message = JSON.parse(event.data)
    if (Array.isArray(message))
      ui.setFigures(message) // #IMP-STATE
    else
      figureInitialPosition = message // #IMP-STATE
  }

  function figureTransformed(properties) { // #CB
    if (ui.selectedFigure != null) {
      var figure = createFigure(
        ui.selectedFigure.id, ui.selectedFigure.shape, ui.selectedFigure.color,
        properties.position, properties.transformation)
      figureChanged(figure)
    }
  }

  function colorChanged(color) { // #CB
    if (ui.selectedFigure != null) {
      var figure = createFigure(
        ui.selectedFigure.id, ui.selectedFigure.shape, color,
        ui.selectedFigure.position, ui.selectedFigure.transformation)
      figureChanged(figure)
    }
  }

  function figureChanged(figure) {
    socket.send(JSON.stringify(change(figure))) // #REMOTE-SEND
  }

  function figureSelected(selectedFigure) { // #CB
    if (ui.selectedFigure != null)
      ui.setColor(ui.selectedFigure.color) // #IMP-STATE
  }

  function removeFigure() { // #CB
    if (ui.selectedFigure != null)
      socket.send(JSON.stringify(remove(ui.selectedFigure))) // #REMOTE-SEND
  }

  function addRectangle() { // #CB
    figureCreated(createRect(50, 50))
  }

  function addCircle() { // #CB
    figureCreated(createCircle(25))
  }

  function addTriangle() { // #CB
    figureCreated(createTriangle(50, 50))
  }

  function figureCreated(shape) {
    var transformation = createTransformation(1, 1, 0)
    var position = figureInitialPosition
    var id = (Math.floor(Math.random() * 4294967296) - 2147483648) | 0
    var figure = createFigure(id, shape, ui.color, position, transformation)

    socket.send(JSON.stringify(create(figure))) // #REMOTE-SEND
  }
})
