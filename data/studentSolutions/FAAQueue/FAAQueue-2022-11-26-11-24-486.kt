//package mpp.faaqueue

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference

class FAAQueue<E> {
    private val head: AtomicReference<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicReference<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment<E>(0L)
        head = AtomicReference(firstNode)
        tail = AtomicReference(firstNode)
    }

    private fun findSegment(segmentRef: AtomicReference<Segment<E>>, localIndex: Long): AtomicReference<Segment<E>> {
        var resultSegmentRef = segmentRef
        while (resultSegmentRef.get().id < localIndex) {
            val currentSegment = resultSegmentRef.get()
            if (currentSegment.next.get() != null) {
                resultSegmentRef = AtomicReference(currentSegment.next.get()!!)
                continue
            }
            val nextSegmentRef = currentSegment.next
            val newSegmentRef = Segment<E>(currentSegment.id + 1)
            if (nextSegmentRef.compareAndSet(null, newSegmentRef)) {
                resultSegmentRef = AtomicReference(newSegmentRef)
            }
        }
        return resultSegmentRef
    }

    private fun moveSegmentRefForward(segmentRefToMove: AtomicReference<Segment<E>>, segment: Segment<E>) {
        while (true) {
            val result = segmentRefToMove.get()
            if (result.id >= segment.id || segmentRefToMove.compareAndSet(result, segment)) {
                break
            }
        }
    }

    /**
     * Adds the specified element [element] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail
            val index = enqIdx.getAndIncrement()
            val segment = findSegment(curTail, index / SEGMENT_SIZE)

            moveSegmentRefForward(curTail, segment.get())
            if (segment.get().cas((index % SEGMENT_SIZE).toInt(), Just<E>(null), Just(element)))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value)
                return null
            val curHead = head
            val index = deqIdx.getAndIncrement()
            val segment = findSegment(curHead, index / SEGMENT_SIZE)
            moveSegmentRefForward(head, segment.get())
            if (segment.get().cas((index % SEGMENT_SIZE).toInt(), Just(null), None))
                continue
            val cellValue = segment.get().get((index % SEGMENT_SIZE).toInt()) as Just<E>
            return cellValue.value
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }
}

sealed class Maybe
class Just<E>(val value: E?) : Maybe()
object None : Maybe()


private class Segment<T>(val id: Long) {
    var next: AtomicReference<Segment<T>?> = AtomicReference(null)
    val elements = Array<AtomicReference<Maybe>>(SEGMENT_SIZE) {
        AtomicReference(Just<T>(null))
    }

    fun get(i: Int): Maybe = elements[i].get()
    fun cas(i: Int, expect: Maybe, update: Maybe) = elements[i].compareAndSet(expect, update)

}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

