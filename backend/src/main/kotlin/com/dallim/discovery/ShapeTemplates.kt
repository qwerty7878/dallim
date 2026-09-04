package com.dallim.discovery

import com.dallim.common.GeoMath
import com.dallim.common.LatLng
import org.apache.batik.parser.AWTPathProducer
import org.apache.batik.parser.PathParser
import java.awt.geom.PathIterator
import java.awt.geom.Point2D

/**
 * docs/02-api-spec.md 13.1/13.3 — the shapes registered for `mode: "SHAPE"` discovery. Each is
 * authored as a single-subpath, closed SVG path (M/L/C/Q/Z per standard path grammar) rather than
 * hand-encoded coordinates, and parsed into a [ShapeTemplate] lazily on first use.
 *
 * v1 set per 13.3: HEART/CIRCLE/DROP only. `STAR` was prototyped and dropped — real-road-network
 * measurement at the 13.1.1 size tiers found its concave vertices produce out-and-back spikes that
 * grow with scale (45.8-60.0km actual distance at radius 1.0-1.5km, still an unrecognizable
 * tangle), whereas the three shapes kept here stayed legible across the same range. See 13.3 for
 * the full writeup and what re-adding a concave shape would require.
 */
enum class ShapeType(private val svgPath: String) {
    CIRCLE(
        "M100,0 C100,55.23 55.23,100 0,100 C-55.23,100 -100,55.23 -100,0 " +
            "C-100,-55.23 -55.23,-100 0,-100 C55.23,-100 100,-55.23 100,0 Z",
    ),
    HEART(
        "M0,25 C-40,-5 -70,-25 -70,-55 C-70,-80 -50,-95 -25,-95 C-10,-95 0,-85 0,-70 " +
            "C0,-85 10,-95 25,-95 C50,-95 70,-80 70,-55 C70,-25 40,-5 0,25 Z",
    ),
    DROP(
        "M0,-100 C40,-40 70,10 70,50 C70,88 39,120 0,120 C-39,120 -70,88 -70,50 " +
            "C-70,10 -40,-40 0,-100 Z",
    ),
    ;

    /** Parsed/normalized once and reused across every request for this shape (immutable). */
    val template: ShapeTemplate by lazy { ShapeTemplate.fromSvgPath(svgPath) }

    companion object {
        /**
         * docs/02-api-spec.md 13.1/13.3 — null for a missing or unrecognized `shapeType` string.
         * `"STAR"` deliberately falls through to null here too — it's not a registered enum entry
         * (13.3) — which is what makes it a 400 VALIDATION_ERROR at the request layer rather than
         * needing a separate explicit rejection list.
         */
        fun fromRequestValue(value: String?): ShapeType? = entries.find { it.name == value }
    }
}

/**
 * A shape template, resampled to [POINT_COUNT] evenly arc-length-spaced points and expressed in
 * tiny-degree units centered near (0,0) — see [fromSvgPath] for why that scale is what lets
 * [GeoMath.resample]/[GeoMath.haversineMeters] (both haversine-based, built for real geographic
 * coordinates) be reused correctly here.
 */
class ShapeTemplate private constructor(val points: List<LatLng>) {
    /**
     * docs/02-api-spec.md 13.1.1 — "정규화된 템플릿(centroid 기준, 최원점 반경 1.0)": the greatest
     * Haversine distance from the template's own centroid (origin, per [fromSvgPath]'s recentering)
     * to any of its points, in meters. `DiscoveryService.generateShapeRoute` divides `size`'s fixed
     * `templateRadiusMeters` by this to get a scale factor — equivalent to first normalizing the
     * template to max-radius 1.0 and then multiplying by the target radius, just without a separate
     * normalization pass.
     */
    val maxRadiusMeters: Double = points.maxOf { GeoMath.haversineMeters(LatLng(lat = 0.0, lng = 0.0), it) }

