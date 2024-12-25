//package mpp.faaqueue

import kotlinx.atomicfu.*
import kotlin.math.ceil
class FAAQueue<E> {
    private val head: AtomicRef<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment<E>(1)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }


    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            var curTail = tail.value
            val curEnqIdx = enqIdx.getAndIncrement()
            while (curTail.id * SEGMENT_SIZE <= curEnqIdx) {
                val nextTail = curTail.next.value
                if (nextTail != null) {
                    curTail = nextTail
                    continue
                }
                val newTail = Segment<E>(curTail.id + 1)
                if (curTail.next.compareAndSet(null, newTail)) {
                    tail.compareAndSet(curTail, newTail)
                }
            }
            if (curTail.cas((curEnqIdx % SEGMENT_SIZE).toInt(),
                    null, Just(element))) break
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            var curHead = head.value
            val curDeqIdx = deqIdx.getAndIncrement()
            while (curHead.id * SEGMENT_SIZE <= curDeqIdx) {
                curHead = curHead.next.value ?: return null
            }
            val i = (curDeqIdx % SEGMENT_SIZE).toInt()
            val elem = curHead.gas(i)
            if (elem is Just<*>) return (elem as Just<E?>).value

            /*if (curHead.cas(i, null, null)) {
                continue
            }
            if (i == SEGMENT_SIZE - 1 && curHead.next.value != null) {
                val newHead = curHead.next.value!!
                if (head.compareAndSet(curHead, newHead)) {
                    return curHead.get(i)
                } else {
                    deqIdx.decrementAndGet()
                }
            }
            return curHead.get(i)*/
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

private class Segment<E>(val id: Int) {
    val next: AtomicRef<Segment<E>?> = atomic(null)
    val elements = atomicArrayOfNulls<Maybe<E>?>(SEGMENT_SIZE)

    constructor(x: Maybe<E>?, id: Int) : this(id) {
        put(0, x)
    }

    fun gas(i: Int) = elements[i].getAndSet(Nothing())
    fun cas(i: Int, expect: Maybe<E>?, update: Maybe<E>?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Maybe<E>?) {
        elements[i].value = value
    }
}

interface Maybe<E>;
private class Nothing<E>: Maybe<E>
private class Just<E>(val value: E): Maybe<E>

const val SEGMENT_SIZE: Int = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

