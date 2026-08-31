package com.dallim.common

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Ramer-Douglas-Peucker polyline simplification, used to shrink a run's raw GPS track (hundreds
 * to thousands of points) down before persisting it as `RunRecord.actual_path`
 * (docs/01-feature-spec.md 2.4: "Run 완료 후 압축... 포인트 수 축소하여 저장"). The raw points
 * themselves stay in `gps_points` — this only affects the simplified copy used for storage/
 * thumbnails, never the judgement inputs (Haversine/Coverage/Fréchet run against the raw points).
 */
object DouglasPeucker {

    /**
     * Simplifies [points] to the smallest subsequence (always keeping the first and last point)
     * such that no dropped point deviates from its simplified segment by more than
     * [epsilonMeters]. Iterative (explicit stack) rather than recursive to avoid stack depth
     * issues on long raw tracks.
     */
    fun simplify(points: List<LatLng>, epsilonMeters: Double): List<LatLng> {
        if (points.size < 3) return points

        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true

        val stack = ArrayDeque<IntRange>()
        stack.addLast(0..points.size - 1)

        while (stack.isNotEmpty()) {
            val range = stack.removeLast()
            val start = range.first
            val end = range.last
            if (end <= start + 1) continue

            var maxDist = -1.0
            var maxIndex = -1
            for (i in start + 1 until end) {
                val d = perpendicularDistanceMeters(points[i], points[start], points[end])
                if (d > maxDist) {
                    maxDist = d
                    maxIndex = i
                }
            }

            if (maxDist > epsilonMeters) {
                keep[maxIndex] = true
                stack.addLast(start..maxIndex)
                stack.addLast(maxIndex..end)
            }
        }

        return points.filterIndexed { i, _ -> keep[i] }
    }

    private data class Point2D(val x: Double, val y: Double)

    /**
     * Perpendicular distance from [point] to the segment [a]-[b], in meters, via an
     * equirectangular planar projection local to [a]. Adequate at running-route scale (the
     * curvature error over a few hundred meters is far smaller than [epsilonMeters] tolerances
     * this is used with).
     */
    private fun perpendicularDistanceMeters(point: LatLng, a: LatLng, b: LatLng): Double {
        val metersPerDegLat = 111_320.0
        val metersPerDegLng = 111_320.0 * cos(Math.toRadians(a.lat))

        fun project(p: LatLng) = Point2D(
            x = (p.lng - a.lng) * metersPerDegLng,
            y = (p.lat - a.lat) * metersPerDegLat,
        )

        val pa = project(a)
        val pb = project(b)
        val pp = project(point)

        val dx = pb.x - pa.x
        val dy = pb.y - pa.y
        if (dx == 0.0 && dy == 0.0) return GeoMath.haversineMeters(point, a)

        val t = (((pp.x - pa.x) * dx) + ((pp.y - pa.y) * dy)) / (dx * dx + dy * dy)
        val tc = t.coerceIn(0.0, 1.0)
        val projX = pa.x + tc * dx
        val projY = pa.y + tc * dy
        val ddx = pp.x - projX
        val ddy = pp.y - projY
        return sqrt(ddx * ddx + ddy * ddy)
    }
}
