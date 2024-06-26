import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
//        val old1 = get(index1)
//        if (cas(index1, expected1, update1)) {
//            if (cas(index2, expected2, update2)) {
//                return true
//            } else {
//                cas(index1, update1, old1)
//                return false
//            }
//        } else {
//            return false
//        }
        if (index1 == index2) {
            return if (expected1 == expected2) cas(index1, expected1, update2)
            else false
        }
        val x: Ref<E>
        val expectedA: E
        val updateA: E
        val y: Ref<E>
        val expectedB: E
        val updateB: E
        if (index1 < index2) {
            x = Ref(a[index1].value!!); expectedA = expected1; updateA = update1
            y = Ref(a[index2].value!!); expectedB = expected2; updateB = update2
        } else {
            x = Ref(a[index2].value!!); expectedA = expected2; updateA = update2
            y = Ref(a[index1].value!!); expectedB = expected1; updateB = update1
        }
        val desc = CAS2Descriptor(x, expectedA, updateA, y, expectedB, updateB)
        return if (x.cas(expectedA, desc)) desc.complete() else false
    }

//    fun DWCASComplete(dwcasDescriptor: DWCASDescriptor<E>): Boolean {
//
//    }
//
//    fun DCSSComplete(dcssDescriptor: DCSSDescriptor<E>): Boolean {
//        val d = a[dcssDescriptor.index2] == dcssDescriptor.expected2
//        if (d) {
//            cas(dcssDescriptor.index1, dcssDescriptor.expected1, dcssDescriptor.update1)
//        }
//        return d
//    }
}

//class DWCASDescriptor<E>(
//    val index1: Int,
//    val expected1: E,
//    val update1: E,
//    val index2: Int,
//    val expected2: E,
//    val update2: E
//)
//
//class DCSSDescriptor<E>(val index1: Int, val expected1: E, val update1: E, val index2: Int, val expected2: E)

class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)

    var value: T
        get() {
            v.loop { cur ->
                if (cur is Descriptor) cur.complete()
                else return cur as T
            }
        }
        set(value) {
            v.loop { cur ->
                if (cur is Descriptor) cur.complete()
                else if (v.compareAndSet(cur, value)) return
            }
        }

    fun cas(expect: Any?, update: Any?): Boolean {
        v.loop { cur ->
            if (cur is Descriptor) cur.complete()
            else if (expect != cur) return false
            else if (v.compareAndSet(cur, update)) return true
        }
    }
}

abstract class Descriptor {
    abstract fun complete(): Boolean
}

class CAS2Descriptor<T>(
    val a: Ref<T>, val expectA: T, val updateA: T,
    val b: Ref<T>, val expectB: T, val updateB: T,
) : Descriptor() {
    val outcome: Ref<Boolean?> = Ref(null)

    override fun complete(): Boolean {
        if (b.v.value != this) dcss(b, expectB, this, outcome, null)

        outcome.v.compareAndSet(null, b.v.value == this)

        val outcomeValue = outcome.value!!

        val aValue = if (outcomeValue) updateA else expectA
        a.v.compareAndSet(this, aValue)

        val bValue = if (outcomeValue) updateB else expectB
        b.v.compareAndSet(this, bValue)

        return outcomeValue
    }
}

fun dcss(
    a: Ref<out Any?>, expectA: Any?, updateA: Any?,
    b: Ref<out Any?>, expectB: Any?
): Boolean {
    val desc = DCSSDescriptor(a, expectA, updateA, b, expectB)
    return if (a.cas(expectA, desc)) desc.complete() else false
}

class DCSSDescriptor(
    val a: Ref<out Any?>, val expectA: Any?, val updateA: Any?,
    val b: Ref<out Any?>, val expectB: Any?
) : Descriptor() {
    val outcome = atomic<Boolean?>(null)

    override fun complete(): Boolean {
        outcome.compareAndSet(null, b.value == expectB)

        val outcomeValue = outcome.value!!

        val aValue = if (outcomeValue) updateA else expectA
        a.v.compareAndSet(this, aValue)

        return outcomeValue
    }

    fun dcss(){

    }
}