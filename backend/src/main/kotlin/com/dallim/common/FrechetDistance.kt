package com.dallim.common

/**
 * Discrete Fréchet distance (Eiter & Mannila, 1994) between two ordered point sequences, in
 * meters, using [GeoMath.haversineMeters] as the underlying point-to-point metric.
 *
 * Used for the Sketch Match score (docs/01-feature-spec.md 2.2.D step 4): unlike a simple
 * average/sum of point-to-point distances, Fréchet distance captures the "leash length" needed
 * to walk both curves from start to end without backtracking, which is a much better measure of
 * whether two tracks have the *same shape* — exactly what matters for judging whether a runner's
 * actual GPS trace still looks like the planned sketch.
 */
object FrechetDistance {

    /**
     * Iterative (non-recursive) DP so a run with thousands of raw GPS fixes can't blow the call
     * stack — the classic recursive formulation recurses to a depth of `p.size + q.size`.
     */
    fun discreteMeters(p: List<LatLng>, q: List<LatLng>): Double {
        val n = p.size
        val m = q.size
        if (n == 0 || m == 0) return Double.MAX_VALUE

        val ca = Array(n) { DoubleArray(m) }
        for (i in 0 until n) {
            for (j in 0 until m) {
                val d = GeoMath.haversineMeters(p[i], q[j])
                ca[i][j] = when {
                    i == 0 && j == 0 -> d
                    i > 0 && j == 0 -> maxOf(ca[i - 1][0], d)
                    i == 0 && j > 0 -> maxOf(ca[0][j - 1], d)
                    else -> maxOf(minOf(ca[i - 1][j], ca[i - 1][j - 1], ca[i][j - 1]), d)
                }
            }
        }
        return ca[n - 1][m - 1]
    }
}
