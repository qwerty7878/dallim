package com.dallim.discovery

/**
 * AI Sketch Discovery / draw-to-route conversion — MVP2 scope, explicitly out of MVP1
 * (docs/02-api-spec.md 8장, docs/01-feature-spec.md 2.1 module list).
 *
 * Left as a stub package on purpose: do not implement `POST /routes/discovery` or
 * `POST /routes/draw-convert` here during MVP1. Nothing in this module is wired into
 * Routing.kt yet.
 *
 * Safety criterion for whenever this gets built (docs/01-feature-spec.md 2.2절 C, added
 * 2026-09-03): generated routes must avoid narrow alleys / roads without a sidewalk / low-visibility
 * segments, and should prefer sidewalks, parks, and riverside paths separated from car traffic —
 * i.e. this should become a filter over the candidate road-network data, not just a shortest/prettiest
 * path search.
 */
object DiscoveryModuleStub
