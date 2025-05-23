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
            val curTail = tail.value

            val curIndex = enqIdx.getAndAdd(1)

            if (!curTail.inRange(curIndex)) {
                var next = tail.value

                while (!next.next.compareAndSet(null, Segment(curIndex, Segment.Element(element)))) {
                    next = next.next.value!!
                }

                return
            }

            if (curTail.cas(curIndex, null, Segment.Element(element))) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        if (isEmpty) {
            return null
        }

        while (true) {
            val curHead = head.value

            val curIndex = deqIdx.getAndAdd(1)

            var my = curHead
            while (!my.inRange(curIndex)) {
                my = my.next.value!!
            }

            head.compareAndSet(curHead, my)

            if (curHead.cas(curIndex, null, Segment.Broken)) {
                continue
            }

            @Suppress("UNCHECKED_CAST")
            return (curHead[curIndex] as? Segment.Element<E>)?.x
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value == deqIdx.value
        }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            var test = FAAQueue<Int>()

            require(test.isEmpty) { "Expected empty queue" }
            require(test.dequeue() == null) { "Expected empty queue" }
            require(test.isEmpty) { "Expected empty queue" }
            require(test.dequeue() == null) { "Expected empty queue" }
            require(test.isEmpty) { "Expected empty queue" }
            test.enqueue(1)
            test.enqueue(2)
            require(!test.isEmpty) { "Expected not empty queue" }
            require(test.dequeue() == 1) { "Expected 1" }
            require(!test.isEmpty) { "Expected not empty queue" }
            require(test.dequeue() == 2) { "Expected 2" }
            require(test.dequeue() == null) { "Expected empty queue" }
            require(test.isEmpty) { "Expected empty queue" }

            test = FAAQueue()
            repeat(10) { test.enqueue(it) }
            repeat(10) { val x = test.dequeue(); println("Expected $it, but was $x") }
        }
    }
}

private class Segment {
    sealed interface ElementType
    class Element<E>(val x: E) : ElementType
    object Broken : ElementType

    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<ElementType>(SEGMENT_SIZE)
    private val id: Long

    constructor() {
        this.id = 0
    }

    constructor(id: Long, element: ElementType) {
        this.id = id / SEGMENT_SIZE
        this.elements[0].getAndSet(element)
    }

    fun inRange(id: Long) = id in (this.id * SEGMENT_SIZE until (this.id + 1) * SEGMENT_SIZE)

    operator fun get(i: Long) = elements[(i % SEGMENT_SIZE).toInt()].value
    fun cas(i: Long, expected: ElementType?, update: ElementType?) = elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(expected, update)
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
