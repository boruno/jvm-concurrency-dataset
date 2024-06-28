package mpp.faaqueue

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

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true){
            var cur_tail = tail.value
            val i = enqIdx.addAndGet(1)
//            if ((i % SEGMENT_SIZE).toInt() == 0){
//                val new_tail = Segment()
//                tail.value.next = new_tail
//                tail.compareAndSet(cur_tail.value, new_tail)
//            }
//            var qtyNext = i / SEGMENT_SIZE - cur_tail.value.Id
            while (tail.value.Id < i / SEGMENT_SIZE){
                if (tail.value.next == null){
                    val new_tail = Segment()
                    new_tail.Id = tail.value.Id + 1
                    tail.value.next = new_tail
                    tail.compareAndSet(cur_tail, new_tail)
                    cur_tail = tail.value
                } else {
                    tail.compareAndSet(cur_tail, tail.value.next!!)
                    cur_tail = tail.value
                }
            }
            if (tail.value.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)){
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true){
            if (deqIdx.value >= enqIdx.value){
                return null
            }
            var cur_head = head.value
            val i = deqIdx.addAndGet(1)
//            if ((i % SEGMENT_SIZE).toInt() == 0){
//                if (cur_head.value.next == null){
////                    println(i)
////                    println(enqIdx.value)
//                    deqIdx.decrementAndGet()
//                    return null
//                }
//                head.compareAndSet(cur_head.value, cur_head.value.next!!)
//            }

            while (head.value.Id < i / SEGMENT_SIZE){
                head.compareAndSet(cur_head, head.value.next!!)
                cur_head = head.value
            }

            if (head.value.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, -1)){
                continue
            }
            return head.value.elements[(i % SEGMENT_SIZE).toInt()].value as E?
        }

    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            if (deqIdx.value >= enqIdx.value){
                return true
            }
            return false
        }
}

private class Segment {
    var Id: Int = 0
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

fun main(){
    var q = FAAQueue<Int>()
    q.enqueue(1)
    q.enqueue(2)
    println(q.dequeue())
    q.enqueue(3)
    q.enqueue(4)
    q.enqueue(5)
    q.enqueue(6)
    println(q.dequeue())
    println(q.dequeue())
    println(q.dequeue())
    println(q.dequeue())
    println(q.dequeue())
    println(q.dequeue())
}
