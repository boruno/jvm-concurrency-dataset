package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val enqProcessingIdx = atomic(0L)
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
        println("try enqueue $element")
        while (true) {
            var curTail = tail.value
            val i = enqIdx.getAndAdd(1)
            if (i < deqIdx.value) {
                continue
            }
            val id = i / SEGMENT_SIZE
            if (id > curTail.id) {
                if (curTail.next == null) {
                    val newSegment = Segment()
                    newSegment.id = id
                    curTail.next = newSegment
                    if (!tail.compareAndSet(curTail, newSegment)) {
                        continue
                    }
                } else {
                    if (!tail.compareAndSet(curTail, curTail.next!!)) {
                        continue
                    }
                }
            }

            if(tail.value.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                println("enqueue $element id $i")
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
                // println("dequeue null like empty ${deqIdx.value} ${enqIdx.value}")
                return null
            }
            var curHead = head.value
            val i = deqIdx.getAndAdd(1)
            val id = i / SEGMENT_SIZE
            if (id > curHead.id) {
                val newSegment = curHead.next ?: return null
                if (head.compareAndSet(curHead, newSegment)) {
                    curHead = newSegment
                } else {
                    continue
                }
            }

            val result = head.value.elements[(i % SEGMENT_SIZE).toInt()].getAndSet(0) ?: continue
            return result as E?
        }


        // TODO("implement me")
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return (deqIdx.value >= enqIdx.value)
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

