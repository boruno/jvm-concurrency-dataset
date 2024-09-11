package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {


    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)
    private val head: AtomicRef<Segment<E>>
    private val tail: AtomicRef<Segment<E>>

    init {
        val dummy = Segment<E>()
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {

            val currTail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(currTail, i / SEGM_SIZE)
            moveTailForward(s)

            val subIndex = i % SEGM_SIZE
            val cell = s.subArray[subIndex]
            if (cell.compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            val deq = deqIdx.value
            val enq = enqIdx.value

            if (enq <= deq) return null

            val currHed = head.value
            val i = deqIdx.getAndIncrement();
            val s = findSegment(currHed, i / SEGM_SIZE)
            moveHeadForward(s)

            val subIndex = i % SEGM_SIZE
            val cell = s.subArray[subIndex]
            if (!cell.compareAndSet(null, POISONED))
                return cell.value as E
        }
    }

    private fun findSegment(start: Segment<E>, id: Int): Segment<E> {
        var current = start
        for (i in 0..id) {
            current = findOrEnqueue(current)
        }
        return current
    }

    private fun findOrEnqueue(current: Segment<E>): Segment<E> {
        while (true){
            val next = current.next.value
            if (next != null)
                return next

            val node = Segment<E>()
            if (current.next.compareAndSet(null, node)){
                tail.compareAndSet(current, node)
                return node
            }
            else
                tail.compareAndSet(current, current.next.value!!)
        }
    }

    private fun moveTailForward(s: Segment<E>) {

    }

    private fun moveHeadForward(s: Segment<E>){

    }

    private class Segment<E> {
        val subArray = atomicArrayOfNulls<Any?>(SEGM_SIZE)
        val next = atomic<Segment<E>?>(null)
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
private val SEGM_SIZE = 16


