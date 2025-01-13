@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        val cell = array[index]
        return when (cell) {
            is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor -> cell.getElementAt(index)
            else -> cell
        } as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
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
        private val expected1: E,
        private val update1: E,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        private val worklist
            get() = listOf(
                Triple(index1, expected1, update1),
                Triple(index2, expected2, update2)
            ).sortedBy { (idx, _, _) -> idx }

        fun apply() {
            install()
            val success = applyLogically()
            applyPhysically(success)
        }

        private fun install() {
            outer@ for ((idx, exp, _) in worklist) {
                inner@ while (true) {
                    if (status.get() != UNDECIDED) {
                        break@outer
                    }

                    val cur = array.compareAndExchange(idx, exp, this)
                    if (cur === exp) {
                        // it's expected value, CAS2 continues
                        continue@outer
                    } else if (cur === this) {
                        // somebody helped us
                        continue@outer
                    } else if (cur is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
                        // let's help somebody else
                        cur.apply()
                        continue@inner
                    } else {
                        // it's unexpected value, CAS2 is failed
                        status.compareAndSet(UNDECIDED, FAILED)
                        break@outer
                    }
                }
            }
        }

        private fun applyLogically(): Boolean {
            return status.compareAndSet(UNDECIDED, SUCCESS)
        }

        private fun applyPhysically(success: Boolean) {
            for ((idx, exp, upd) in worklist) {
                array.compareAndSet(idx, this, if (success) upd else exp)
            }
        }

        fun getElementAt(index: Int): Any {
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