//@file:Suppress("UNCHECKED_CAST")

//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val currentTail = tail.value

            val i = enqIdx.getAndIncrement()

            val segment = findSegment(currentTail, i.toInt())

            if (tail.compareAndSet(currentTail, segment)) {
                if (segment.elements[i.toInt() % SEGMENT_SIZE].compareAndSet(null, element)) {
                    return
                }
            }

            /// Если индекс для инкремента в сегменте с бОльшим айдишником (находится после текущего)
//            if ((i.toInt() / SEGMENT_SIZE) > currentTail.getId()    /*(i.toInt() % SEGMENT_SIZE) == 0*/   /*i >= SEGMENT_SIZE*/) {
////                println("enqueue new segment")
//                val newSegmentId = currentTail.getId() + 1
//
//                val newTail = Segment(newSegmentId)
//
////                val currentTailNext = currentTail.next.value
//                val currentTailNext = segment.next.value
//
//                if (currentTailNext == null) {
//                    newTail.elements[0].value = element
//                    segment.next.value = newTail
//                    if (tail.compareAndSet(segment, newTail)) {
////                        print("enq i = ")
////                        println(i.toInt())
////                        print("newTail.elements[0].value = ")
////                        println(newTail.elements[0].value)
////                        enqIdx.value = 1
//                        return
//                    }
//                }
//            } else {
//                if (i.toInt() == 0) {
//                    if (tail.value.elements[i.toInt()].compareAndSet(null, element)) {
////                        print("enq i = ")
////                        println(i.toInt())
//                        return
//                    }
//                } else {
////                    print("element = ")
////                    println(element)
//                    if (tail.value.elements[i.toInt() % SEGMENT_SIZE].compareAndSet(null, element)) {
////                        print("enq i = ")
////                        println(i.toInt())
//                        return
//                    }
//                }
//            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
//            println("\n DEqUEUE----------")
            if (isEmpty) {
//                println("isEmpty")
//                print("enqIdx = ")
//                println(enqIdx.value)
//                print("deqIdx = ")
//                println(deqIdx.value)
                return null
            }

//            print("enqIdx = ")
//            println(enqIdx.value)
//            print("deqIdx = ")
//            println(deqIdx.value)

            val currentHead = head.value

            val i = deqIdx.getAndIncrement()

            val segment = findSegment(currentHead, i.toInt())

            if (head.compareAndSet(currentHead, segment)) {
                val value = segment.elements[(i % SEGMENT_SIZE).toInt()].getAndSet(null)
                if (value != null) {
                    return value as E?
                } else {
                    return null
                }
            }
//            if ((i.toInt() / SEGMENT_SIZE) > currentHead.getId()   /*(i.toInt() % SEGMENT_SIZE) == 0*/    /*i >= SEGMENT_SIZE*/) {
////                val currentHeadNext = currentHead.next.value
//                val currentHeadNext = currentHead.next.value
//
//                if (currentHeadNext != null) {
//                    if (
//                        head.compareAndSet(currentHead, currentHeadNext)
//                    ) {
////                        println("update head")
//                        deqIdx.value = i
//                    }
//                } else {
//                    return null
//                }
//            } else {
//                if (i.toInt() == 0) {
//                    val value = currentHead.elements[i.toInt()].getAndSet(null)
//                    if (value != null) {
////                        print("deq i = ")
////                        println(i.toInt())
//                        return value as E?
//                    }
//                } else {
//                    val value = currentHead.elements[i.toInt() % SEGMENT_SIZE].getAndSet(null)
////                    print("value = ")
////                    println(value)
//                    if (value != null) {
////                        print("deq i = ")
////                        println(i.toInt())
//                        return value as E?
//                    }
//                }
//            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
//                println("is empty")
//                print("enq = ")
//                println(enqIdx.value)
//                print("deq = ")
//                println(deqIdx.value)
//                println("-------\n")
            return head.value == tail.value && deqIdx.value >= enqIdx.value
        }

    fun findSegment(start: Segment, index: Int): Segment {
        val id = index / SEGMENT_SIZE
        var startSegment = start

        while (startSegment.getId() != id) {
            val nextSegment = startSegment.next.value
            if (nextSegment != null) {
                startSegment = nextSegment
            } else {
                val next = startSegment.next.value
                val newSegment = Segment(id)
                if (startSegment.next.compareAndSet(next, newSegment)) {
//                startSegment.next.value = newSegment
                    return newSegment
                }
            }
        }
        return startSegment
    }
}

class Segment {
    val id: AtomicInt = atomic(0)
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun getId(): Int {
        return id.value
    }

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }

    constructor(id: Int) {
        this.id.value = id
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

