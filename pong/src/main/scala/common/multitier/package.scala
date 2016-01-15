package common

import retier.transmission.RemoteRef
import retier.transmission.PullBasedTransmittable
import retier.rescalaTransmitter.SignalDefaultValue

package object multitier {
  implicit object transmittablePoint
      extends PullBasedTransmittable[Point, (Int, Int), Point] {
    def send(value: Point, remote: RemoteRef) =
      (value.x, value.y)
    def receive(value: (Int, Int), remote: RemoteRef) =
      Point(value._1, value._2)
  }

  implicit object transmittableArea
      extends PullBasedTransmittable[Area, (Int, Int, Int, Int), Area] {
    def send(value: Area, remote: RemoteRef) =
      (value.x, value.y, value.width, value.height)
    def receive(value: (Int, Int, Int, Int), remote: RemoteRef) =
      Area(value._1, value._2, value._3, value._4)
  }
  
  implicit object defaultPoint extends SignalDefaultValue(Point(0, 0))
  
  implicit object defaultArea extends SignalDefaultValue(Area(0, 0, 0, 0))
}
