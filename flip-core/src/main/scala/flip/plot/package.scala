package flip

import flip.pdf.Prim
import flip.range.syntax._

package object plot {

  type Block = ((Prim, Double), (Prim, Double))

  object syntax
    extends PlotSyntax
      with DensityPlotSyntax
      with CountPlotSyntax

}
