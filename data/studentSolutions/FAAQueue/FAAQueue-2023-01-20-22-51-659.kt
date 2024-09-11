package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment<E>(1)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }


    
/**
     * Adds the specified element [x] to the queue.
     */

    fun enqueue(element: E) {
    /*while (true) {
        val tail = this.tail.value
        val enqInx = tail.enqIdx.getAndIncrement();
        if (enqInx >= SEGMENT_SIZE) {
            val newTail = Segment(x)
            if (!tail.next.compareAndSet(null, newTail)) {
                tail.next.value?.let { this.tail.compareAndSet(tail, it) }
                continue
            }
            this.tail.compareAndSet(tail, newTail)
            return
        } else {
            if (tail.elements[enqInx].compareAndSet(null, x))
                return
        }
    }*/
        while (true) {
            var curTail = tail.value
            val curEnqIdx = enqIdx.getAndIncrement()
            if (curEnqIdx >= SEGMENT_SIZE * curTail.id) {
                val newTail = Segment(Just(element), curTail.id + 1)
                if (!curTail.next.compareAndSet(null, newTail)) {
                    curTail.next.value?.let { tail.compareAndSet(curTail, it) }
                    continue
                }
                tail.compareAndSet(curTail, newTail)
                return
            } else {
                if (curTail.cas(curEnqIdx.toInt(), null, Just(element)))
                    return
            }
            /*while (curTail.id * SEGMENT_SIZE <= curEnqIdx) {
                val nextTail = curTail.next.value
                if (nextTail != null) {
                    curTail = nextTail
                    continue
                }
                val newTail = Segment<E>(curTail.id + 1)
                if (curTail.next.compareAndSet(null, newTail)) {
                    tail.compareAndSet(curTail, newTail)
                }
            }
            if (curTail.cas((curEnqIdx % SEGMENT_SIZE).toInt(),
                    null, Just(element))) break*/
        }
    }
/**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */

    fun dequeue(): E? {
    /*
    while (true) {
            val head = this.head.value
            val deqInx = head.deqIdx.getAndIncrement()
            if (deqInx >= SEGMENT_SIZE) {
                val next = head.next.value ?: return null
                this.head.compareAndSet(head, next)
                continue
            }
            return (head.elements[deqInx].getAndSet(DONE) ?: continue) as T?
        }
     */
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            var curHead = head.value
            val curDeqIdx = deqIdx.getAndIncrement()
            if(curDeqIdx >= SEGMENT_SIZE * curHead.id)  {
                val next = curHead.next.value ?: return null
                head.compareAndSet(curHead, next)
                continue
            }
            return (curHead.gas(curDeqIdx.toInt()) ?: continue) as E?
            /*while (curHead.id * SEGMENT_SIZE <= curDeqIdx) {
                curHead = curHead.next.value ?: return null
            }*/
            /*val i = (curDeqIdx % SEGMENT_SIZE).toInt()
            while (true) {
                val elem = curHead.get(i)
                when (elem) {
                    is Nothing -> return null
                    is Just<*> -> return elem.value as E
                    null -> continue
                }
            }*/
    
        }
    }
    
/**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */

    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }
}

private class Segment<E>(val id: Int) {
    val next: AtomicRef<Segment<E>?> = atomic(null)
    val elements = atomicArrayOfNulls<Maybe<E>?>(SEGMENT_SIZE)

    constructor(x: Maybe<E>?, id: Int) : this(id) {
        put(0, x)
    }

    fun get(i: Int) = elements[i].value
    fun gas(i: Int) = elements[i].getAndSet(Nothing())
    fun cas(i: Int, expect: Maybe<E>?, update: Maybe<E>?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Maybe<E>?) {
        elements[i].value = value
    }
}

interface Maybe<E>;
private class Nothing<E>: Maybe<E>
private class Just<E>(val value: E): Maybe<E>

const val SEGMENT_SIZE: Int = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

/*
class FAAQueue<T> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    */
/**
     * Adds the specified element [x] to the queue.
     *//*

    fun enqueue(x: T) {
        while (true) {
            val tail = this.tail.value
            val enqInx = tail.enqIdx.getAndIncrement();
            if (enqInx >= SEGMENT_SIZE) {
                val newTail = Segment(x)
                if (!tail.next.compareAndSet(null, newTail)) {
                    tail.next.value?.let { this.tail.compareAndSet(tail, it) }
                    continue
                }
                this.tail.compareAndSet(tail, newTail)
                return
            } else {
                if (tail.elements[enqInx].compareAndSet(null, x))
                    return
            }
        }
    }

    */
/**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     *//*

    @Suppress("UNCHECKED_CAST")
    fun dequeue(): T? {
        while (true) {
            val head = this.head.value
            val deqInx = head.deqIdx.getAndIncrement()
            if (deqInx >= SEGMENT_SIZE) {
                val next = head.next.value ?: return null
                this.head.compareAndSet(head, next)
                continue
            }
            return (head.elements[deqInx].getAndSet(DONE) ?: continue) as T?
        }
    }

    */
/**
     * Returns `true` if this queue is empty;
     * `false` otherwise.
     *//*

    val isEmpty: Boolean get() {
        while (true) {
            if (head.value.isEmpty) {
                if (head.value.next.value == null) return true
                head.value = head.value.next.value!!
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
        enqIdx.compareAndSet(0, 1)
        elements[0].compareAndSet(null, x)
    }

    val isEmpty: Boolean get() = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE

}

private val DONE = Any() // Marker for the "DONE" slot state; to avoid memory leaks
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
*/
