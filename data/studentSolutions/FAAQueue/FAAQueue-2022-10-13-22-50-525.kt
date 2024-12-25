//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment<E>(0L)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private fun findSegment(segment: Segment<E>, localIndex: Long): Segment<E> {
        var result = segment
        while (result.id < localIndex && result.next != null) {
            result = result.next!!
        }
        while (result.id < localIndex) {
            result.next = Segment(result.id + 1)
            result = result.next!!
        }
        return result
    }

    private fun moveBoundSegmentForward(boundSegment: Segment<E>, segment: Segment<E>) {
        var result = boundSegment
        while (true) {
            if (result.id >= segment.id || head.compareAndSet(result, segment)) {
                break
            }
            result = head.value
        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
//        println("enqueue: $element")
        while (true) {
            val curTail = tail.value
            val index = enqIdx.getAndIncrement()
            val segment = findSegment(curTail, index / SEGMENT_SIZE)
//            println("try enqueue ${curTail.id} - $index - ${segment.id}")
            moveBoundSegmentForward(curTail, segment)
            if (segment.cas((index % SEGMENT_SIZE).toInt(), null, Maybe(element)))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
//        println("dequeue")
        while (true) {
            if (deqIdx.value >= enqIdx.value)
                return null
            val curHead = head.value
            val index = deqIdx.getAndIncrement()
            val segment = findSegment(curHead, index / SEGMENT_SIZE)
//            println("try dequeue ${curHead.id} - $index - ${segment.id}")
            moveBoundSegmentForward(curHead, segment)
            if (segment.cas((index % SEGMENT_SIZE).toInt(), null, Maybe.none()))
                continue
//            println(segment.get((index % SEGMENT_SIZE).toInt())?.getValueIfHas())
            return segment.get((index % SEGMENT_SIZE).toInt())?.getValueIfHas()
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

private data class Maybe<T>(
    private val element: T? = null
) {
    val hasValue: Boolean = element != null

    fun getValueIfHas(): T = element!!

    companion object {
        fun <T> none() = Maybe<T>()
    }
}

private class Segment<T>(val id: Long) {
    var next: Segment<T>? = null
    val elements = atomicArrayOfNulls<Maybe<T>?>(SEGMENT_SIZE)

    fun get(i: Int): Maybe<T>? = elements[i].value
    fun cas(i: Int, expect: Maybe<T>?, update: Maybe<T>?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Maybe<T>?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

