package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
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
//    @Suppress("UNCHECKED_CAST")
//    fun enqueue(element: E) {
//        println(element)
//        while (true) {
//            val curTail = tail.value
//            val i = enqIdx.getAndIncrement()
//            if(curTail.index < i / SEGMENT_SIZE) {
//                val seg = Segment(curTail.index + 1)
//                if(curTail.next.compareAndSet(null, seg))
//                    tail.getAndSet(seg)
//                continue
//            }
//            if (tail.value.cas((i % SEGMENT_SIZE), null, element))
//                return
//        }
//    }

    fun enqueue(x: E) {
        while (true) {
            val curTail = tail.value
            var i = curTail.enqIdx.getAndIncrement()
            if (i >= SEGMENT_SIZE) {
                val newSegment = Segment()
                if (curTail.next.compareAndSet(null, newSegment)) {
                    this.tail.getAndSet(newSegment)
                    i = 0
                } else
                    this.tail.compareAndSet(curTail, curTail.next.value!!)
            }
            if (curTail.cas(i, null, x))
                break
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
//    @Suppress("UNCHECKED_CAST")
//    fun dequeue(): E? {
//        while (true) {
//            if(deqIdx.value <= enqIdx.value)
//                return null
//            val curHead = head.value
//            val i = deqIdx.getAndIncrement()
//            if(curHead.index < i / SEGMENT_SIZE) {
//                val seg = Segment(curHead.index + 1)
//                if(curHead.next.compareAndSet(null, seg))
//                    head.getAndSet(seg)
//                continue
//            }
//            if (head.value.cas((i % SEGMENT_SIZE), null, KEK))
//                continue
//            return head.value.get((i % SEGMENT_SIZE)) as E?
//        }
//    }

    @Suppress("UNCHECKED_CAST")
    fun dequeue(): E? {
        while (true) {
            val curHead = this.head.value
            val i = curHead.deqIdx.getAndIncrement()
            if (i >= SEGMENT_SIZE) {
                val headNext = curHead.next.value ?: return null
                head.compareAndSet(curHead, headNext)
                continue
            }
            val value = curHead.get(i)
            if(curHead.cas(i, value, KEK))
                return value as E?
            continue
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val head: Segment = head.value
                if (head.isEmpty) {
                    val headNext: Segment = head.next.value ?: return true
                    this.head.compareAndSet(head, headNext)
                    continue
                } else return false
            }
        }
}

class Segment() {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    val enqIdx = atomic(0)
    val deqIdx = atomic(0)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }

    val isEmpty: Boolean get() = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE
}

private object KEK: Any()
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
