//package mpp.faaqueue

import kotlinx.atomicfu.*
import java.text.BreakIterator.DONE


class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
//        while (true) {
            val tailVal = tail.value
            val tailNextVal = tailVal.next
            if (tailNextVal != null) {
                tail.compareAndSet(tailVal, tailNextVal)
                return 
            }
            val enqIdx = enqIdx.getAndIncrement()
            if (enqIdx >= SEGMENT_SIZE) {
                val s = Segment()
                s.put(0, element)
                if (tail.value.next?.cas(0, null, element) == true) {
                    return
                }
            } else {
                if (tailVal.elements[enqIdx.toInt()].compareAndSet(null, element)) {
                    return
                }
            }
//        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
//        while (true) {
            if (enqIdx.value >= deqIdx.value) {
                return null
            }
            val headVal = head.value
            val deqIdx = deqIdx.getAndIncrement()
            if (deqIdx >= SEGMENT_SIZE) {
                val headNext = headVal.next ?: return null
                if (head.compareAndSet(headVal, headNext)) {
                    return null
                }
            }
            val res = headVal.elements[deqIdx.toInt()].getAndSet(DONE)
            return res as E?
//        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val e = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE
                return if (e) {
                    if (head.value.next != null) {
                        head.value = head.value.next!!
                        continue
                    }
                    true
                } else {
                    false
                }
            }
        }
}

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

