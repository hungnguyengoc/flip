package flip

import org.specs2.mutable._
import org.specs2.ScalaCheck
import flip.pdf.AdaPerSketch

class PackageSpec extends Specification with ScalaCheck {

  "Package" should {

    "basic sketch" in {
      Sketch.empty[Double] must beAnInstanceOf[Sketch[Double]]
    }

    "basic sketch type" in {
      Sketch.empty[Double] must beAnInstanceOf[AdaPerSketch[Double]]
    }

    "sketch with custom conf" in {
      val (cmapSize, cmapNo, cmapStart, cmapEnd) = (10, 2, 0, 10)
      val (counterSize, counterNo) = (8, 2)
      implicit val conf: SketchConf = SketchConf(cmapSize, cmapNo, cmapStart, cmapEnd, counterSize, counterNo)

      Sketch.empty[Double] must beAnInstanceOf[AdaPerSketch[Double]]
    }

  }

}