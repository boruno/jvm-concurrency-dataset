//package day2

import java.util.concurrent.atomic.*
import kotlin.math.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = enqIdx.incrementAndGet()
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            infiniteArray.compareAndSet(i.toInt(), null, element)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
        while (true) {
            var enqI: Long
            var deqI: Long
            do {
                enqI = enqIdx.get()
                deqI = deqIdx.get()
            } while (enqI != enqIdx.get())
            if (enqI <= deqI) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = deqIdx.incrementAndGet()
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            return infiniteArray.get(i.toInt()) as E
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
