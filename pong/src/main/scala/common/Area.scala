package common

case class Area(x: Int, y: Int, width: Int, height: Int) {
  def contains(p: Point) =
    p.x >= x && p.x <= x + width &&
    p.y >= y && p.y <= y + height
}
