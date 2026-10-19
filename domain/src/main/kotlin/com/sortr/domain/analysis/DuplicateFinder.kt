package com.sortr.domain.analysis

class HashedPhoto(val id: Long, val hash: Long)

/** What decides which copy in a group of duplicates is worth keeping. */
data class DuplicateCandidate(
    val id: Long,
    val pixels: Long,
    val sharpness: Double?,
    val sizeBytes: Long,
    val dateTaken: Long,
)

/**
 * Groups photos whose hashes are at most [maxDistance] bits apart. A BK-tree finds each photo's
 * neighbours without comparing every pair, and union-find joins chains of neighbours into groups.
 */
object DuplicateFinder {

    const val DEFAULT_MAX_DISTANCE = 6

    /** Groups of two or more ids, each in input order; groups ordered by their first member. */
    fun groups(photos: List<HashedPhoto>, maxDistance: Int = DEFAULT_MAX_DISTANCE): List<List<Long>> {
        if (photos.size < 2) return emptyList()
        val tree = BkTree()
        photos.forEachIndexed { index, photo -> tree.add(photo.hash, index) }
        val sets = UnionFind(photos.size)
        photos.forEachIndexed { index, photo ->
            tree.search(photo.hash, maxDistance) { other -> if (other != index) sets.union(index, other) }
        }
        return photos.indices
            .groupBy { sets.find(it) }
            .values
            .filter { it.size >= 2 }
            .sortedBy { it.first() }
            .map { members -> members.map { photos[it].id } }
    }

    /**
     * The copy to keep: most pixels, then sharpest, then largest file, then the oldest
     * (usually the original rather than a re-saved copy).
     */
    fun pickBest(candidates: List<DuplicateCandidate>): Long {
        require(candidates.isNotEmpty()) { "No candidates" }
        return candidates.maxWith(
            compareBy<DuplicateCandidate> { it.pixels }
                .thenBy { it.sharpness ?: 0.0 }
                .thenBy { it.sizeBytes }
                .thenByDescending { it.dateTaken },
        ).id
    }

    private class BkTree {
        private class Node(val hash: Long) {
            val members = ArrayList<Int>(1)
            val children = HashMap<Int, Node>()
        }

        private var root: Node? = null

        fun add(hash: Long, index: Int) {
            var node = root
            if (node == null) {
                root = Node(hash).also { it.members += index }
                return
            }
            while (true) {
                val distance = PerceptualHash.distance(hash, node!!.hash)
                if (distance == 0) {
                    node.members += index
                    return
                }
                val child = node.children[distance]
                if (child == null) {
                    node.children[distance] = Node(hash).also { it.members += index }
                    return
                }
                node = child
            }
        }

        fun search(hash: Long, radius: Int, onMatch: (Int) -> Unit) {
            val pending = ArrayDeque<Node>()
            root?.let(pending::addLast)
            while (pending.isNotEmpty()) {
                val node = pending.removeLast()
                val distance = PerceptualHash.distance(hash, node.hash)
                if (distance <= radius) node.members.forEach(onMatch)
                // Only branches within the radius can hold matches (triangle inequality).
                for ((edge, child) in node.children) {
                    if (edge in (distance - radius)..(distance + radius)) pending.addLast(child)
                }
            }
        }
    }

    private class UnionFind(size: Int) {
        private val parent = IntArray(size) { it }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var node = x
            while (parent[node] != root) {
                val next = parent[node]
                parent[node] = root
                node = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb)
        }
    }
}
