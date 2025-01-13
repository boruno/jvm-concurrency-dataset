//package day2

import java.util.concurrent.atomic.*
import kotlin.contracts.contract
import kotlin.math.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        // IN

        // i := FAA(endUdx, +1)
        // if cas(arr[i], null, element) return

        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
//        val i = enqIdx.get()
//        enqIdx.set(i + 1)
        val i = enqIdx.getAndIncrement()
        if (infiniteArray.compareAndSet(i.toInt(), null, element)) return
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.
//        infiniteArray.set(i.toInt(), element)
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // OUT

        // if (dexIdx.get() >= enqIdx.get()) return null
        // i = FAA(deqIdx, +1)
        // if cas(arr[i], null, shit) continue
        // return arr[i]

        // Is this queue empty?
        if (deqIdx.get() >= enqIdx.get()) return null
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
//        val i = deqIdx.get()
//        deqIdx.set(i + 1)
        val i = deqIdx.getAndIncrement()
        if (infiniteArray.compareAndSet(i.toInt(), null, POISONED)) return null

        return infiniteArray.getAndSet(i.toInt(), null) as E?
        // TODO: Try to retrieve an element if the cell contains an
        // TODO: element, poisoning the cell if it is empty.
//        return infiniteArray.get(i.toInt()) as E
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
