package mpp.faaqueue

import kotlinx.atomicfu.*

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

        val newNode = Segment() // ToDo:: check this shit because null is sad
        while (true) {
            val curTail = tail.value
            val nextTail = curTail.next

            val i = enqIdx.getAndIncrement().toInt()

            if (enqIdx.value.toInt() >= SEGMENT_SIZE) {
                if (nextTail!!.elements[i].compareAndSet(null, newNode)) {
                    tail.compareAndSet(curTail, newNode)
                    return
                }
            }
            else if (tail.value.elements[enqIdx.value.toInt()].compareAndSet(null, element))
            {
                return
            }

            /*val s = findSame(val start = curTail, id = i / SEGMENT_SIZE) ToDo::
            moveTailForward(s) ToDo::
            if (s.compareAndSet(arr.[i % SEGMENT_SIZE], null, element))
                return*/
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            if (isEmpty) {
                val nextHead = curHead.next
                if (nextHead == null) {
                    return null
                }
                else {
                    head.compareAndSet(curHead, nextHead)
                }
            }
            else {
               val deqIndex = deqIdx.getAndIncrement()
                if (deqIndex >= SEGMENT_SIZE)
                    continue
                val res = curHead.elements[deqIndex.toInt()].getAndSet("L") ?: continue

                return res as E?
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return (deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE)
        }
}

private class Segment {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

