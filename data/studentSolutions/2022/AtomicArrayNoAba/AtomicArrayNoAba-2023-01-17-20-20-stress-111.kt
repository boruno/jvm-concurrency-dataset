import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) : E {
        return a[index].value!! as E
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun dcss(index1: Int, expected1: E, update1: E, index2: Int, expected2: E): Boolean {
        val desc = DCSSDescriptor(index1, expected1, update1, index2, expected2, this)
        if (a[index1].compareAndSet(expected1, desc)) {
            desc.complete()
            return desc.result.value!!
        }
        return false
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (a[index1].value != expected1 || a[index2].value != expected2) return false
        a[index1].value = update1
        a[index2].value = update2
        return true
    }

    class DCSSDescriptor<E>(val index1: Int, val expected1: E, val update1: E, val index2: Int, val expected2: E, val arr : AtomicArrayNoAba<E>) {
        val result : AtomicRef<Boolean?> = atomic(null)
        fun complete() {
            if (arr.a[index2].value == expected2) {
                result.compareAndSet(null, true)
                arr.a[index1].compareAndSet(this, update1)
            } else {
                result.compareAndSet(null, false)
                arr.a[index1].compareAndSet(this, expected1)
            }
        }
    }
}