import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)
    private val aD = atomicArrayOfNulls<Descriptor<E>>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2) {
            return cas(index1, expected1, (((expected1) as Int) + 2) as E)
        }
        val desc1 = Descriptor(index1, expected1, update1)
        val desc2 = Descriptor(index2, expected2, update2)
        val desc = DCASDescriptor<E>()
        desc.descriptors[0] = desc1
        desc.descriptors[1] = desc2


        var cont = true
        var success = true
        for (descriptor in desc.descriptors) {
            if (descriptor == null) {
                continue
            }
            while (true) {
                val pair = readInternal(descriptor.index, desc)
                if (pair.first == descriptor) {
                    break
                }
                if (pair.second != descriptor.expected) {
                    success = false
                    cont = false
                    break
                }
                if (desc.status.value != StatusType.ACTIVE) {
                    cont = false
                    break
                }
                if (aD[descriptor.index].compareAndSet(pair.first, descriptor)) break
            }
            if (cont) continue else break
        }
        if (success) {
            desc.status.compareAndSet(StatusType.ACTIVE, StatusType.SUCCESSFUL)
            if (!a[index1].compareAndSet(expected1, update1)) {
                return false
            }

            if (!a[index2].compareAndSet(expected2, update2)) {
                return false
            }
            aD[index1].value = null
            aD[index2].value = null
            return true
        } else {
            desc.status.compareAndSet(StatusType.ACTIVE, StatusType.FAILED)
            aD[index1].value = null
            aD[index2].value = null
            return false
        }
    }

    fun DCAS(desc: DCASDescriptor<E>) : Boolean {
        var cont = true
        var success = true
        for (descriptor in desc.descriptors) {
            if (descriptor == null) {
                continue
            }
            while (true) {
                val pair = readInternal(descriptor.index, desc)
                if (pair.first == descriptor) {
                    break
                }
                if (pair.second != descriptor.expected) {
                    success = false
                    cont = false
                    break
                }
                if (desc.status.value != StatusType.ACTIVE) {
                    cont = false
                    break
                }
                if (aD[descriptor.index].compareAndSet(pair.first, descriptor)) break
            }
            if (cont) continue else break
        }
        if (success) {
            desc.status.compareAndSet(StatusType.ACTIVE, StatusType.SUCCESSFUL)
        } else {
            desc.status.compareAndSet(StatusType.ACTIVE, StatusType.FAILED)
        }
        return desc.status.value == StatusType.SUCCESSFUL
    }

    fun readInternal(ind: Int, desc: DCASDescriptor<E>) : Pair<Descriptor<E>?, E> {
        while (true) {
            val value = aD[ind].value
            if (value == null) {
                return Pair(value, a[ind].value!!)
            } else {
                val parent = value.parent
                if (parent != desc && parent.status.value == StatusType.ACTIVE) {
                    DCAS(parent)
                    continue
                } else {
                    return if (parent.status.value == StatusType.SUCCESSFUL)
                        Pair(value, value.update)
                    else
                        Pair(value, value.expected)
                }
            }
        }
    }


    class Descriptor<E> (val index: Int, val expected: E, val update: E) {
        val parent = DCASDescriptor<E>()
    }

    class DCASDescriptor<E> {

        val status: AtomicRef<StatusType> = atomic(StatusType.ACTIVE)
        val size = 2
        val descriptors = arrayOfNulls<Descriptor<E>>(size)
    }

    enum class StatusType {ACTIVE, SUCCESSFUL, FAILED}
}