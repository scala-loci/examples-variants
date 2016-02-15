$(function() {
  function create(figure) { return { $type: "Create", figure: figure } }
  function change(figure) { return { $type: "Change", figure: figure } }
  function remove(figure) { return { $type: "Remove", figure: figure } }

  var socket = new WebSocket("ws://localhost:8080")
  var figureInitialPosition = createPosition(0, 0)

  var ui = new UI(
    figureTransformed, figureSelected, removeFigure, colorChanged,
    addRectangleEvent, addCircleEvent, addTriangleEvent)

  socket.onmessage = function(event) {
    var message = JSON.parse(event.data)
    if (Array.isArray(message))
      ui.setFigures(message)
    else
      figureInitialPosition = message
  }

  function figureTransformed(properties) {
    if (ui.selectedFigure != null) {
      var figure = createFigure(
        ui.selectedFigure.id, ui.selectedFigure.shape, ui.selectedFigure.color,
        properties.position, properties.transformation)
      figureChanged(figure)
    }
  }

  function colorChanged(color) {
    if (ui.selectedFigure != null) {
      var figure = createFigure(
        ui.selectedFigure.id, ui.selectedFigure.shape, color,
        ui.selectedFigure.position, ui.selectedFigure.transformation)
      figureChanged(figure)
    }
  }

  function figureChanged(figure) {
    socket.send(JSON.stringify(change(figure)))
  }

  function figureSelected(selectedFigure) {
    if (ui.selectedFigure != null)
      ui.setColor(ui.selectedFigure.color)
  }

  function removeFigure() {
    if (ui.selectedFigure != null)
      socket.send(JSON.stringify(remove(ui.selectedFigure)))
  }

  function addRectangleEvent() {
    figureCreated(createRect(50, 50))
  }

  function addCircleEvent() {
    figureCreated(createCircle(25))
  }

  function addTriangleEvent() {
    figureCreated(createTriangle(50, 50))
  }

  function figureCreated(shape) {
    var transformation = createTransformation(1, 1, 0)
    var position = figureInitialPosition
    var id = (Math.floor(Math.random() * 4294967296) - 2147483648) | 0
    var figure = createFigure(id, shape, ui.color, position, transformation)

    socket.send(JSON.stringify(create(figure)))
  }
})
