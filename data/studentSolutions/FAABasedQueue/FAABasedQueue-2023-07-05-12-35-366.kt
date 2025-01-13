//package day2

import kotlinx.atomicfu.*

class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    private val head: AtomicRef<Segment>
    private val tail: AtomicRef<Segment>

    init {
        val dummy = Segment(-1)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            val node = findSegment(tail.value, i / BLOCK_SIZE)
            moveTail()
            val segIdx = i % BLOCK_SIZE
            if (node.array[segIdx].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!shouldTryDeque()) return null
            val i = deqIdx.getAndIncrement()
            val node = findSegment(head.value, i / BLOCK_SIZE)
            moveHead()
            val segIdx = i % BLOCK_SIZE
            if (node.array[segIdx].compareAndSet(null, POISONED)) continue
            return node.array[segIdx].value as E
        }
    }

    private fun shouldTryDeque(): Boolean {
        while (true) {
            val curDeq = deqIdx.value
            val curEnq = enqIdx.value
            if (curDeq != deqIdx.value) continue
            return curEnq > curDeq
        }
    }

    private fun findSegment(start: Segment, index: Int): Segment {
        var current: Segment = start
        while (true) {
            if (current.index == index) return current
            var next = current.next.value
            while (next == null) {
                val newNode = Segment(current.index + 1)
                if (current.next.compareAndSet(next, newNode)) {
                    next = newNode
                    break
                }
            }
            current = next!!
        }
    }

    private fun moveHead() {
        val index = deqIdx.value / BLOCK_SIZE
        while (true) {
            val node = head.value
            val next = node.next.value
            if (next == null || next.index > index) break
            head.compareAndSet(node, next)
        }
    }

    private fun moveTail() {
        val index = enqIdx.value / BLOCK_SIZE
        while (true) {
            val node = tail.value
            val next = node.next.value
            if (next == null || next.index > index) break
            tail.compareAndSet(node, next)
        }
    }

    private class Segment(val index: Int) {
        val array = atomicArrayOfNulls<Any>(BLOCK_SIZE)

        val next = atomic<Segment?>(null)
    }
}

private const val BLOCK_SIZE = 15

private val POISONED = Any()
