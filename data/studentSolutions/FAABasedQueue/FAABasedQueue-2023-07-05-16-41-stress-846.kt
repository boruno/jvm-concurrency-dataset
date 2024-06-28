package day2

import day1.*
import kotlinx.atomicfu.*

//class InfiniteArray<E>() {
//    private val sectionSize: Int = 15
//    private val head: AtomicRef<Section<E>>
//    private val tail: AtomicRef<Section<E>>
//
//    init {
//        val dummy = Section<E>(0)
//        head = atomic(dummy)
//        tail = atomic(dummy)
//    }
//
//    private inner class Section<E>(val index: Int) {
//        val array = atomicArrayOfNulls<E?>(sectionSize)
//        val next = atomic<Section<E>?>(null)
//    }
//
//    private fun enqueue(section: Section<E>) {
//        while (true) {
//            val curTail = tail.value
//            if (curTail.next.compareAndSet(null, section)) {
//                tail.compareAndSet(curTail, section)
//                return
//            } else {
//                tail.compareAndSet(curTail, curTail.next.value!!)
//            }
//        }
//    }
//
//    private  fun dequeue() {
//        while (true) {
//            val curHead = head.value
//            val curHeadNext = curHead.next.value
//            if (curHeadNext == null) {
//                return
//            }
//            if (head.compareAndSet(curHead, curHeadNext)) {
//                return
//            }
//        }
//    }
//
//    private fun getSectionIfExists(sectionIndex: Int): Section<E>? {
//        var section: Section<E>? = head.value
//        while (section != null && section.index < sectionIndex) {
//            section = section.next.value
//        }
//        return section
//    }
//
//    fun getForEnqueue(index: Int): AtomicRef<E?> {
//        val sectionIndex = index / sectionSize
//        val indexInSection = index % sectionSize
//        val section = getSectionIfExists(sectionIndex) ?: run {
//            val newSection = Section<E>(sectionIndex)
//            enqueue(newSection)
//            newSection
//        }
//        return section.array[indexInSection]
//    }
//
//    fun getForDequeue(index: Int): AtomicRef<E?> {
//        val sectionIndex = index / sectionSize
//        val indexInSection = index % sectionSize
//        val section = getSectionIfExists(sectionIndex)!!
//        return section.array[indexInSection]
//    }
//}

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
//    private val infiniteArray = InfiniteArray<Any?>()
private val sectionSize: Int = 1
    private val head: AtomicRef<Section>
    private val tail: AtomicRef<Section>

    init {
        val dummy = Section(0)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private inner class Section(val index: Int) {
        val array = atomicArrayOfNulls<Any?>(sectionSize)
        val next = atomic<Section?>(null)
    }

    private fun enqueueSection(section: Section) {
        while (true) {
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, section)) {
                tail.compareAndSet(curTail, section)
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
            }
        }
    }

    private fun dequeueSection() {
        while (true) {
            val curHead = head.value
            val curHeadNext = curHead.next.value
            if (curHeadNext == null) {
                return
            }
            if (head.compareAndSet(curHead, curHeadNext)) {
                return
            }
        }
    }

    private fun getSectionIfExists(sectionIndex: Int): Section? {
        var section: Section? = head.value
        while (section != null && section.index < sectionIndex) {
            section = section.next.value
        }
        return section
    }

    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            val sectionIndex = i / sectionSize
            val indexInSection = i % sectionSize
            val section = getSectionIfExists(sectionIndex) ?: run {
                val newSection = Section(sectionIndex)
                enqueueSection(newSection)
                newSection
            }
            val elementRef = section.array[indexInSection]
            if (elementRef.compareAndSet(null, element)) return
        }
    }

    fun shouldTryDequeue(): Boolean {
        while (true) {
            val currDeq = deqIdx.value
            val currEnq = enqIdx.value
            if (currDeq == deqIdx.value) {
                return currEnq > currDeq
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!shouldTryDequeue()) return null
            val i = deqIdx.getAndIncrement()
            val sectionIndex = i / sectionSize
            val indexInSection = i % sectionSize
            val section = getSectionIfExists(sectionIndex)!!
            val elementRef = section.array[indexInSection]
            if (elementRef.compareAndSet(null, POISONED)) continue
            return elementRef.value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()