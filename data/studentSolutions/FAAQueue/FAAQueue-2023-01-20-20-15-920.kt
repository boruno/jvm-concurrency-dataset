package mpp.faaqueue

import kotlinx.atomicfu.*

sealed interface Maybe<T>

class Done<T> : Maybe<T>
class Boxed<T>(val t: T) : Maybe<T>

class FAAQueue<T> {
    private val head: AtomicRef<Segment<T>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment<T>> // Tail pointer, similarly to the Michael-Scott queue

    init {
        val firstNode = Segment<T>()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: T) {
        while (true) {
            val tailVal = tail.value
            val tailNextVal = tailVal.next.value
            if (tailNextVal != null) {
                tail.compareAndSet(tailVal, tailNextVal)
                continue
            }
            val enqIdx = tailVal.enqIdx.getAndIncrement()
            if (enqIdx >= SEGMENT_SIZE) {
                if (tail.value.next.compareAndSet(null, Segment(x))) {
                    return
                }
            } else {
                if (tailVal.elements[enqIdx].compareAndSet(null, Boxed(x))) {
                    return
                }
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
            val headVal = head.value
            val deqIdx = headVal.deqIdx.getAndIncrement()
            if (deqIdx >= SEGMENT_SIZE) {
                if (headVal.next.value != null) {
                    val headNext = headVal.next.value!!
                    head.compareAndSet(headVal, headNext)
                    continue
                }
                return null
            }
            return when (val elem = headVal.elements[deqIdx].getAndSet(Done())) {
                is Boxed -> elem.t
                is Done -> null
                null -> continue
            }
        }
    }

    /**
     * Returns `true` if this queue is empty;
     * `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                return if (head.value.isEmpty) {
                    if (head.value.next.value != null) {
                        head.value = head.value.next.value!!
                        continue
                    }
                    true
                } else {
                    false
                }
            }
        }
}

private class Segment<T> {
    val next = atomic<Segment<T>?>(null)
    val enqIdx = atomic(0) // index for the next enqueue operation
    val deqIdx = atomic(0) // index for the next dequeue operation
    val elements = atomicArrayOfNulls<Maybe<T?>>(SEGMENT_SIZE)

    constructor() // for the first segment creation

    constructor(x: T?) { // each next new segment should be constructed with an element
        elements[0].getAndSet(Boxed(x))
        enqIdx.incrementAndGet()
    }

    val isEmpty: Boolean
        get() {
            val deqValue = deqIdx.value
            val enqValue = enqIdx.value
            return deqValue >= enqValue || deqValue >= SEGMENT_SIZE
        }

}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS


/*
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


    */
/**
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
            if (curTail.cas((curEnqIdx % SEGMENT_SIZE).toInt(),
                    null, Just(element))) break
        }
    }

    */
/**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     *//*

    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            var curHead = head.value
            val curDeqIdx = deqIdx.getAndIncrement()
            while (curHead.id * SEGMENT_SIZE <= curDeqIdx) {
                curHead = curHead.next.value ?: return null
            }
            val i = (curDeqIdx % SEGMENT_SIZE).toInt()
            while (true) {
                val elem = curHead.get(i)
                when (elem) {
                    is Nothing -> return null
                    is Just<*> -> return elem.value as E
                    null -> continue
                }
            }

            */
/*if (curHead.cas(i, null, null)) {
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
            return curHead.get(i)*//*

        }
    }

    */
/**
     * Returns `true` if this queue is empty, or `false` otherwise.
     *//*

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
