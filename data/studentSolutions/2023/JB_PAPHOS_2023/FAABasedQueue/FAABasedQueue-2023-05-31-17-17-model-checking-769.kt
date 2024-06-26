package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.LinkedList

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    internal class Segment<E> {
        val elements = atomicArrayOfNulls<E>(SEGMENT_SIZE)
        val enqIdx = atomic(0)
        val deqIdx = atomic(0)

        constructor()
        constructor(init: E?) {
            enqIdx.getAndIncrement()
            elements[0].compareAndSet(null, init)
        }

        val next = atomic<Segment<E>?>(null)
    }
    companion object {
        private const val SEGMENT_SIZE = 64
    }

    private val head: AtomicRef<Segment<E>>
    private val tail: AtomicRef<Segment<E>>

    init {
        val same = Segment<E>()
        head = atomic(same)
        tail = atomic(same)
    }

    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = curTail.enqIdx.getAndIncrement()
            if (i >= SEGMENT_SIZE) {
                val newNode = Segment(element)
                if (curTail.next.compareAndSet(null, newNode)) {
                    tail.compareAndSet(curTail, newNode)
                    return
                } else {
                    curTail.next.value?.let { tail.compareAndSet(curTail, it) }
                }
            } else {
                if (curTail.elements.get(i).compareAndSet(null, element)) return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
//            if (deqIdx.value > enqIdx.value) return null

            val curHead = head.value
            val i = curHead.deqIdx.getAndIncrement()
            if (i >= SEGMENT_SIZE) {
                val newHead = curHead.next.value ?: return null
                head.compareAndSet(curHead, newHead)
                continue
            }

            if (head.value.elements[i].compareAndSet(null, POISONED as E)) continue
            return curHead.elements[i].value
        }
    }
}

private val POISONED = Any()
