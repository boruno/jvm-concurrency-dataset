package day2

import day1.*
import java.util.concurrent.atomic.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            if (infiniteArray.compareAndSet(i.toInt(), null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (deqIdx.get() >= enqIdx.get()) return null
            val i = deqIdx.getAndIncrement()
            val value = infiniteArray.getAndSet(i.toInt(), POISONED)
            if (value != null) return value as E
        }
    }

    override fun validate() {
        check(enqIdx.get() >= deqIdx.get()) {
            "The \"enqIdx >= deqIdx\" invariant is violated at the end of the execution"
        }
        for (i in 0 until deqIdx.get().toInt()) {
            check(infiniteArray[i] == null) {
                "`infiniteArray[i]` must be `null` with `deqIdx = ${deqIdx.get()}` at the end of the execution"
            }
        }
        for (i in enqIdx.get().toInt() until infiniteArray.length()) {
            check(infiniteArray[i] == null) {
                "`infiniteArray[i]` must be `null` with `enqIdx = ${enqIdx.get()}` at the end of the execution"
            }
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
