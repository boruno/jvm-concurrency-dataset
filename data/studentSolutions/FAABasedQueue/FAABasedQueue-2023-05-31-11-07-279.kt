//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueue<E> : Queue<E> {
    private val infiniteArray = LinkedListSegment<E>()
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            if (infiniteArray.set(i, element)) {
                return
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            val i = deqIdx.getAndIncrement()
            val value = infiniteArray.get(i)
            if (value != null) {
                infiniteArray.set(i, null)
                return value
            }
        }
    }
}

private class LinkedListSegment<E> {
    companion object {
        private const val SEGMENT_SIZE = 1024
    }

    private val segment: Array<Any?> = arrayOfNulls(SEGMENT_SIZE)

    fun set(index: Int, value: Any?): Boolean {
        val segmentIndex = index / SEGMENT_SIZE
        val elementIndex = index % SEGMENT_SIZE
        val currentSegment = getOrCreateSegment(segmentIndex)
        currentSegment[elementIndex] = value
        return true
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E? {
        val segmentIndex = index / SEGMENT_SIZE
        val elementIndex = index % SEGMENT_SIZE
        val currentSegment = getSegment(segmentIndex)
        return currentSegment?.get(elementIndex) as E?
    }

    private fun getOrCreateSegment(index: Int): Array<Any?> {
        var currentSegment = segment
        var currentIndex = 0
        while (currentIndex < index) {
            var nextSegment = currentSegment[SEGMENT_SIZE - 1]
            if (nextSegment == null) {
                nextSegment = arrayOfNulls<Any?>(SEGMENT_SIZE)
                currentSegment[SEGMENT_SIZE - 1] = nextSegment
            }
            currentSegment = nextSegment as Array<Any?>
            currentIndex++
        }
        return currentSegment
    }

    private fun getSegment(index: Int): Array<Any?>? {
        var currentSegment = segment
        var currentIndex = 0
        while (currentIndex < index) {
            currentSegment = currentSegment[SEGMENT_SIZE - 1] as? Array<Any?> ?: return null
            currentIndex++
        }
        return currentSegment
    }
}

private val POISONED = Any()
