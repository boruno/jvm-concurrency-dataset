//package day2

import java.util.concurrent.atomic.*
import kotlin.math.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            // TODO: Increment the counter atomically via Fetch-and-Add. Use `getAndIncrement()` function for that.
            val i = enqIdx.getAndIncrement()
            // TODO: Atomically install the element into the cell if the cell is not poisoned.
            if (infiniteArray.compareAndSet(i.toInt(), null, element))
                return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (enqIdx.get() <= deqIdx.get()) return null //todo(vg): improve condition later via shouldTryToDequeue
            // TODO: Increment the counter atomically via Fetch-and-Add. Use `getAndIncrement()` function for that.
            val i = deqIdx.getAndIncrement()
            // TODO: Try to retrieve an element if the cell contains an element, poisoning the cell if it is empty.
            if (!infiniteArray.compareAndSet(i.toInt(), null, POISONED)) {
                val result = infiniteArray.get(i.toInt()) as E
                infiniteArray.set(i.toInt(), null)
                return result
            }
        }
    }

    override fun validate() {
        for (i in 0 until min(deqIdx.get().toInt(), enqIdx.get().toInt())) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with `deqIdx = ${deqIdx.get()}` at the end of the execution"
            }
        }
        for (i in max(deqIdx.get().toInt(), enqIdx.get().toInt()) until infiniteArray.length()) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with `enqIdx = ${enqIdx.get()}` at the end of the execution"
            }
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
