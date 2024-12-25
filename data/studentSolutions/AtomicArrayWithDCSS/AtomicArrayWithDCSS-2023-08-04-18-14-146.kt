@file:Suppress("IfThenToSafeAccess", "DuplicatedCode")

//package day3

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class AtomicArrayWithDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = Array(size) { Cell(initialValue) }

    fun get(index: Int): E? {
        val cell = array[index]

        val currentDescriptor = cell.descriptorRef.value
        if (currentDescriptor != null) {
            if (currentDescriptor.statusRef.value == Status.SUCCESS)
                return currentDescriptor.update1
        }

        return cell.valueRef.value
    }

    fun cas(index: Int, expected: E?, update: E?): Boolean {
        val cell = array[index]

        while (true) {
            val descriptor = cell.descriptorRef.value ?: break
            descriptor.proceed()
        }

        return cell.valueRef.compareAndSet(expected, update)
    }

    fun dcss(
        index1: Int, expected1: E?, update1: E?,
        index2: Int, expected2: E?
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = DCSSDescriptor(index1, expected1, update1, index2, expected2)
        descriptor.apply()
        return descriptor.statusRef.value == Status.SUCCESS
    }

    private inner class Cell(initialValue: E?) {
        val valueRef = atomic(initialValue)
        val descriptorRef = atomic<DCSSDescriptor?>(null)
    }

    private inner class DCSSDescriptor(
        index1: Int,
        private val expected1: E?,
        val update1: E?,
        private val index2: Int,
        private val expected2: E?
    ) {
        val statusRef = atomic(Status.UNDECIDED)

        private val cell1 = array[index1]

        fun apply() {
            while (true) {
                if (cell1.descriptorRef.compareAndSet(null, this))
                    break

                val existingDescriptor = cell1.descriptorRef.value
                existingDescriptor?.proceed()
            }

            proceed()
        }

        fun proceed() {
            while (true) {
                if (cell1.descriptorRef.value !== this)
                    return

                when (statusRef.value) {
                    Status.SUCCESS -> return handleSuccess()
                    Status.FAILED -> return uninstall()
                    Status.UNDECIDED -> Unit
                }

                val existing1 = cell1.valueRef.value
                val existing2 = get(index2)

                if (existing1 != cell1.valueRef.value)
                    continue

                val newStatus = if (existing1 == expected1 && existing2 == expected2) Status.SUCCESS else Status.FAILED
                statusRef.compareAndSet(Status.UNDECIDED, newStatus)

                return if (statusRef.value == Status.SUCCESS)
                    handleSuccess()
                else
                    uninstall()
            }
        }

        private fun handleSuccess() {
            cell1.valueRef.compareAndSet(expected1, update1)
            uninstall()
        }

        private fun uninstall() {
            cell1.descriptorRef.compareAndSet(this, null)
        }
    }

    private enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}