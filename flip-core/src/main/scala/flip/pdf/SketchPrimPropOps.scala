package flip.pdf

import cats.data.NonEmptyList
import cats.implicits._
import flip.cmap.Cmap
import flip.hcounter.HCounter
import flip.measure.Measure
import flip.pdf.update.{EqualSpaceCdfUpdate, EqualSpaceSmoothingPs, NormalSmoothingPs, SmoothingPs}
import flip.plot._
import flip.range._
import flip.range.syntax._

import scala.collection.mutable
import scala.language.{higherKinds, postfixOps}

/**
  * This Ops introduces the update function with primitive type as a parameter.
  */
trait SketchPrimPropOps[S[_] <: Sketch[_]] extends SketchPrimPropLaws[S] with SketchPropOps[S] { self =>

  def smoothingPs: SmoothingPs = EqualSpaceSmoothingPs

  // Update ops

  /**
    * Update a list of primitive value <code>p</code> without rearranging process only for structures.
    * */
  def primNarrowUpdateForStr[A](sketch: S[A], ps: List[(Prim, Count)]): S[A] =
    modifyStructure(
      sketch,
      strs => {
        val cmapNo = sketch.conf.cmap.no
        val effNo = if (cmapNo > 1) cmapNo - 1 else cmapNo
        val (effStrs, refStrs) = strs.toList.splitAt(effNo)
        def updatePs(cmap: Cmap, counter: HCounter, ps: List[(Prim, Count)]): HCounter =
          counter.updates(ps.map { case (p, count) => (cmap(p), count) })
        val utdEffStrs = effStrs.map { case (cmap, counter) => (cmap, updatePs(cmap, counter, ps)) }

        NonEmptyList.fromListUnsafe(utdEffStrs ++ refStrs)
      }
    )

  /**
    * Deep update a list of primitive value <code>p</code> instead of <code>a</code> ∈ <code>A</code>
    * */
  def primDeepUpdate[A](sketch: S[A], ps: List[(Prim, Count)]): (S[A], Option[Structure]) = {
    val utdCmap = EqualSpaceCdfUpdate.updateCmapForSketch[A](sketch.asInstanceOf[Sketch[A]], ps)
    val seed = ((sum(sketch) + ps.headOption.map(_._1).getOrElse(-1d)) * 1000).toInt
    val emptyCounter = counter(sketch.conf, seed)
    val (utdStrs, oldStrs) = ((utdCmap, emptyCounter) :: sketch.structures).toList.splitAt(sketch.conf.cmap.no)
    val utdSketch1 = modifyStructure(sketch, _ => NonEmptyList.fromListUnsafe(utdStrs))
    val utdSketch2 = if (ps.nonEmpty) {
      primNarrowPlotUpdateForStr(utdSketch1, smoothingPs(ps, 0.5), ps.map(_._2).sum)
    } else utdSketch1

    (utdSketch2, oldStrs.headOption)
  }

  def primNarrowPlotUpdateForStr[A](sketch: S[A], psDist: Dist[Prim], sum: Double): S[A] = {
    val ps = youngCmap(sketch).bin.map { range =>
      // todo range.middle is hacky approach
      (range.middle, psDist.probability(range.start, range.end) * sum)
    }

    primNarrowUpdateForStr(sketch, ps)
  }

  // Read ops

  private var decayRateCache: mutable.Map[(Double, Int), Double] = mutable.HashMap.empty

  private val decayRateCacheLimit: Int = 100

  def decayRate(decayFactor: Double, i: Int): Double = {
    decayRateCache.getOrElse(
      (decayFactor, i), {
        val rate = math.exp(-1 * decayFactor * i)
        decayRateCache.put((decayFactor, i), rate)
        if (decayRateCache.size > decayRateCacheLimit) decayRateCache = decayRateCache.takeRight(decayRateCacheLimit)
        rate
      }
    )
  }

  def singleCount(cmap: Cmap, hcounter: HCounter, pStart: Double, pEnd: Double): Double = {
    val (startHdim, endHdim) = (cmap.apply(pStart), cmap.apply(pEnd))
    val (startRng, endRng) = (cmap.range(startHdim), cmap.range(endHdim))

    // mid count
    val midCount = if ((endHdim - 1) > (startHdim + 1)) {
      hcounter.count(startHdim + 1, endHdim - 1)
    } else 0.0

    // boundary count
    val boundaryCount = if (startHdim == endHdim) {
      val count = hcounter.get(startHdim)
      val percent = startRng.overlapPercent(RangeP(pStart, pEnd))
      count * percent
    } else {
      val startCount = HCounter.get(hcounter, startHdim)
      val startPercent = startRng.overlapPercent(RangeP(pStart, startRng.end))
      val endCount = HCounter.get(hcounter, endHdim)
      val endPercent = endRng.overlapPercent(RangeP(endRng.start, pEnd))
      startCount * startPercent + endCount * endPercent
    }

    midCount + boundaryCount
  }

  def primCountForStr(sketch: S[_], pFrom: Prim, pTo: Prim): Double = {
    val counts = sketch.structures.map { case (cmap, hcounter) => singleCount(cmap, hcounter, pFrom, pTo) }
    val decayRates = (0 until counts.size.toInt)
      .map(i => decayRate(sketch.conf.decayFactor, i))
    val weightedCountSum = (counts.toList zip decayRates).map { case (count, r) => count * r }.sum
    val normalization = decayRates.sum

    weightedCountSum / normalization
  }

  /**
    * Total number of elements be effective memorized.
    * */
  def sumForStr(sketch: S[_]): Double = {
    val sums = sketch.structures.map { case (_, hcounter) => hcounter.sum }

    val decayRates = (0 until sketch.conf.cmap.no).map(i => decayRate(sketch.conf.decayFactor, i))
    val weightedSumSum = (sums.toList zip decayRates).map { case (sum, r) => sum * r }.sum
    val normalization = decayRates.take(sums.size.toInt).sum

    weightedSumSum / normalization
  }

}

trait SketchPrimPropLaws[S[_] <: Sketch[_]] { self: SketchPrimPropOps[S] =>

  def countForStr[A](sketch: S[A], start: A, end: A): Double = {
    val measure = sketch.measure.asInstanceOf[Measure[A]]
    primCountForStr(sketch, measure(start), measure(end))
  }

  // implements the Sketch ops

  def count[A](sketch: S[A], start: A, end: A): Double = countForStr(sketch, start, end)

  def sum(sketch: S[_]): Count = sumForStr(sketch)

  def narrowUpdate[A](sketch: S[A], as: List[(A, Count)]): S[A] = {
    val ps = as.map { case (value, count) => (sketch.measure.asInstanceOf[Measure[A]](value), count) }

    primNarrowUpdateForStr(sketch, ps)
  }

  def deepUpdate[A](sketch: S[A], as: List[(A, Count)]): (S[A], Option[Structure]) = {
    val measure = sketch.measure.asInstanceOf[Measure[A]]
    val ps = as.map { case (value, count) => (measure.to(value), count) }

    primDeepUpdate(sketch, ps)
  }

}
