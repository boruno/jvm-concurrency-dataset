//package mpp.faaqueue

import kotlinx.atomicfu.*
import kotlin.math.ceil

class FAAQueue<T> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: T) {
        while (true) {
            val tail: Segment = tail.value
            val enqIdx: Int = tail.enqIdx.getAndIncrement()
            if (enqIdx >= SEGMENT_SIZE) {
                val newTail = Segment(x)
                if (tail.next.compareAndSet(null, newTail)) {
                    this.tail.compareAndSet(tail, newTail)
                    return
                }
                this.tail.compareAndSet(tail, tail.next.value!!)
            } else if (tail.elements[enqIdx].compareAndSet(null, x)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): T? {
        while (true) {
            val head = head.value
            val deqIdx = head.deqIdx.getAndIncrement()
            if (deqIdx >= SEGMENT_SIZE) {
                val headNext: Segment = head.next.value ?: return null
                this.head.compareAndSet(head, headNext)
                continue
            }
            val res = head.elements[deqIdx].getAndSet(DONE) ?: continue
            return res as T?
        }
    }

    /**
     * Returns `true` if this queue is empty;
     * `false` otherwise.
     */
    val isEmpty: Boolean get() {
        while (true) {
            val head: Segment = head.value
            if (head.isEmpty) {
                val headNext: Segment = head.next.value ?: return true
                this.head.compareAndSet(head, headNext)
                continue
            } else {
                return false
            }
        }
    }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val enqIdx = atomic(0) // index for the next enqueue operation
    val deqIdx = atomic(0) // index for the next dequeue operation
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    constructor() // for the first segment creation

    constructor(x: Any?) { // each next new segment should be constructed with an element
        enqIdx.value = 1
        elements[0].getAndSet(x)
    }

    val isEmpty: Boolean get() = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE

}


/*class FAAQueue<E> {
    private val head: AtomicRef<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment<E>(1)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    *//**
     * Adds the specified element [x] to the queue.
     *//*
    fun enqueue(element: E) {
        while (true) {
            var curTail = tail.value
            val curEnqIdx = enqIdx.getAndIncrement()
            while ((curTail.id + 1)* SEGMENT_SIZE - 1< curEnqIdx) {
                val nextTail = curTail.next.value
                if (nextTail != null) {
                    curTail = nextTail
                    continue
                }
                val newTail = Segment(element, curTail.id + 1)
                if (curTail.next.compareAndSet(null, newTail)) {
                    tail.compareAndSet(curTail, newTail)
                    break
                } else {
                    enqIdx.getAndDecrement()
                }
            }
            if (curTail.cas((curEnqIdx % SEGMENT_SIZE).toInt(),
                    null, element))
                break
            else enqIdx.decrementAndGet()
        }
    }

    *//**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     *//*
    fun dequeue(): E? {
        while (true) {
            //println("try to deque")
            if (deqIdx.value >= enqIdx.value) return null
            var curHead = head.value
            val curDeqIdx = deqIdx.getAndIncrement()
            while (curHead.id < ceil((curDeqIdx + 1).toDouble() / SEGMENT_SIZE)) {
                curHead = curHead.next.value ?: return null
            }
            val i = (curDeqIdx % SEGMENT_SIZE).toInt()

            if (curHead.cas(i, null, null)) {
                continue
            }
            if (i == SEGMENT_SIZE - 1 && curHead.next.value != null) {
                val newHead = curHead.next.value!!
                if (head.compareAndSet(curHead, newHead)) {
                    return curHead.get(i)
                } else {
                    deqIdx.decrementAndGet()
                }
            }
            return curHead.get(i)
        }
    }

    *//**
     * Returns `true` if this queue is empty, or `false` otherwise.
     *//*
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }
}

private class Segment<E>(val id: Int) {
    val next: AtomicRef<Segment<E>?> = atomic(null)
    val elements = atomicArrayOfNulls<E?>(SEGMENT_SIZE)

    constructor(x: E?, id: Int) : this(id) {
        put(0, x)
    }

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: E?, update: E?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: E?) {
        elements[i].value = value
    }
}*/

val DONE = Any()

const val SEGMENT_SIZE: Int = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

