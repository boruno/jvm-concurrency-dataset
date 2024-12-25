//package mpp.faaqueue

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
        while (true) {
            var curTail = tail.value
            val i = enqIdx.getAndAdd(1)
            val id = i / SEGMENT_SIZE
            if (id > curTail.id) {
                val newSegment = Segment()
                newSegment.id = id
                curTail.next = newSegment
                if (tail.compareAndSet(curTail, newSegment)) {
                    curTail = newSegment
                } else {
                    continue
                }
            }

            if(tail.value.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                println("enqueue $element")
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) {
                println("dequeue null like empty")
                return null
            }
            var curHead = head.value

            val i = deqIdx.getAndAdd(1)
            val id = i / SEGMENT_SIZE
            if (id > curHead.id) {
                val newSegment = curHead.next ?: continue
                if (head.compareAndSet(curHead, newSegment)) {
                    curHead = newSegment
                } else {
                    continue
                }
            }

            if(head.value.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, "-")) {
                continue
            } else {
                val result = head.value.elements[(i % SEGMENT_SIZE).toInt()].value as E?
                println("dequeue $result in normal")
                return result
            }
        }


        // TODO("implement me")
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }
}

private class Segment {
    var next: Segment? = null
    var id: Long = 0L
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

