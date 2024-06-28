package mpp.faaqueue

import kotlinx.atomicfu.*
import kotlin.math.min

class FAAQueue<E : Any> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
//    private val enqIdx = atomic(0L)
//    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }


    /**
     * Adds the specified element [element] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val enqueueIndex = curTail.enqueueIndex.getAndIncrement()

            // overflowing the segment. need to create a new one
            if (enqueueIndex >= SEGMENT_SIZE) {
                val newSeg = Segment(element)
                if (curTail.next.compareAndSet(null, newSeg)) {
                    tail.compareAndSet(curTail, curTail.next.value!!)
                    return
                } else {
                    continue // not needed??
                }
            } else {
                if (curTail.elements[enqueueIndex].compareAndSet(null, element)) {
                    return
                }
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val dequeueIndex = curHead.dequeueIndex.getAndIncrement()

            if (dequeueIndex >= SEGMENT_SIZE) {
                val curNext = curHead.next.value ?: return null
                head.compareAndSet(curHead, curNext)
                continue
            }
            else {
                val value = curHead.elements[dequeueIndex].getAndSet(bot) ?: continue
                return value as E
            }

        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val curHead = head.value
                return if (curHead.isEmpty()) {
                    val next = curHead.next.value
                    if (next == null) {
                        true
                    } else {
                        head.compareAndSet(curHead, next)
                        continue
                    }
                } else {
                    false
                }
            }
        }
}

private val bot = Any()

private class Segment { // I treat a segment like a queue of fixed size
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    val enqueueIndex = atomic(0)
    val dequeueIndex = atomic(0)

//    private fun get(i: Int) = elements[i].value
//    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
//    private fun put(i: Int, value: Any?) {
//        elements[i].value = value
//    }

    constructor()
    constructor(x : Any) {
        enqueueIndex.getAndIncrement()
        elements[0].getAndSet(x)
    }

    fun isEmpty () : Boolean {
        return dequeueIndex.value >= min(enqueueIndex.value, SEGMENT_SIZE)
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

