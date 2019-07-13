package common
package reactive

import rescala.default._

trait FrontEnd

trait FrontEndHolder {
  def createFrontEnd(
    areas: Signal[List[Area]],
    ball: Signal[Point],
    score: Signal[String]): FrontEnd

  val mousePosition: Signal[Point]
}
