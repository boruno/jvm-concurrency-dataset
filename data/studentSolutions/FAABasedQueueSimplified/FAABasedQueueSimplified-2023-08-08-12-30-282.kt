//package day2

import java.util.concurrent.atomic.*
import kotlin.math.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {

        while (true) {
            val oldIndex = enqIdx.getAndIncrement()
            if (infiniteArray.compareAndSet(oldIndex.toInt(), null, element)) {
                return
            }
        }

//        // TODO: Increment the counter atomically via Fetch-and-Add.
//        // TODO: Use `getAndIncrement()` function for that.
//        val i = enqIdx.get()
//        enqIdx.set(i + 1)
//        // TODO: Atomically install the element into the cell
//        // TODO: if the cell is not poisoned.
//        infiniteArray.set(i.toInt(), element)
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {


        while (true) {

            var enqIndSnapshot = 0L
            var deqIndSnapshot = 0L
            while (true) {
                enqIndSnapshot = enqIdx.get()
                deqIndSnapshot = deqIdx.get()
                if (enqIndSnapshot == enqIdx.get()) break
            }
            if (enqIndSnapshot <= deqIndSnapshot) return null



//        // Is this queue empty?
//        if (enqIdx.get() <= deqIdx.get()) return null



            val oldIndex = deqIdx.getAndIncrement()
            if (!infiniteArray.compareAndSet(oldIndex.toInt(), null, POISONED)) {
                val r = infiniteArray[oldIndex.toInt()] as E
                infiniteArray[oldIndex.toInt()] = null
                return r
            }
        }
//
//        // TODO: Increment the counter atomically via Fetch-and-Add.
//        // TODO: Use `getAndIncrement()` function for that.
//        val i = deqIdx.get()
//        deqIdx.set(i + 1)
//        // TODO: Try to retrieve an element if the cell contains an
//        // TODO: element, poisoning the cell if it is empty.
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
