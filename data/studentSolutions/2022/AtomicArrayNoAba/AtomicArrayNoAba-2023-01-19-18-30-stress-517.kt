import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Element<E>>(size)
    //private val aD = atomicArrayOfNulls<Descriptor<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Value(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.arrayValue.value

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.arrayValue.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2) {
            return cas(index1, expected1, (((expected1) as Int) + 2) as E)
        }
        val desc1 = Descriptor(index1, expected1, update1, get(index1))
        val desc2 = Descriptor(index2, expected2, update2, get(index2))
        val desc = DCASDescriptor<E>()
        desc1.parent = desc
        desc2.parent = desc
        desc.descriptors[0] = desc1
        desc.descriptors[1] = desc2

        if (DCAS(desc)) {
            a[index1].compareAndSet(desc1, Value(desc1.update))
            a[index2].compareAndSet(desc2, Value(desc2.update))
            return true
        } else {
            a[index1].compareAndSet(desc1, Value(desc1.old))
            a[index2].compareAndSet(desc2, Value(desc2.old))
            return false
        }
    }

    fun DCAS(desc: DCASDescriptor<E>) : Boolean {
        var cont = true
        var success = true
        for (descriptor in desc.descriptors) {

            while (true) {
                val pair = readInternal(descriptor!!.index, desc)
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
                if (a[descriptor.index].compareAndSet(pair.first, descriptor)) break
            }
            if (cont) continue else break
        }
        if (success) {
            desc.status.compareAndSet(StatusType.ACTIVE, StatusType.SUCCESSFUL)
            return true
        } else {
            desc.status.compareAndSet(StatusType.ACTIVE, StatusType.FAILED)
            return false
        }
    }

    fun readInternal(ind: Int, desc: DCASDescriptor<E>) : Pair<Element<E>?, E> {
        while (true) {
            val value = a[ind].value
            val number = value!!.arrayValue
            if (value is Value) {
                return Pair(value, number.value)
            } else if (value is Descriptor<E>) {
                val parent = value.parent
                if (parent != desc && parent.status.value == StatusType.ACTIVE) {
                    DCAS(parent)
                    continue
                }
                else {
                    val desc0 = parent.descriptors[0]!!
                    val desc1 = parent.descriptors[1]!!
                    if (parent.status.value == StatusType.SUCCESSFUL) {
                        if (a[desc0.index].value is Descriptor<E>) {
                            a[desc0.index].compareAndSet(desc0, Value(desc0.update))
                        }
                        if (a[desc1.index].value is Descriptor<E>) {
                            a[desc1.index].compareAndSet(desc1, Value(desc1.update))
                        }
                        return Pair(value, value.update)
                    }
                    else {
                        if (a[desc0.index].value is Descriptor<E>) {
                            a[desc0.index].compareAndSet(desc0, Value(desc0.old))
                        }
                        if (a[desc1.index].value is Descriptor<E>) {
                            a[desc1.index].compareAndSet(desc1, Value(desc1.old))
                        }
                        return Pair(value, value.expected)
                    }
                }
            }
        }
    }


    class Descriptor<E> (val index: Int, val expected: E, val update: E, val old: E)  : Element<E>(expected) {
        var parent = DCASDescriptor<E>()
    }

    class Value<E> (v: E) : Element<E>(v) {

    }

    class DCASDescriptor<E> {

        val status: AtomicRef<StatusType> = atomic(StatusType.ACTIVE)
        val size = 2
        val descriptors = arrayOfNulls<Descriptor<E>>(size)
    }

    abstract class Element<E>(v: E) {
        val arrayValue = atomic(v)
    }



    enum class StatusType {ACTIVE, SUCCESSFUL, FAILED}
}