import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Descriptor<E>>(size)

    init {
        for (i in 0 until size) a[i].value =
            Descriptor(initialValue, initialValue, initialValue, initialValue, i, i, true)
    }

    fun get(index: Int): E {
        val descriptor = a[index].value
        return descriptor!!.getValueByIndex(index)
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        val newDescriptor = Descriptor(update, update, update, update, index, index, true)
        while (true) {
            val descriptor = getDescriptorAndHelp(index)
            a[index].compareAndSet(descriptor, newDescriptor)
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        val indexL: Int
        val expectedL: E
        val updateL: E
        val indexR: Int
        val expectedR: E
        val updateR: E
        if (index1 < index2) {
            indexL = index1
            expectedL = expected1
            updateL = update1
            indexR = index2
            expectedR = expected2
            updateR = update2
        } else {
            indexR = index1
            expectedR = expected1
            updateR = update1
            indexL = index2
            expectedL = expected2
            updateL = update2
        }
        val newDescriptor = Descriptor(expectedL, expectedR, updateL, updateR, indexL, indexR)
        val lDescriptor = getDescriptorAndHelp(indexL)
        val rDescriptor = getDescriptorAndHelp(indexR)
        if (lDescriptor.getValueByIndex(indexL) != expectedL || rDescriptor.getValueByIndex(indexR) != expectedR) {
            return false
        }
        if (!a[indexL].compareAndSet(lDescriptor, newDescriptor)) {
            return false
        }
        if (!a[indexR].compareAndSet(rDescriptor, newDescriptor) && a[indexR].value != newDescriptor) {
            return false
        }
        newDescriptor.active = true
        return true
    }

    private fun help(index: Int, descriptor: Descriptor<E>) {
        if (descriptor.active) return
        if (index == descriptor.indexL) {
            val rDescriptor = a[descriptor.indexR].value!!
            if (rDescriptor.getValueByIndex(descriptor.indexR) == descriptor.newR && a[descriptor.indexR].compareAndSet(
                    rDescriptor,
                    descriptor
                )
            ) {
                descriptor.active = true
            }
        }
    }

    private fun getDescriptorAndHelp(index: Int): Descriptor<E> {
        val descriptor = a[index].value!!
        help(index, descriptor)
        return descriptor
    }

}

class Descriptor<E>(
    val prevL: E,
    val prevR: E,
    val newL: E,
    val newR: E,
    val indexL: Int,
    val indexR: Int,
    var active: Boolean = false
) {
    val lValue get() = if (active) newL else prevL
    val rValue get() = if (active) newR else prevR

    fun getValueByIndex(index: Int) = if (index == indexL) lValue else rValue
}

