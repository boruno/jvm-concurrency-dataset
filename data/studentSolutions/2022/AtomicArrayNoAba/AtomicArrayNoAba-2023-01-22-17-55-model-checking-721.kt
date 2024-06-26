import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {

    private val arr = Array(size) { Ref(initialValue) }

    fun get(index: Int) =
        arr[index].value

    fun set(index: Int, value: E) {
        arr[index].value = value
    }

    fun cas(index: Int, expected: E, update: E) =
        arr[index].cas(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) {
            return if (expected1 == expected2) arr[index1].cas(expected1, update2)
            else false
        }
        val a: Ref<E>;
        val expectedA: E;
        val updateA: E
        val b: Ref<E>;
        val expectedB: E;
        val updateB: E
        if (index1 < index2) {
            a = arr[index1]; expectedA = expected1; updateA = update1
            b = arr[index2]; expectedB = expected2; updateB = update2
        } else {
            a = arr[index2]; expectedA = expected2; updateA = update2
            b = arr[index1]; expectedB = expected1; updateB = update1
        }
        val desc = CAS2Descriptor(a, expectedA, updateA, b, expectedB, updateB)
        return if (a.cas(expectedA, desc)) desc.complete() else false
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