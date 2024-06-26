@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2AndImplementedDCSS.Status.*
import java.util.concurrent.atomic.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2AndImplementedDCSS<E : Any>(size: Int, initialValue: E) {
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
            is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor ->
                (cell as AtomicArrayWithCAS2AndImplementedDCSS<E>.CAS2Descriptor).getElementAt(index)
            else -> cell as E?
        }
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

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
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

                when (val cur = array.get(idx)) {
                    this ->
                        // somebody helped us
                        return true

                    is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor ->
                        // let's help somebody else, and try one more time
                        cur.apply()

                    exp ->
                        // it's expected value, try to install
                        //TODO("do only if status is UNDECIDED")
                        if (array.compareAndSet(idx, exp, this)) {
                            return true
                        }

                    else ->
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

    // TODO: Please use this DCSS implementation to ensure that
    // TODO: the status is `UNDECIDED` when installing the descriptor.
    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?
    ): Boolean =
        if (array[index] == expectedCellState && statusReference.get() == expectedStatus) {
            array[index] = updateCellState
            true
        } else {
            false
        }
}