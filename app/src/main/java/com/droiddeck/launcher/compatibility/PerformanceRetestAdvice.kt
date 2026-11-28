package com.droiddeck.launcher.compatibility

/** Read-only retest candidates, not an estimate of the best configuration. */
internal object PerformanceRetestAdvice {
    data class Observation(val id: Long, val sample: OptimizationComparison.Sample, val runtime: String)
    data class Candidate(val baseline: Observation, val observed: Observation,
        val comparison: OptimizationComparison.Result) {
        fun chronologicalIds(): Pair<Long, Long> = minOf(baseline.id, observed.id) to maxOf(baseline.id, observed.id)
    }

    fun candidates(observations: List<Observation>, baselineId: Long): List<Candidate> {
        require(observations.size <= 20 && observations.map { it.id }.distinct().size == observations.size)
        val baseline = observations.singleOrNull { it.id == baselineId } ?: return emptyList()
        if (baseline.id <= 0 || baseline.runtime.isBlank()) return emptyList()
        return observations.asSequence()
            .filter { it.id != baseline.id && it.id > 0 && it.runtime.isNotBlank() }
            .map { Candidate(baseline, it, OptimizationComparison.compare(baseline.sample, it.sample)) }
            .filter { it.comparison.verdict == OptimizationComparison.Verdict.IMPROVED &&
                // Do not recommend a record if either metric worsened, even below the verdict threshold.
                it.observed.sample.fps >= baseline.sample.fps &&
                it.observed.sample.p95Ms <= baseline.sample.p95Ms }
            // Most recent qualifying observation first, not a claim of global optimality.
            .sortedByDescending { it.observed.id }
            .take(3).toList()
    }
}
