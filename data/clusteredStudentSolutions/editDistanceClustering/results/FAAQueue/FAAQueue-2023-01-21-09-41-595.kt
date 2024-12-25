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
            return Segment()
        }
    }

    fun enqueue(element: E) {
        while (true) {
            val cur_tail = tail.value
            val cur_tail_enqIdx = cur_tail.enqIdx.getAndAdd(1)

            if (cur_tail_enqIdx > SEGMENT_SIZE - 1) {
                val new_segment = Segment()
                if (moveTailForward(new_segment)) {
                    return
                }
                new_segment.enqIdx.compareAndSet(0, 1)
                new_segment.elements[0].value = element

                val cas_result = cur_tail.next.compareAndSet(null, new_segment)
                var help_flag = false
                var success_flag = false

                if (cas_result) {
                    success_flag = true
                }
                if (!cas_result) {
                    help_flag = true
                }
                if (help_flag == true) {
                    val help_tail = cur_tail.next.value
                    tail.compareAndSet(cur_tail, help_tail!!)
                }
                if (success_flag) {
                    tail.compareAndSet(cur_tail, new_segment)
                    return
                }

                val i = enqIdx.getAndAdd(1)
                val s = findSegment(cur_tail, i / SEGMENT_SIZE)
                if (moveTailForward(s)) {
                    break
                }
                if (s.id.value == 1L) {
                    findSegment(cur_tail, i / SEGMENT_SIZE)
                }
            }
            if (cur_tail_enqIdx < SEGMENT_SIZE) {
                if (moveTailForward(cur_tail)) {
                    continue
                }
                if (cur_tail.elements[cur_tail_enqIdx].compareAndSet(null, element)) {
                    return
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun dequeue(): E? {
        while (true) {
            val cur_head = head.value

            if (cur_head.deqIdx.value > SEGMENT_SIZE - 1) {
                val next_segment = cur_head.next.value
                var help = false
                if (next_segment == null) {
                    if (this.isEmpty) {
                        return null
                    }
                }
                else {
                    help = true
                }
                if (help) {
                    head.compareAndSet(cur_head, next_segment!!)
                }
            }
            if (cur_head.deqIdx.value < SEGMENT_SIZE) {
                val cur_head_deqIdx = cur_head.deqIdx.getAndAdd(1)
                var cur_head_deqIdx_new = 0

                if (cur_head_deqIdx > SEGMENT_SIZE - 1) {
                    cur_head_deqIdx_new = cur_head.deqIdx.getAndAdd(1)
                    if (cur_head_deqIdx_new >= SEGMENT_SIZE) {
                        continue
                    }
                    else {
                        val next_segment = cur_head.next.value
                        var help = false
                        if (next_segment == null) {
                            if (this.isEmpty) {
                                return null
                            }
                        }
                        else {
                            help = true
                        }
                        if (help) {
                            head.compareAndSet(cur_head, next_segment!!)
                        }
                        continue
                    }
                }

                val result = cur_head.elements[cur_head_deqIdx].getAndSet("Broken element")
                moveHeadForward(Segment())
                if (result == null) {
                    cur_head_deqIdx_new += 1
                    continue
                }
                else {
                    return result as E
                }
            }


            val i = this.deqIdx.getAndAdd(1)
            val s = findSegment(cur_head, i / SEGMENT_SIZE)
            if (s.elements[0].value != null) {
                if (moveHeadForward(s)) {
                    return null
                }
                continue
            }
            continue
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
            while (true) {
                val current_head = head.value
                val next_segment = current_head.next.value
                val cur_head_deqIdx_value = current_head.deqIdx.value

                var help = false

                if (cur_head_deqIdx_value > SEGMENT_SIZE - 1) {
                    if (next_segment != null) {
                        help = true
                    }
                }

                if (help) {
                    head.compareAndSet(current_head, next_segment!!)
                    continue
                }

                if (next_segment == null && cur_head_deqIdx_value >= SEGMENT_SIZE) {
                    return true
                }

                if (moveTailForward(current_head)) {
                    return false
                }

            }
        }
}

private class Segment() {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val id = atomic(0L)
    val enqIdx = atomic(0)
    val deqIdx = atomic(0)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
