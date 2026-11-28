package com.hikmahlab.chargemaster.domain.engine

import com.hikmahlab.chargemaster.domain.model.BatteryHealthSnapshot
import com.hikmahlab.chargemaster.domain.model.BatteryReading
import com.hikmahlab.chargemaster.domain.model.Confidence
import kotlin.math.abs

/**
 * Estimates full-charge capacity from charge-counter deltas over a single charge:
 *   capacity ≈ ΔQ / ΔLevel × 100
 * Requires a large level span; detects fuel gauges whose counter is merely level × constant
 * (which would always "prove" 100 % health).
 */
object CapacityEstimator {
    const val MIN_SPAN_PERCENT = 30
    private const val MEDIUM_SPAN = 40
    private const val HIGH_SPAN = 60
    private const val MIN_PLAUSIBLE_MAH = 500f
    private const val MAX_PLAUSIBLE_MAH = 20_000f

    data class Estimate(
        val capacityMah: Float?,
        val confidence: Confidence,
        val levelSpan: Int,
        /** Counter looked like a function of level only — not an independent measurement. */
        val counterDerivedFromLevel: Boolean,
    )

    fun estimateFromReadings(readings: List<BatteryReading>): Estimate {
        val points = readings
            .filter { it.level != null && it.chargeCounterMah != null && it.level > 0 }
            .sortedBy { it.timestamp }
        if (points.size < 2) return Estimate(null, Confidence.INSUFFICIENT, 0, false)
        val first = points.first()
        val last = points.last()
        val span = last.level!! - first.level!!
        val dq = last.chargeCounterMah!! - first.chargeCounterMah!!
        if (span < MIN_SPAN_PERCENT || dq <= 0f) return Estimate(null, Confidence.INSUFFICIENT, span.coerceAtLeast(0), false)

        val capacity = dq / span * 100f
        if (capacity !in MIN_PLAUSIBLE_MAH..MAX_PLAUSIBLE_MAH) {
            return Estimate(null, Confidence.INSUFFICIENT, span, false)
        }
        val derived = looksDerivedFromLevel(points)
        val confidence = when {
            derived -> Confidence.LOW
            span >= HIGH_SPAN && points.size >= 6 -> Confidence.HIGH
            span >= MEDIUM_SPAN -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        return Estimate(capacity, confidence, span, derived)
    }

    /** True when counter/level ratio is constant within 0.5 % across all points. */
    internal fun looksDerivedFromLevel(points: List<BatteryReading>): Boolean {
        if (points.size < 3) return false
        val ratios = points.map { it.chargeCounterMah!! / it.level!! }
        val mean = ratios.average().toFloat()
        return ratios.all { abs(it - mean) / mean < 0.005f }
    }

    /** Combines per-session estimates (newest last); weights by confidence. */
    fun combine(snapshots: List<BatteryHealthSnapshot>, maxSamples: Int = 10): Pair<Float?, Confidence> {
        val usable = snapshots
            .filter { it.estimatedCapacityMah != null && it.confidence >= Confidence.MEDIUM }
            .sortedBy { it.timestamp }
            .takeLast(maxSamples)
        if (usable.isEmpty()) {
            val low = snapshots.filter { it.estimatedCapacityMah != null }
            return if (low.isEmpty()) null to Confidence.INSUFFICIENT
            else low.last().estimatedCapacityMah to Confidence.LOW
        }
        val weighted = usable.flatMap { s ->
            List(if (s.confidence == Confidence.HIGH) 2 else 1) { s.estimatedCapacityMah!! }
        }.sorted()
        val median = weighted[weighted.size / 2]
        val confidence = when {
            usable.size >= 3 && usable.count { it.confidence == Confidence.HIGH } >= 2 -> Confidence.HIGH
            usable.size >= 2 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        return median to confidence
    }

    /** State of Health %, only with a user-supplied design capacity and at least medium confidence. */
    fun healthPercent(estimatedMah: Float?, confidence: Confidence, designMah: Int?): Float? {
        if (estimatedMah == null || designMah == null || designMah <= 0) return null
        if (confidence < Confidence.MEDIUM) return null
        return estimatedMah / designMah * 100f
    }

    /**
     * Wear trend in percentage points of design capacity per 30 days (negative = losing capacity).
     * Requires ≥3 usable snapshots spanning ≥14 days.
     */
    fun wearTrendPerMonth(snapshots: List<BatteryHealthSnapshot>, designMah: Int?): Float? {
        val pts = snapshots.filter { it.estimatedCapacityMah != null && it.confidence >= Confidence.MEDIUM }
            .sortedBy { it.timestamp }
        if (pts.size < 3 || designMah == null || designMah <= 0) return null
        val spanDays = (pts.last().timestamp - pts.first().timestamp) / 86_400_000.0
        if (spanDays < 14) return null
        val xs = pts.map { (it.timestamp - pts.first().timestamp) / 86_400_000.0 }
        val ys = pts.map { it.estimatedCapacityMah!! / designMah * 100.0 }
        val mx = xs.average(); val my = ys.average()
        val num = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den == 0.0) return null
        return (num / den * 30).toFloat()
    }
}
