//package mpp.faaqueue

import kotlinx.atomicfu.*
import java.util.concurrent.Phaser
import kotlin.concurrent.thread

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)

        println("#".repeat(100))
    }

//    private fun println(x: Any) {}

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        println("> $element")
        while (true) {
            val curTail = tail.value

            val curIndex = enqIdx.getAndAdd(1)

            if (!curTail.inRange(curIndex)) {
                var next = curTail

                while (!next.next.compareAndSet(null, Segment(curIndex, Segment.Element(element)))) {
                    next = next.next.value!!
                }

                if (!tail.compareAndSet(curTail, next.next.value!!)) {
                    continue
                }

                println("$element> moved tail")
                return
            }

            if (curTail.cas(curIndex, null, Segment.Element(element))) {
                println("$element> cas")
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
                println("< empty")
                return null
            }

            val curHead = head.value

            val curIndex = deqIdx.getAndAdd(1)

            var my = curHead
            while (!my.inRange(curIndex)) {
                my = my.next.value ?: return null
                head.compareAndSet(curHead, my)
            }

            if (curHead.cas(curIndex, null, Segment.Broken)) {
                println("< broken")
                continue
            }

            println("< ${(my[curIndex] as? Segment.Element<E>)?.x}")
            @Suppress("UNCHECKED_CAST")
            return (my[curIndex] as? Segment.Element<E>)?.x
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value <= deqIdx.value
        }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            var test = FAAQueue<Int>()

//            require(test.isEmpty) { "Expected empty queue" }
//            require(test.dequeue() == null) { "Expected empty queue" }
//            require(test.isEmpty) { "Expected empty queue" }
//            require(test.dequeue() == null) { "Expected empty queue" }
//            require(test.isEmpty) { "Expected empty queue" }
//            test.enqueue(1)
//            test.enqueue(2)
//            require(!test.isEmpty) { "Expected not empty queue" }
//            require(test.dequeue() == 1) { "Expected 1" }
//            require(!test.isEmpty) { "Expected not empty queue" }
//            require(test.dequeue() == 2) { "Expected 2" }
//            require(test.dequeue() == null) { "Expected empty queue" }
//            require(test.isEmpty) { "Expected empty queue" }

            test = FAAQueue()
//            repeat(10) { test.enqueue(it) }
//            repeat(10) { val x = test.dequeue(); println("Expected $it, but was $x") }
//            repeat(5) { test.enqueue(it) }
//            repeat(15) { val x = test.dequeue(); println("Expected $it, but was $x") }
//            repeat(5) { test.enqueue(it) }
//            repeat(5) { val x = test.dequeue(); println("Expected $it, but was $x") }

            test.enqueue(1)
            print(test.dequeue())
            test.enqueue(2)
            print(test.dequeue())
            print(test.dequeue())
            test.enqueue(3)
            print(test.dequeue())

//            repeat(150) {
//                test = FAAQueue()
//                test.enqueue(2)
//                test.enqueue(6)
//                println(test.dequeue())
//                val phaser = Phaser(3)
//                val t1 = thread(true) { println(test.dequeue()); phaser.arrive() }
//                val t2 = thread(true) { println(test.dequeue()); phaser.arrive() }
//                phaser.arriveAndAwaitAdvance()
//                test.enqueue(-4)
//                println(test.dequeue())
//            }
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

    fun inRange(x: Long) = x in (this.id * SEGMENT_SIZE until (this.id + 1) * SEGMENT_SIZE)

    operator fun get(i: Long) = elements[(i % SEGMENT_SIZE).toInt()].value
    fun cas(i: Long, expected: ElementType?, update: ElementType?) = elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(expected, update)
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
