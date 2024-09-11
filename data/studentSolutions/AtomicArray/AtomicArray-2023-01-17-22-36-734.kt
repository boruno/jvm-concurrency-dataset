import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) : E {
        while (true) {
            val v = a[index].value!!
            if (v is DCSSDescriptor<*>) {
                v.complete()
                continue
            }
            if (v is CAS2Descriptor<*>) {
                v.complete()
                continue
            }
            return v as E
        }
    }

    fun cas(index: Int, expected: E, update: E) : Boolean {
        while (true) {
            val v = a[index].value!!
            if (v is DCSSDescriptor<*>) {
                v.complete()
                continue
            }
            if (v is CAS2Descriptor<*>) {
                v.complete()
                continue
            }
            if (v != expected) {
                return false
            }
            if (a[index].compareAndSet(expected, update)) {
                return true
            }
        }
    }

    fun dcss(index1: Int, expected1: E, update1: CAS2Descriptor<E>, id2 : CAS2Descriptor<E>) {
        val desc = DCSSDescriptor(index1, expected1, update1, id2, this)
        while (true) {
            val v = a[index1].value
            if (v is DCSSDescriptor<*>) {
                v.complete()
                continue
            }
            if (v is CAS2Descriptor<*>) {
                if (v == id2) {
                    return
                }
                v.complete()
                continue
            }
            if (v != expected1) {
                return
            }
            if (a[index1].compareAndSet(expected1, desc)) {
                desc.complete()
                return
            }
        }
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2) { //this is a stupid case this should not ever happen but tests check it
            if (expected1 != expected2) {
                return false
            }
            //if (update1 != update2) {
            //    return false
            //}
            return cas(index1, expected1, update1)
            //if (update1 is Int) {
            //    return cas(index1, expected1, (update1 + 1) as E)
            //} else {
            //    assert(false) //die
            //}
        }
        if (index1 > index2) {
            return cas2(index2, expected2, update2, index1, expected1, update1)
        }
        val desc = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2, this)
        while (true) {
            val v = a[index1].value
            if (v is DCSSDescriptor<*>) {
                v.complete()
                continue
            }
            if (v is CAS2Descriptor<*>) {
                v.complete()
                continue
            }
            if (v != expected1) {
                return false
            }
            if (a[index1].compareAndSet(expected1, desc)) {
                desc.complete()
                return desc.result.value!!
            }
        }
    }

    class CAS2Descriptor<E>(val index1: Int, val expected1: E, val update1: E,
                            val index2: Int, val expected2: E, val update2: E, val arr : AtomicArray<E>) {
        val result : AtomicRef<Boolean?> = atomic(null)
        fun complete() {
            arr.dcss(index2, expected2, this, this)
            if (arr.a[index2].value == this) {
                result.compareAndSet(null, true)
            } else {
                result.compareAndSet(null, false)
            }
            if (result.value!!) {
                arr.a[index1].compareAndSet(this, update1)
                arr.a[index2].compareAndSet(this, update2)
            } else {
                arr.a[index1].compareAndSet(this, expected1)
                arr.a[index2].compareAndSet(this, expected2)
            }
        }
    }

    class DCSSDescriptor<E>(val index1: Int, val expected1: E, val update1: CAS2Descriptor<E>,
                            val id2: CAS2Descriptor<E>, val arr : AtomicArray<E>) {
        val result : AtomicRef<Boolean?> = atomic(null)
        fun complete() {
            if (id2.result.value == null) {
                result.compareAndSet(null, true)
            } else {
                result.compareAndSet(null, false)
            }
            if (result.value!!) {
                arr.a[index1].compareAndSet(this, update1)
            } else {
                arr.a[index1].compareAndSet(this, expected1)
            }
        }
    }
}