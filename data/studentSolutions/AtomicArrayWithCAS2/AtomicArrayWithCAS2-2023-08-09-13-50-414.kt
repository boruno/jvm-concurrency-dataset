@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

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
        val cell = array[index]
        return when (cell) {
            is AtomicArrayWithCAS2<*>.CAS2Descriptor ->
                (cell as AtomicArrayWithCAS2<E>.CAS2Descriptor).getElementAt(index)
            else -> cell as E?
        }
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        // TODO: the cell can store a descriptor
        return array.compareAndSet(index, expected, update)
    }

    fun cas2(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?, update2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
    inner class CAS2Descriptor(
        private val index1: Int,
        private val expected1: E?,
        private val update1: E?,
        private val index2: Int,
        private val expected2: E?,
        private val update2: E?
    ) {
        val status = AtomicReference(UNDECIDED)

        private val worklist
            get() = listOf(
                Triple(index1, expected1, update1),
                Triple(index2, expected2, update2)
            ).sortedBy { (idx, _, _) -> idx }

        fun apply() {
            val installed = install()
            applyLogically(installed)
            applyPhysically()
        }

        private fun install(): Boolean {
            return worklist.all { (idx, exp, _) -> installOne(idx, exp) }
        }

        private fun installOne(idx: Int, exp: E?): Boolean {
            while (true) {
                when (status.get()) {
                    SUCCESS -> return true
                    FAILED -> return false
                    UNDECIDED -> Unit
                }

                val cur = array.compareAndExchange(idx, exp, this)
                if (cur === exp) {
                    // it's expected value, CAS2 continues
                    return true
                } else if (cur === this) {
                    // somebody helped us
                    return true
                } else if (cur is AtomicArrayWithCAS2<*>.CAS2Descriptor) {
                    // let's help somebody else
                    cur.apply()
                    // and try one more time
                } else {
                    // it's unexpected value, CAS2 is failed
                    return false
                }
            }
        }

        private fun applyLogically(installed: Boolean) {
            status.compareAndSet(UNDECIDED, if (installed) SUCCESS else FAILED)
        }

        private fun applyPhysically() {
            val success = when (status.get()) {
                SUCCESS -> true
                FAILED -> false
                UNDECIDED -> throw IllegalStateException()
            }
            for ((idx, exp, upd) in worklist) {
                array.compareAndSet(idx, this, if (success) upd else exp)
            }
        }

        fun getElementAt(index: Int): E? {
            val (_, exp, upd) = worklist.find { it.first == index }!!
            return when (status.get()) {
                UNDECIDED, FAILED -> exp
                SUCCESS -> upd
                else -> throw IllegalStateException()
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}