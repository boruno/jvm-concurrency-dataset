//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment<E>> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment<E>> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    init {
        val firstNode = Segment<E>(1)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }


    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val afterTail = curTail.next.value
            if (afterTail != null) {
                tail.compareAndSet(curTail, afterTail)
                continue
            }
            val i = enqIdx.getAndIncrement()
            if (i >= curTail.index * SEGMENT_SIZE) {
                if (curTail.constructNext(element)) {
                    return
                }
            } else {
                if (curTail.elements[i % SEGMENT_SIZE].compareAndSet(null, OKBox(element))) {
                    return
                } else {
                    println(curTail.elements[i % SEGMENT_SIZE].value)
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
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            if (i >= curHead.index * SEGMENT_SIZE) {
                if (curHead.next.value != null) {
                    head.compareAndSet(curHead, curHead.next.value!!)
                    continue
                } else {
                    deqIdx.getAndDecrement()
                    return null
                }
            }
            val element = curHead.elements[i % SEGMENT_SIZE].getAndSet(BrokenBox())
            if (element == null) {
                continue
            } else {
                return element.value
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                if (deqIdx.value >= enqIdx.value) {
                    if (head.value.next.value != null) {
                        head.compareAndSet(head.value, head.value.next.value!!)
                        continue
                    } else {
                        return true
                    }
                }
                return false
            }
        }

}

open class Box<T>(val value: T?)

class OKBox<T>(value: T) : Box<T>(value)

class BrokenBox<T> : Box<T>(null)

private class Segment<T>(val index: Int) {
    val next = atomic<Segment<T>?>(null)
    val elements = atomicArrayOfNulls<Box<T>>(SEGMENT_SIZE)

    fun constructNext(x: T): Boolean {
        val seg = Segment<T>(index + 1)
        seg.elements[0].getAndSet(OKBox(x))
        return next.compareAndSet(null, seg)
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

