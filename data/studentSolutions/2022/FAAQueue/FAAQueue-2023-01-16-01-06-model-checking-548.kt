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
        // println("try enqueue $element")
        while (true) {
            var curTail = tail.value
            val i = enqIdx.getAndAdd(1)
            /*if (i < deqIdx.value) {
                continue
            }*/
            val id = i / SEGMENT_SIZE
            if (id > curTail.id) {
                val newSegment = Segment()
                newSegment.id = id
                if (curTail.next == null) {
                    curTail.next = newSegment
                    tail.compareAndSet(curTail, newSegment)
                    curTail = newSegment
                } else {
                    continue
                }
            }

            if(curTail.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                // println("enqueue $element id $i")
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
            /*
            if (isEmpty) {
                // println("dequeue null like empty ${deqIdx.value} ${enqIdx.value}")
                return null
            }*/
            val curHead = head.value
            var result : E?
            outerloop@ while (true) {
                val i = deqIdx.getAndAdd(1)
                val id = i / SEGMENT_SIZE
                if (id == curHead.id) {
                    while (true) {
                        var value = curHead.elements[(i % SEGMENT_SIZE).toInt()].value

                        if (value != null) {
                            if (curHead.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(value, null)) {
                                // println("dequeue $value id $i")
                                result = value as E
                                break@outerloop
                            }
                        } else {
                            break
                        }
                    }
                }

                if (isEmpty) {
                    // println("dequeue null like empty ${deqIdx.value} ${enqIdx.value}")
                    result = null
                    break@outerloop
                }
            }

            if (result != null) return result
            if (curHead.next == null) return null
            head.compareAndSet(curHead, curHead.next!!)
        }


        // TODO("implement me")
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            if (deqIdx.value >= enqIdx.value) {
                while (true) {
                    val enq = enqIdx.getAndAdd(0)
                    val deq = deqIdx.getAndAdd(0)

                    if (enqIdx.value != enq) continue
                    if (deq <= enq) return true
                    if (enqIdx.compareAndSet(enq, deq)) return true
                }
            } else {
                return false
            }
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

