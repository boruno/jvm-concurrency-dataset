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
        val desc1 = Descriptor(index1, expected1, update1)
        val desc2 = Descriptor(index2, expected2, update2)
        val desc = DCASDescriptor<E>()
        desc1.parent = desc
        desc2.parent = desc
        desc.descriptors[0] = desc1
        desc.descriptors[1] = desc2

        return DCAS(desc)
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
        val desc0 = desc.descriptors[0]!!
        val desc1 = desc.descriptors[1]!!
        if (success) {
            a[desc0.index].compareAndSet(desc0, Value(desc0.update))
            a[desc1.index].compareAndSet(desc1, Value(desc1.update))
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
                } else {
                    return if (parent.status.value == StatusType.SUCCESSFUL)
                        Pair(value, value.update)
                    else
                        Pair(value, value.expected)
                }
            }
        }
    }


    class Descriptor<E> (val index: Int, val expected: E, val update: E)  : Element<E>(expected) {
        var parent = DCASDescriptor<E>()
    }

    class Value<E> (v: E) : Element<E>(v) {
        val str = "DUMMY"
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