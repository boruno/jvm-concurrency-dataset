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

    private fun findSegment(start: Segment, id: Long): Segment {
        var cur_segment = start
        var found_segment: Segment? = null

        while (true) {
            val next_segment = cur_segment.next.value

            if (next_segment != null) {
                cur_segment = next_segment
                if (cur_segment.id.value == id) {
                    found_segment = cur_segment
                    break
                }
            }
            else {
                break
            }
        }

        if (found_segment != null) {
            return found_segment
        }
        else {
            return Segment(id)
        }
    }

    fun enqueue(element: E) {
        while (true) {
            val cur_tail = tail.value
            val i = enqIdx.getAndAdd(1)

            val s = findSegment(cur_tail, i / SEGMENT_SIZE)

            val is_moved = moveTailForward(s)
            if (!is_moved) {
                continue
            }

            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null

            val cur_head = head.value
            val i = deqIdx.getAndAdd(1)

            val s = findSegment(cur_head, i / SEGMENT_SIZE)

            val is_moved = moveHeadForward(s)
            if (!is_moved) {
                continue
            }

            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, "Broken element")) {
                continue
            }


            val res = s.elements[(i % SEGMENT_SIZE).toInt()].value
            return res as E
        }
    }

    private fun moveTailForward(s: Segment): Boolean {
        val cur_tail = tail.value
        if (s.id.value > cur_tail.id.value) {
            return tail.compareAndSet(cur_tail, s)
        }
        return false
    }

    private fun moveHeadForward(s: Segment): Boolean {
        val cur_head = head.value
        if (s.id.value > cur_head.id.value) {
            return head.compareAndSet(cur_head, s)
        }
        return false
    }

    /**
     * Returns true if this queue is empty, or false otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }
}

private class Segment(id: Long) {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val id = atomic(id)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

//package mpp.faaqueue
//
//import kotlinx.atomicfu.*
//
//class FAAQueue<E> {
//    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
//    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
//    private val enqIdx = atomic(0L)
//    private val deqIdx = atomic(0L)
//
//    init {
//        val firstNode = Segment()
//        head = atomic(firstNode)
//        tail = atomic(firstNode)
//    }
//
//    private fun findSegmentEnqueque(tail: Segment, id: Long): Segment? {
////        var count = 0;
////        var cur_head = head.value;
////        while (cur_head != tail) {
////            val cur_head_next = cur_head.next
////            if (cur_head_next.value == null) break
////            count++;
////            cur_head = cur_head_next.value!!;
////        }
////        if (count < id) {
////            return Segment()
////        }
////        else {
////            return null
////        }
//        var cur_head = head.value
//        var cur_head_next = cur_head.next.value
//        repeat(id.toInt()) {
//            if (cur_head_next == null) {
//                return Segment()
//            }
//            cur_head = cur_head_next as Segment
//            cur_head_next = cur_head.next.value
//        }
//        return null
//    }
//
//    private fun moveTailForward(s: Segment?) {
////        if (s == null) return
////        tail.value.next = s
////        tail.value = s
//    }
//
//    private fun findSegmentDequeque(head: Segment, id: Long): Segment? {
//        var count = 0L;
//        var cur_head = head;
//        while (count < id) {
//            if (cur_head.next.value == null) return null
//            count++;
//            cur_head = cur_head.next.value!!
//        }
//        return cur_head
//    }
//
//    private fun moveHeadForward(s: Segment?) {
//        if (s == null) return
//        head.value = s
//    }
//
//    @Suppress("UNCHECKED_CAST")
//    private fun getElement(i: Long): E {
//        var id = i / SEGMENT_SIZE
//        var cur_head = head.value
//        for (j in 0 until id) {
//            cur_head = cur_head.next.value!!
//        }
//        return cur_head.elements[(i % SEGMENT_SIZE).toInt()].value as E
//    }
//
//    /**
//     * Adds the specified element [x] to the queue.
//     */
//    fun enqueue(element: E) {
////        val i = enqIdx.getAndAdd(1)
//        while (true) {
//            val cur_tail = tail.value
//            val i = enqIdx.getAndAdd(1)
//            val s = findSegmentEnqueque(cur_tail, i / SEGMENT_SIZE)
//
////            moveTailForward(s)
//            if (!tail.value.next.compareAndSet(null, s)) {
//                tail.value.next.value?.let { tail.compareAndSet(cur_tail, it) }
//                continue
//            }
//            tail.value.next.value?.let { tail.compareAndSet(cur_tail, it) }
//            if (s != null) {
//                if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
//                    return
//                }
//            }
//        }
//    }
//
//    /**
//     * Retrieves the first element from the queue and returns it;
//     * returns `null` if the queue is empty.
//     */
//
//    @Suppress("UNCHECKED_CAST")
//    fun dequeue(): E? {
//        while (true) {
//            if (deqIdx.value >= enqIdx.value) return null
//            var cur_head = head.value
//            var i = deqIdx.getAndAdd(1)
//            var s = findSegmentDequeque(cur_head, i / SEGMENT_SIZE)
////            moveHeadForward(s)
//
//            if (s != null) {
//                val res_index = (i % SEGMENT_SIZE).toInt()
//                val res = s.elements[res_index]
//                if (res.compareAndSet(null, "Broken element") || res.value == "Broken element") {
//                    continue
//                }
//                return res.value as E?
//            }
//        }
//    }
//
//    /**
//     * Returns `true` if this queue is empty, or `false` otherwise.
//     */
//    val isEmpty: Boolean
//        get() {
//            return deqIdx.value >= enqIdx.value
//        }
//}
//
//private class Segment {
//    // var next: Segment? = null
//    val next = atomic<Segment?>(null)
//    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
//
//    private fun get(i: Int) = elements[i].value
//    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
//    private fun put(i: Int, value: Any?) {
//        elements[i].value = value
//    }
//}
//
//const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
//
