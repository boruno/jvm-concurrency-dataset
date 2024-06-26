package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    private val FAIL = Object()

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
            val curTail = tail.value

            val i = enqIdx.getAndIncrement()

            val s = findSegment(curTail, sidx(i))
            moveTail(s)
            if (s.cas(idx(i), null, element)) {
                return
            }
        }
    }

    private fun moveHead(target: Segment) {
        while (true) {
            val cur = head.value

            if (cur.num >= target.num) {
                return
            } else {
                head.compareAndSet(cur, cur.next.value!!)
            }
        }
    }

    private fun moveTail(target: Segment) {
        while (true) {
            val cur = tail.value

            if (cur.num >= target.num) {
                return
            } else {
                tail.compareAndSet(cur, cur.next.value!!)
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty)
                return null

            val curHead = head.value

            val i = deqIdx.getAndIncrement()

            val s = findSegment(curHead, sidx(i))
            moveHead(s)
            if (!s.cas(idx(i), null, FAIL)) {
                return s.get(idx(i)) as E?
            }
        }
    }

    private fun findSegment(curTail: Segment, sidx: Int): Segment {
        var found: Segment = curTail
        while (found.num < sidx) {
            val next = found.next.value

            found = if (next != null) {
                next
            } else {
                val newSegment = Segment(found.num + 1)
                if (found.next.compareAndSet(next, newSegment)) {
                    newSegment
                } else {
                    found.next.value!!
                }
            }
        }

        return found
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }

    private fun idx(idx: Long): Int {
        return (idx % SEGMENT_SIZE).toInt()
    }

    private fun sidx(idx: Long): Int {
        return (idx / SEGMENT_SIZE).toInt()
    }
}

private class Segment(val num: Int) {
    val elements = atomicArrayOfNulls<Any?>(SEGMENT_SIZE)
    val next: AtomicRef<Segment?> = atomic(null)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

//class MSQueue<E> {
//    private val head: AtomicRef<Node<E>>
//    private val tail: AtomicRef<Node<E>>
//
//    init {
//        val dummy = Node<E>(null)
//        head = atomic(dummy)
//        tail = atomic(dummy)
//    }
//
//    /**
//     * Adds the specified element [x] to the queue.
//     */
//    fun enqueue(x: E) {
//        val node = Node(x)
//
//        while (true) {
//            val curTail = tail.value
//            if (curTail.next.compareAndSet(null, node)) {
//                tail.compareAndSet(curTail, node)
//                return
//            } else {
//                curTail.next.value?.let { tail.compareAndSet(curTail, it) }
//            }
//        }
//    }
//
//    /**
//     * Retrieves the first element from the queue
//     * and returns it; returns `null` if the queue
//     * is empty
//     */
//    fun dequeue(): E? {
//        while (true) {
//            val curHead = head.value;
//
//            val next = curHead.next.value ?: return null;
//            if (head.compareAndSet(curHead, next)) {
//                return next.x;
//            }
//        }
//    }
//
//    fun isEmpty(): Boolean {
//        return head.value.next.value == null
//    }
//}