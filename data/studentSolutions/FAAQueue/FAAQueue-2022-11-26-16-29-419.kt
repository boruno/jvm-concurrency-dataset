//package mpp.faaqueue

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class FAAQueue<E> {
    private val head: AtomicReference<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicReference<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = AtomicLong(0L)
    private val deqIdx = AtomicLong(0L)

    init {
        val firstNode = Segment<E>(0L)
        head = AtomicReference(firstNode)
        tail = AtomicReference(firstNode)
    }

    private fun findSegment(segmentRef: AtomicReference<Segment<E>>, localIndex: Long): Segment<E> {
        var resultSegmentRef = segmentRef.get()
        while (resultSegmentRef.id < localIndex) {
            val currentSegment = resultSegmentRef
            val nextSegment = currentSegment.next.get()
            if (nextSegment != null) {
                resultSegmentRef = nextSegment
                continue
            }
            val nextSegmentRef = currentSegment.next
            val newSegmentRef = Segment<E>(currentSegment.id + 1)
            if (nextSegmentRef.compareAndSet(null, newSegmentRef)) {
                resultSegmentRef = newSegmentRef
            }
        }
        return resultSegmentRef
    }

    private fun moveSegmentRefForward(segmentRefToMove: AtomicReference<Segment<E>>, segment: Segment<E>) {
        while (true) {
            val result = segmentRefToMove.get()
            if (result.id == segment.id || segmentRefToMove.compareAndSet(result, segment)) {
                break
            }
        }
    }

    /**
     * Adds the specified element [element] to the queue.
     */
    fun enqueue(element: E) {
//        println("Enqueue $element")
        while (true) {
            val curTail = tail
            val index = enqIdx.getAndIncrement()
            val segment = findSegment(curTail, index / SEGMENT_SIZE)

            moveSegmentRefForward(curTail, segment)
//            println((segment.get().get((index % SEGMENT_SIZE).toInt()) as Just<E>).value)
            if (segment.cas((index % SEGMENT_SIZE).toInt(), Null, Just(element)))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.get() >= enqIdx.get())
                return null
            val curHead = head
            val index = deqIdx.getAndIncrement()
            val segment = findSegment(curHead, index / SEGMENT_SIZE)
            moveSegmentRefForward(head, segment)
            if (segment.cas((index % SEGMENT_SIZE).toInt(), Null, None))
                continue
            val cellValue = segment.get((index % SEGMENT_SIZE).toInt())
            if (cellValue is Just<*>) {
                return (cellValue as Just<E>).value
            } else {
                println(cellValue)
                continue
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.get() >= enqIdx.get()
        }
}

sealed class Maybe
data class Just<E>(val value: E) : Maybe()
object Null : Maybe()
object None : Maybe()


private class Segment<T>(val id: Long) {
    var next: AtomicReference<Segment<T>?> = AtomicReference(null)
    val elements = Array<AtomicReference<Maybe>>(SEGMENT_SIZE) {
        AtomicReference(Null)
    }

    fun get(i: Int): Maybe = elements[i].get()
    fun cas(i: Int, expect: Maybe, update: Maybe) = elements[i].compareAndSet(expect, update)
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

