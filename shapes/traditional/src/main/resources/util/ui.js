$(function() {
  window.UI = function(
      figureTransformed, figureSelected, removeFigure, colorChanged,
      addRectangleEvent, addCircleEvent, addTriangleEvent) {
    var self = this
    self.selectedFigure = null
    self.color = ""

    function applyGeneralFigureProperties(figure, obj) {
      obj.set({
        originX: "center",
        originY: "center",
        fill: figure.color,
        left: figure.position.x,
        top: figure.position.y,
        scaleX: figure.transformation.scaleX,
        scaleY: figure.transformation.scaleY,
        angle: figure.transformation.angle})
    }

    function applyFigureProperties(figure, obj) {
      if (figure.shape.$type === "Rect")
        obj.set({ width: figure.shape.width, height: figure.shape.height })
      else if (figure.shape.$type === "Circle")
        obj.set({ radius: figure.shape.radius })
      else if (figure.shape.$type === "Triangle")
        obj.set({ width: figure.shape.width, height: figure.shape.height })

      applyGeneralFigureProperties(figure, obj)
      obj.setCoords()
    }


    function render(figures) {
      var ids = figures.map(function(figure) { return figure.id })
      var map = {}

      ui.canvas.forEachObject(function(obj) {
        if (ids.indexOf(obj.figure.id) != -1 )
          map[obj.figure.id] = obj
        else
          ui.canvas.remove(obj)
      })

      figures.forEach(function(figure) {
        var obj = map[figure.id]
        if (obj) {
          if (!sameFigure(obj.figure, figure)) {
            applyFigureProperties(figure, obj)
            obj.figure = figure
          }
        }
        else {
          if (figure.shape.$type === "Rect")
            obj = new fabric.Rect
          else if (figure.shape.$type === "Circle")
            obj = new fabric.Circle
          else if (figure.shape.$type === "Triangle")
            obj = new fabric.Triangle

          applyFigureProperties(figure, obj)
          obj.figure = figure

          ui.canvas.add(obj)
        }
      })

      ui.canvas.renderAll()
    }

    function objectsModified(options) {
      var obj = options.target
      var figure = obj.figure

      var position = createPosition(obj.left, obj.top)
      var transformation = createTransformation(obj.scaleX, obj.scaleY, obj.angle)

      if (!samePosition(figure.position, position) ||
          !sameTransformation(figure.transformation, transformation))
        figureTransformed({ position: position, transformation: transformation })
    }

    function selectionChanged(options) {
      if (self.selectedFigure == null ||
          !sameFigure(self.selectedFigure, options.target.figure)) {
        self.selectedFigure = options.target.figure
        figureSelected(self.selectedFigure)
      }
    }

    function selectionCleared(options) {
      if (self.selectedFigure != null) {
        self.selectedFigure = null
        figureSelected(self.selectedFigure)
      }
    }


    $(window).keyup(function(event) {
      if (event.keyCode === 46)
        removeFigure()
    })

    ui.colorpicker.on("changeColor", function(event) {
      var color = event.color.toString("hsla")
      if (color != self.color) {
        self.color = color
        colorChanged(self.color)
      }
    })

    self.color = ui.colorpicker.colorpicker("getValue").toString()

    ui.addRectangle.on("click", function() { addRectangleEvent() })
    ui.addCircle.on("click", function() { addCircleEvent() })
    ui.addTriangle.on("click", function() { addTriangleEvent() })

    ui.canvas.renderOnAddRemove = false

    ui.canvas.on({
      "object:modified": objectsModified,
      "object:selected": selectionChanged,
      "selection:cleared": selectionCleared
    })


    self.setColor = function(changeColor) {
      ui.colorpicker.colorpicker("setValue", changeColor)
    }

    self.setFigures = function(figures) {
      if (self.selectedFigure != null)
        for (var i = 0, l = figures.length; i < l; ++i)
          if (figures[i].id === self.selectedFigure.id) {
            self.selectedFigure = figures[i]
            break
          }

      render(figures)
    }
  }
})
