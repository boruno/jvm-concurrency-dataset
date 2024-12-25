//package day2

import day1.*
import java.util.concurrent.atomic.*
import kotlin.math.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqueueIndex = AtomicLong(0)
    private val dequeueIndex = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            val index = enqueueIndex.getAndIncrement().toInt()
            if (infiniteArray.compareAndSet(index, null, element))
                return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (shouldTryToDequeue()) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = dequeueIndex.getAndIncrement().toInt()
            if (infiniteArray.compareAndSet(i, null, POISONED)) continue
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            return infiniteArray[i] as E
        }
    }

    private fun shouldTryToDequeue(): Boolean =
        dequeueIndex.doubleCollect(enqueueIndex) { dequeueIndex, enqueueIndex -> dequeueIndex >= enqueueIndex }

    override fun validate() {
        for (i in 0 until min(dequeueIndex.get().toInt(), enqueueIndex.get().toInt())) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with `dequeueIndex = ${dequeueIndex.get()}` at the end of the execution"
            }
        }
        for (i in max(dequeueIndex.get().toInt(), enqueueIndex.get().toInt()) until infiniteArray.length()) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with enqueueIndex = ${enqueueIndex.get()}` at the end of the execution"
            }
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