    companion object {
        // docs/02-api-spec.md 13.3 — "균등 아크렝스 N개 점(예: 40개)".
        const val POINT_COUNT = 40

        // Raw SVG path units -> tiny-degree units. The exact magnitude doesn't matter (every
        // request rescales by targetDistanceMeters / perimeterMeters anyway); it only needs to
        // stay small enough that GeoMath's haversine-based functions are indistinguishable from
        // planar Euclidean distance for this template's own internal geometry. ~150 raw SVG units
        // / 100_000 ~= 0.0015 degrees, comfortably inside that small-angle regime.
        private const val DEGREE_SCALE_DIVISOR = 100_000.0

        /**
         * Parses [svgPath] into a flattened, explicitly-closed polyline (see
         * [flattenToClosedPolyline]), re-centers it on its own centroid, scales it down to
         * [DEGREE_SCALE_DIVISOR]-tiny degree units, then resamples to [POINT_COUNT] points via
         * [GeoMath.resample] (13.3: "기존 GeoMath.resample로... 재사용한다").
         */
        fun fromSvgPath(svgPath: String): ShapeTemplate {
            val raw = flattenToClosedPolyline(svgPath)
            val centroidX = raw.sumOf { it.x } / raw.size
            val centroidY = raw.sumOf { it.y } / raw.size

            // SVG's y-axis points down; negate so the template reads right-side-up in lat/lng terms.
            // Purely cosmetic — a random rotation is applied per-request regardless (13.3).
            val tinyDegree = raw.map { p ->
                LatLng(
                    lat = -(p.y - centroidY) / DEGREE_SCALE_DIVISOR,
                    lng = (p.x - centroidX) / DEGREE_SCALE_DIVISOR,
                )
            }

            return ShapeTemplate(GeoMath.resample(tinyDegree, POINT_COUNT))
        }

        /**
         * Parses [svgPath] with Apache Batik's PathParser/AWTPathProducer
         * (`org.apache.xmlgraphics:batik-parser`) — handles the full standard SVG path grammar
         * (M/L/H/V/C/S/Q/T/A, relative and absolute) — into a [java.awt.Shape], then flattens
         * curves into line segments via [java.awt.Shape.getPathIterator]'s flatness overload.
         *
         * `SEG_CLOSE` doesn't carry its own coordinates (it just means "line back to the last
         * moveto"), so this explicitly appends the subpath's start point when it's encountered —
         * the resulting list is a genuinely closed ring (first point == last point), not something
         * that merely renders closed.
         *
         * docs/02-api-spec.md 13.3 — "닫힌 윤곽선(한붓그리기) 전제": any path with more than one
         * subpath (more than one `M`) is rejected here, at shape-registration time (this runs once,
         * lazily, from [ShapeType.template] — never per API request) rather than being a
         * request-time validation concern.
         */
        private fun flattenToClosedPolyline(svgPath: String): List<Point2D.Double> {
            val producer = AWTPathProducer()
            val parser = PathParser()
            parser.pathHandler = producer
            parser.parse(svgPath)

            val points = mutableListOf<Point2D.Double>()
            var subpathCount = 0
            var subpathStart: Point2D.Double? = null
            val coords = DoubleArray(6)

            // flatness=0.5 (raw SVG units) is far finer than the eventual 40-point resample needs.
            val iterator = producer.shape.getPathIterator(null, 0.5)
            while (!iterator.isDone) {
                when (iterator.currentSegment(coords)) {
                    PathIterator.SEG_MOVETO -> {
                        subpathCount++
                        val point = Point2D.Double(coords[0], coords[1])
                        subpathStart = point
                        points += point
                    }
                    PathIterator.SEG_LINETO -> points += Point2D.Double(coords[0], coords[1])
                    PathIterator.SEG_CLOSE -> subpathStart?.let { points += it }
                    // SEG_QUADTO/SEG_CUBICTO never surface from a flatness-based iterator — they're
                    // already broken down into SEG_LINETO segments.
                }
                iterator.next()
            }

            check(subpathCount == 1) {
                "Shape template must be a single closed subpath (one-stroke outline), found $subpathCount"
            }
            check(points.size >= 3) { "Shape template must resolve to at least 3 points" }
            return points
        }
    }
}
