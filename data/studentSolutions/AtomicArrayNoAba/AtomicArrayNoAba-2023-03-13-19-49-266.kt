import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

    fun set(index: Int, value: E) {
        a[index].value = value
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
//        if (a[index1].value != expected1 || a[index2].value != expected2) return false
//        a[index1].value = update1
//        a[index2].value = update2
//        return true
        if (index1 == index2) {
            if (expected1 == expected2)
                return cas(index1, expected1, update2)
            else
                return false
        }

        val x: Ref<E>
        val expectedX: E
        val updateX: E
        val y: Ref<E>
        val expectedY: E
        val updateY: E
        if (index1 < index2) {
            x = Ref(a[index1].value!!)
            expectedX = expected1
            updateX = update1
            y = Ref(a[index2].value!!)
            expectedY = expected2
            updateY = update2
        } else {
            x = Ref(a[index2].value!!)
            expectedX = expected2
            updateX = update2
            y = Ref(a[index1].value!!)
            expectedY = expected1
            updateY = update1
        }
        val desc = CAS2Descriptor(x, expectedX, updateX, y, expectedY, updateY)
        return if (x.cas(expectedX, desc)) desc.complete() else false
    }
}

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

private abstract class Descriptor {
    abstract fun complete(): Boolean
}

private class DCSSDescriptor(
    val a: Ref<out Any?>, val expectA: Any?, val updateA: Any?,
    val b: Ref<out Any?>, val expectB: Any?,
) : Descriptor() {
    val outcome = atomic<Boolean?>(null)

    override fun complete(): Boolean {
        outcome.compareAndSet(null, b.value == expectB)

        val outcomeValue = outcome.value!!

        val aValue = if (outcomeValue) updateA else expectA
        a.v.compareAndSet(this, aValue)

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

private class CAS2Descriptor<T>(
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