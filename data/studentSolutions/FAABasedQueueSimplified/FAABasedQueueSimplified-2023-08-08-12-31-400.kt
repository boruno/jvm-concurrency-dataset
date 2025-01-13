//package day2

import java.util.concurrent.atomic.*
import kotlin.math.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        while (true) {
            val i = enqIdx.getAndIncrement().toInt()
//        val i = enqIdx.get()
//        enqIdx.set(i + 1)
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
//        infiniteArray.set(i.toInt(), element)
//            if (infiniteArray[i] == POISONED) continue
            if (infiniteArray.compareAndSet(i, null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (enqIdx.get() <= deqIdx.get()) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
//        val i = deqIdx.get()
//        deqIdx.set(i + 1)
            val i = deqIdx.getAndIncrement().toInt()
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
//            return infiniteArray.get(i.toInt()) as E
            if (infiniteArray.compareAndSet(i, null, POISONED)) continue
            val element = infiniteArray[i] as E
            if (!infiniteArray.compareAndSet(i, element, null)) continue
            return element
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
