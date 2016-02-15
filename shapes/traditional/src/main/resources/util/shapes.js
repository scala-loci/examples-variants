function createPosition(x, y) {
  return { x: x, y: y }
}

function samePosition(a, b) {
  return a.x === b.x && a.y === b.y
}


function createTransformation(scaleX, scaleY, angle) {
  return { scaleX: scaleX, scaleY: scaleY, angle: angle }
}

function sameTransformation(a, b) {
  return a.scaleX === b.scaleX && a.scaleY === b.scaleY && a.angle === b.angle
}


function createRect(width, height) {
  return { $type: "Rect", width: width, height: height }
}

function createCircle(radius) {
  return { $type: "Circle", radius: radius }
}

function createTriangle(width, height) {
  return { $type: "Triangle", width: width, height: height }
}

function sameShape(a, b) {
  return a.$type === b.$type &&
    a.width === b.width && a.height === b.height &&
    a.radius === b.radius
}


function createFigure(id, shape, color, position, transformation) {
  return {
    id: id, shape: shape, color: color,
    position: position, transformation: transformation,
  }
}

function sameFigure(a, b) {
  return a.id === b.id && sameShape(a.shape, b.shape) && a.color === b.color &&
    samePosition(a.position, b.position) &&
    sameTransformation(a.transformation, b.transformation)
}
