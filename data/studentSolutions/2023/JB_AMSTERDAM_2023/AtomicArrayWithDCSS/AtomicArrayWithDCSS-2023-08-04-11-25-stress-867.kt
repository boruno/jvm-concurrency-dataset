@file:Suppress("IfThenToSafeAccess", "DuplicatedCode")

package day3

import day3.AtomicArrayWithDCSS.Status.*
import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = Array(size) { Cell(initialValue) }

    fun get(index: Int): E? {
        val cell = array[index]

        val currentDescriptor = cell.descriptor.value
        val currentDescriptorStatus = if (currentDescriptor != null) currentDescriptor.status.value else null

        val cellValue = cell.value.value

        return if (currentDescriptor != null && currentDescriptorStatus == SUCCESS && currentDescriptor.index1 == index)
            currentDescriptor.update1
        else
            cellValue
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        val descriptor = DCSSDescriptor(index, expected, update, index, expected)
        descriptor.apply()
        return descriptor.status.value == SUCCESS


//        val cell = array[index]
//
//        while (true) {
//            val currentDescriptor = cell.descriptor.value
//            if (currentDescriptor != null) {
//                currentDescriptor.apply()
//            } else
//                break
//        }
//
//        return cell.value.compareAndSet(expected, update)
//
//
//        // TODO: the cell can store a descriptor
//        return array[index].value.compareAndSet(expected, update)
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
//        // TODO This implementation is not linearizable!
//        // TODO Store a DCSS descriptor in array[index1].
//        if (array[index1].value.value != expected1 || array[index2].value.value != expected2) return false
//        array[index1].value.value = update1
//        return true
        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        descriptor.apply()
        return descriptor.status.value == SUCCESS
    }

    inner class Cell(initialValue: E) {
        val value = atomic<E?>(initialValue)
        val descriptor = atomic<DCSSDescriptor?>(null)
    }

    inner class DCSSDescriptor(
        val index1: Int, val expected1: E?, val update1: E?,
        val index2: Int, val expected2: E?
    ) {
        val status = atomic(UNDECIDED)

        private val cell1 = array[index1]

        fun apply() {
            while (true) {
                when (status.value) {
                    SUCCESS -> return handleSuccess()
                    FAILED -> return handleFailed()
                    UNDECIDED -> Unit
                }

                if (!cell1.descriptor.compareAndSet(null, this)) {
                    val existingDescriptor = cell1.descriptor.value
                    if (existingDescriptor === this)
                        break

                    existingDescriptor?.apply()
                } else
                    break
            }

            handleUndecided()
        }

        private fun handleSuccess() {
            if (cell1.descriptor.value === this)
                cell1.value.compareAndSet(expected1, update1)

            uninstall()
        }

        private fun uninstall() {
            cell1.descriptor.compareAndSet(this, null)
        }

        private fun handleUndecided() {
            val hasExpectedValues = cell1.value.value == expected1 && get(index2) == expected2

            when (status.value) {
                UNDECIDED -> {
                    if (hasExpectedValues) {
                        status.value = SUCCESS
                        handleSuccess()
                    } else {
                        status.value = FAILED
                        handleFailed()
                    }
                }

                SUCCESS -> handleSuccess()
                FAILED -> handleFailed()
            }
        }

        private fun handleFailed() {
            uninstall()
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}