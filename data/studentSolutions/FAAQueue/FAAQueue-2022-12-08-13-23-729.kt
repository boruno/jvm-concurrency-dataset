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
            while (cur_tail.Id < i / SEGMENT_SIZE){
                if (cur_tail.next == null){
                    val new_tail = Segment()
                    new_tail.Id = cur_tail.Id + 1
                    cur_tail.next = new_tail
                    tail.compareAndSet(cur_tail, new_tail)
                    cur_tail = tail.value
                } else {
                    tail.compareAndSet(cur_tail, cur_tail.next!!)
                    cur_tail = cur_tail.next!!
                }
            }
            if (cur_tail.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)){
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
            while (cur_head.Id < i / SEGMENT_SIZE){
                if (cur_head.next == null){
                    deqIdx.decrementAndGet()
                    return null
                }
                head.compareAndSet(cur_head, cur_head.next!!)
                cur_head = cur_head.next!!
            }
            if (cur_head.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, 123)){
                continue
            }
            return cur_head.elements[(i % SEGMENT_SIZE).toInt()].value as E?
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
    for (j in 1..10000){
        var q = FAAQueue<Int>()
        val numbers = Array<Int?>(6, {0})
        q.enqueue(2)
        q.enqueue(6)
        numbers[0] = q.dequeue()
        q.enqueue(-6)
        Thread {  numbers[1] = q.dequeue(); q.enqueue(4); numbers[4] = q.dequeue()}.start()
        Thread { q.enqueue(1); numbers[2] = q.dequeue()}.start()
        Thread.sleep(100)
        numbers[3] = q.dequeue()
        if (numbers[3] == null){
            println("error")
        }
    }
    Thread.sleep(1000)
}
