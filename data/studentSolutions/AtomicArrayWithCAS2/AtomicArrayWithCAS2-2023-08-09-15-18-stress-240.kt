@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2.Status.*
import java.util.concurrent.atomic.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E? {
        val state = array[index]
        return if (state is AtomicArrayWithCAS2<*>.CAS2Descriptor<*>) {
            getValue(state, index)
        } else if (state is AtomicArrayWithCAS2<*>.RDCSS<*>) {
            getValue(state.descriptor, index)
        } else {
            state as E
        }
    }

    private fun getValue(state: AtomicArrayWithCAS2<*>.CAS2Descriptor<*>, index: Int) =
        if (state.status.get() == SUCCESS) {
            state.getUpdate(index)
        } else {
            state.getExpected(index)
        } as E

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        val descriptor = CAS2Descriptor(Entry(index, expected!!, update!!))
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            Entry(index1, expected1!!, update1!!),
            Entry(index2, expected2!!, update2!!)
        )
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    data class Entry<E>(
        val index: Int,
        val expected: E,
        val update: E
    )

    inner class CAS2Descriptor<E>(vararg entries: Entry<E>) {
        val status = AtomicReference(UNDECIDED)

        private val entries = entries.sortedBy { it.index }.toTypedArray()

        fun apply() {
            val success = install()
            logically(success)
            physically()
        }

        private fun physically() {
            if (status.get() == SUCCESS) {
                entries.forEach { array.compareAndSet(it.index, this, it.update) }
            } else {
                entries.forEach { array.compareAndSet(it.index, this, it.expected) }
            }
        }

        private fun logically(success: Boolean) {
            status.compareAndSet(UNDECIDED, if (success) SUCCESS else FAILED)
        }

        private fun install(): Boolean {
            for (entry in entries) {
                if (!install(entry.index, entry.expected)) {
                    return false
                }
            }
            return true
        }

        private fun install(index: Int, expected: E): Boolean {
            while (true) {
                when (val state = array.get(index)) {
                    this -> return true
                    is AtomicArrayWithCAS2<*>.CAS2Descriptor<*> -> state.apply()
                    expected -> {
                        return setDescriptor(index, expected)
                    }

                    else -> return false
                }
            }
        }

        private fun setDescriptor(index: Int, expected: E?): Boolean {
            while (true) {
                when (val state = array.get(index)) {
                    is AtomicArrayWithCAS2<*>.RDCSS<*> -> state.apply()
                    expected -> {
                        val rdcss = RDCSS(index, expected, this)
                        if (array.compareAndSet(index, expected, rdcss)) {
                            return rdcss.apply()
                        }
                    }
                    else -> return false
                }
            }
        }

        fun getUpdate(index: Int): E {
            return entries.find { it.index == index }?.update ?: error("Unknown index $index")
        }

        fun getExpected(index: Int): E {
            return entries.find { it.index == index }?.expected ?: error("Unknown index $index")
        }
    }

    inner class RDCSS<E>(
        private val index: Int,
        private val expected: E?,
        val descriptor: AtomicArrayWithCAS2<*>.CAS2Descriptor<*>
    ) {

        fun apply(): Boolean {
            return if (descriptor.status.get() == UNDECIDED) {
                array.compareAndSet(index, this, descriptor)
                true
            } else {
                array.compareAndSet(index, this, expected)
                false
            }
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}