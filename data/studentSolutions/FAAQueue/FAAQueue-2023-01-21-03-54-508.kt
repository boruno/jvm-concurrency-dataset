//package mpp.faaqueue

import kotlinx.atomicfu.*

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
            while (curTail.id * SEGMENT_SIZE <= curEnqIdx) {
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
            if (curTail.cas(
                    (curEnqIdx % SEGMENT_SIZE).toInt(),
                    null, Just(element)
                )
            ) break

        }
    }

    *//**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     *//*


    fun dequeue(): E? {
        while (true) {
            *//*if (deqIdx.value >= enqIdx.value){
                //deqIdx.getAndDecrement()
                return null
            }*//*
            val curDeqIdx = deqIdx.getAndIncrement()
            var curHead = head.value
            while (curHead.id * SEGMENT_SIZE <= curDeqIdx) {
                val nextHead = curHead.next.value
                if (nextHead != null) {
                    curHead = nextHead
                    continue
                } else {
                    val newHead = Segment<E>(curHead.id + 1)
                    if (curHead.next.compareAndSet(null, newHead)) {
                        tail.compareAndSet(curHead, newHead)
                    }
                }
            }
            if (curDeqIdx >= SEGMENT_SIZE * curHead.id) {
                val next = curHead.next.value
                if (next == null) {
                    deqIdx.getAndDecrement()
                    return null
                }
                head.compareAndSet(curHead, next)
                curHead = head.value
            }

            val i = (curDeqIdx % SEGMENT_SIZE).toInt()
            while (true) {
                val elem = curHead.get(i)
                when (elem) {
                    is Nothing -> return null
                    is Just<*> -> {
                        curHead.cas(i, elem, Nothing())
                        return elem.value as E
                    }
                    null -> {
                        if (curHead.cas(i, null, Nothing())) break
                    }
                }
            }
        }
    }
*//**
 * Returns `true` if this queue is empty, or `false` otherwise.
 *//*


    val isEmpty: Boolean
        get() {

            return deqIdx.value >= enqIdx.value
        }
}*/

/*
private class Segment<E>(val id: Int) {
    val next: AtomicRef<Segment<E>?> = atomic(null)
    val elements = atomicArrayOfNulls<Maybe<E>?>(SEGMENT_SIZE)

    constructor(x: Maybe<E>?, id: Int) : this(id) {
        put(0, x)
    }

    fun get(i: Int) = elements[i].value
    fun gas(i: Int) = elements[i].getAndSet(Nothing()) as Just<E>
    fun cas(i: Int, expect: Maybe<E>?, update: Maybe<E>?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Maybe<E>?) {
        elements[i].value = value
    }
}

interface Maybe<E>;
private class Nothing<E>: Maybe<E>
private class Just<E>(val value: E): Maybe<E>

const val SEGMENT_SIZE: Int = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

*/

class FAAQueue<T> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(1)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }


    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: T) {
        while (true) {
            val curTail = tail.value
            val curEnqIdx = enqIdx.getAndIncrement();
            if (curEnqIdx >= SEGMENT_SIZE * curTail.id) {
                val newTail = Segment(x, curTail.id + 1)
                if (!curTail.next.compareAndSet(null, newTail)) {
                    val next = curTail.next.value ?: continue
                    tail.compareAndSet(curTail, next)
                    continue
                }
                this.tail.compareAndSet(curTail, newTail)
                return
            } else {
                if (curTail.elements[curEnqIdx.toInt() % SEGMENT_SIZE].compareAndSet(null, x))
                    return
            }
        }
    }


    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */

    @Suppress("UNCHECKED_CAST")
    fun dequeue(): T? {
        while (true) {
            val curHead = head.value
            val curDeqIdx = deqIdx.getAndIncrement()
            if (curDeqIdx >= SEGMENT_SIZE * curHead.id) {
                val next = curHead.next.value ?: return null
                this.head.compareAndSet(curHead, next)
                continue
            }
            return (curHead.elements[curDeqIdx.toInt() % SEGMENT_SIZE].getAndSet(DONE) ?: continue) as T?
        }
    }
/**
     * Returns `true` if this queue is empty;
     * `false` otherwise.
     */

    val isEmpty: Boolean get() {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                if (head.value.next.value == null) return true
                head.value = head.value.next.value!!
                continue
            } else {
                return false
            }
        }
    }
}

private class Segment(val id: Int) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    /*val enqIdx = atomic(0)
    val deqIdx = atomic(0)*/

    constructor(element: Any?, id: Int): this(id) { // each next new segment should be constructed with an element
        //enqIdx.compareAndSet(0, 1)
        elements[0].compareAndSet(null, element)
    }

    //val isEmpty: Boolean get() = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE

}

private val DONE = Any()
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
