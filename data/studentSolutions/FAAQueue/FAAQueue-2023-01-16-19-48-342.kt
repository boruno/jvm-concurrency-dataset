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
        // println("try enqueue $element")
        while (true) {
            var curTail = tail.value
            if (curTail.next != null) {
                tail.compareAndSet(curTail, curTail.next!!)
                continue
            }

            var i: Long
            while (true) {
                i = enqIdx.getAndAdd(1)
                val id = i / SEGMENT_SIZE
                if (id > curTail.id) {
                    break
                }
                if (curTail.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                    // println("enqueue $element like normal in $i")
                    return
                }
            }

            val newSegment = Segment()
            newSegment.id = i / SEGMENT_SIZE
            if (curTail.next == null) {
                if (i % SEGMENT_SIZE != 0L) {
                    continue
                }
                curTail.next = newSegment
                if (tail.compareAndSet(curTail, newSegment)) {
                    if (newSegment.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                        // println("enqueue $element like new in $i")
                        return
                    }
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
            /*
            if (isEmpty) {
                // println("dequeue null like empty ${deqIdx.value} ${enqIdx.value}")
                return null
            }*/
            var curHead = head.value
            var result : E?
            var i : Long
            outerloop@ while (true) {
                i = deqIdx.getAndAdd(1)
                // println("enq: ${enqIdx.value} deq: $i")
                val id = i / SEGMENT_SIZE
                if (id == curHead.id) {
                    while (true) {
                        var value = curHead.elements[(i % SEGMENT_SIZE).toInt()].value

                        if (value != null && value != "0") {
                            if (curHead.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(value, "0")) {
                                // println("dequeue $value id $i in classic way")
                                result = value as E
                                break@outerloop
                            }
                        } else if (value == null) {
                            if (!curHead.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, "0")) {
                                val valueNew = curHead.elements[(i % SEGMENT_SIZE).toInt()].value
                                if (valueNew != "0") {
                                    // println("this code shouldn't work, but IDK, maybe work")
                                    return valueNew as E
                                }
                            }
                            break
                        } else {
                            result = null
                            break
                        }
                    }
                } else {
                    result = null
                }

                if (i + 1 >= enqIdx.value) {
                    while (true) {
                        val enq = enqIdx.getAndAdd(0)
                        val deq = deqIdx.getAndAdd(0)

                        if (enqIdx.value != enq) continue
                        if (deq <= enq) {
                            result = null
                            break@outerloop
                        }
                        if (enqIdx.compareAndSet(enq, deq)) {
                            result = null
                            break@outerloop
                        }
                    }
                }

                if (id != curHead.id) {
                    result = null
                    break
                }
            }

            if (result != null) {
                // println("dequeue $result")
                return result
            }

            if (i / SEGMENT_SIZE < curHead.id) {
                continue
            }
            
            if (curHead.next == null) {
                // println("dequeue null like empty ${deqIdx.value} ${enqIdx.value}")
                return null
            }

            head.compareAndSet(curHead, curHead.next!!)
                // println("dequeue here $i")
            /*
            while (true) {
                if (curHead.next == null) break
                if (curHead.next!!.id == i / SEGMENT_SIZE) {
                    // println("+")
                    break
                }
                // println("-")
                curHead = curHead.next!!
            }*/

            if (curHead.next == null) {
                // println("dequeue null like empty ${deqIdx.value} ${enqIdx.value}")
                return null
            }
            val value = curHead.next!!.elements[(i % SEGMENT_SIZE).toInt()].value
            if (value != null && value != "0") {
                if (curHead.next!!.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(value, "0")) {
                    // println("dequeue $value id $i")
                    return value as E
                }
            } else {
                if (!curHead.next!!.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, "0")) {
                    val valueNew = curHead.next!!.elements[(i % SEGMENT_SIZE).toInt()].value
                    if (valueNew != "0") {
                        // println("this code shouldn't work, but IDK, maybe work")
                        return valueNew as E
                    }
                }
            }

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

