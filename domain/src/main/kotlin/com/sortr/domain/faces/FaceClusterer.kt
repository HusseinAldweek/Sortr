package com.sortr.domain.faces

/**
 * A person's running average embedding. [mean] is the plain average of the member embeddings
 * (not re-normalised), so it can be updated exactly as faces join or leave.
 */
class Centroid(
    val personId: Long,
    mean: FloatArray,
    count: Int,
) {
    var mean: FloatArray = mean.copyOf()
        private set
    var count: Int = count
        private set

    fun add(embedding: FloatArray) {
        val n = count + 1
        for (i in mean.indices) mean[i] += (embedding[i] - mean[i]) / n
        count = n
    }

    /** Takes a member back out. Returns false when nothing is left. */
    fun remove(embedding: FloatArray): Boolean {
        if (count <= 1) {
            count = 0
            return false
        }
        val n = count - 1
        for (i in mean.indices) mean[i] = (mean[i] * count - embedding[i]) / n
        count = n
        return true
    }

    fun absorb(other: Centroid) {
        val n = count + other.count
        if (n == 0) return
        for (i in mean.indices) mean[i] = (mean[i] * count + other.mean[i] * other.count) / n
        count = n
    }

    companion object {
        fun of(personId: Long, embeddings: List<FloatArray>): Centroid {
            require(embeddings.isNotEmpty()) { "A centroid needs at least one face" }
            val centroid = Centroid(personId, embeddings.first(), 1)
            embeddings.drop(1).forEach(centroid::add)
            return centroid
        }
    }
}

class FaceSample(
    val embedding: FloatArray,
    val quality: FaceQuality,
    /** The user said this face isn't this person, so it's never matched to them again. */
    val rejectedPersonId: Long? = null,
)

sealed interface Placement {
    val similarity: Float

    data class Existing(val personId: Long, override val similarity: Float) : Placement

    /** Starts a new person. [personId] is the temporary id given by [FaceClusterer.placeAll]. */
    data class NewPerson(val personId: Long, override val similarity: Float) : Placement

    /** Not confident enough: goes to "Unsorted faces". */
    data class Unsorted(override val similarity: Float) : Placement
}

/**
 * Incremental grouping by cosine similarity. Each face is compared with every person's average:
 * at or above [threshold] it joins the closest person and that average is updated; a little
 * below (within [unsureMargin]) it's left unsorted rather than guessed; clearly below, a good
 * face starts a new person. Weak faces never start a new person, to avoid clutter.
 */
class FaceClusterer(
    val threshold: Float,
    val unsureMargin: Float = DEFAULT_UNSURE_MARGIN,
) {
    init {
        require(threshold in -1f..1f) { "Threshold must be a cosine similarity" }
    }

    /** Decides where one face goes, without changing anything. */
    fun place(sample: FaceSample, centroids: Collection<Centroid>): Placement {
        var best: Centroid? = null
        var bestSimilarity = -1f
        for (centroid in centroids) {
            if (centroid.personId == sample.rejectedPersonId || centroid.count == 0) continue
            val similarity = FaceMath.cosine(sample.embedding, centroid.mean)
            if (similarity > bestSimilarity) {
                best = centroid
                bestSimilarity = similarity
            }
        }
        return when {
            best != null && bestSimilarity >= threshold -> Placement.Existing(best.personId, bestSimilarity)
            sample.quality == FaceQuality.WEAK -> Placement.Unsorted(bestSimilarity)
            best != null && bestSimilarity >= threshold - unsureMargin -> Placement.Unsorted(bestSimilarity)
            else -> Placement.NewPerson(personId = 0, similarity = bestSimilarity)
        }
    }

    /**
     * Places [samples] in order, updating [centroids] as it goes. New people get negative
     * temporary ids (−1, −2, …) and are added to [centroids]. Good faces are placed first, so
     * people are seeded from the clearest pictures.
     */
    fun placeAll(samples: List<FaceSample>, centroids: MutableList<Centroid>): List<Placement> {
        var nextTempId = -1L
        val placements = arrayOfNulls<Placement>(samples.size)
        val order = samples.indices.sortedBy { if (samples[it].quality == FaceQuality.GOOD) 0 else 1 }
        for (index in order) {
            val sample = samples[index]
            val placement = when (val decision = place(sample, centroids)) {
                is Placement.Existing -> {
                    centroids.first { it.personId == decision.personId }.add(sample.embedding)
                    decision
                }
                is Placement.NewPerson -> {
                    val id = nextTempId--
                    centroids += Centroid(id, sample.embedding, 1)
                    Placement.NewPerson(id, decision.similarity)
                }
                is Placement.Unsorted -> decision
            }
            placements[index] = placement
        }
        return placements.map { it!! }
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.6f
        const val DEFAULT_UNSURE_MARGIN = 0.08f
        const val MIN_THRESHOLD = 0.4f
        const val MAX_THRESHOLD = 0.85f
    }
}
