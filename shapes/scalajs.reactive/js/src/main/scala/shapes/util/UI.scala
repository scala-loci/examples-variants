package shapes
package util

import rescala.default._

class UI {
  val color = Var("#000000")
  val selectedFigure = Var(Option.empty[Figure])
  val figureSelected = Evt[Figure]
  val figureTransformed = Evt[(Position, Transformation)]
  val addRectangle = Evt[Unit]
  val addCircle = Evt[Unit]
  val addTriangle = Evt[Unit]
  val removeFigure = Evt[Unit]

  private val ui = new common.UI(
    color.set, selectedFigure.set, figureSelected.fire, figureTransformed.fire,
    { () => addRectangle.fire }, { () => addCircle.fire },
    { () => addTriangle.fire }, { () => removeFigure.fire })

  def changeColor: Event[String] = ???
  def changeColor_=(changeColor: Event[String]) = { changeColor observe ui.updateColor }

  def figures: Signal[Seq[Figure]] = ???
  def figures_=(figures: Signal[Seq[Figure]]) = { figures observe ui.updateFigures }
}
