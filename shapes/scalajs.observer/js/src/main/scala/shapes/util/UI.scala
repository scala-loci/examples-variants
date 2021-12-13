package shapes
package util

class UI {
  val color = Observable("#000000")
  val selectedFigure = Observable(Option.empty[Figure])
  val figureTransformed = Observable(Position(0, 0) -> Transformation(0, 0, 0))
  val addRectangle = Observable(())
  val addCircle = Observable(())
  val addTriangle = Observable(())
  val removeFigure = Observable(())

  private val ui = new common.UI(
    color.set, selectedFigure.set, Function const (()), figureTransformed.set,
    { () => addRectangle.set(()) }, { () => addCircle.set(()) },
    { () => addTriangle.set(()) }, { () => removeFigure.set(()) })

  def updateColor(color: String) = ui.updateColor(color)
  def updateFigures(figures: Seq[Figure]) = ui.updateFigures(figures)
}
