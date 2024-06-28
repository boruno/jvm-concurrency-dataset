import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.forceValue

    fun set(index: Int, value: E) {
        a[index].value!!.forceValue = value
    }

    fun cas(index: Int, expected: E, update: E): Boolean =
        a[index].value!!.cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        while (true) {
            val a1 = a[index1].value!!
            val a2 = a[index2].value!!
            val d = Cas2Descriptor(a1  as Ref<Any>, expected1 as Any, update1 as Any, a2 as Ref<Any>, expected2 as Any, update2 as Any)
            if (a1.cas(a1.forceValue, d)) {
                d.complete()
            } else {
                return false
            }
            return d.outcome.forceValue == DStatus.SUCCESS
        }
    }
}

abstract class Descriptor {
    abstract fun complete()
}

enum class DStatus { UNDECIDED, FAIL, SUCCESS }

private class Cas2Descriptor(
    val a: Ref<Any>, val expectedA: Any, val updateA: Any,
    val b: Ref<Any>, val expectedB: Any, val updateB: Any,
    var outcome: Ref<DStatus> = Ref(DStatus.UNDECIDED)
): Descriptor() {
    override fun complete() {
        val rdcssDescriptor = RdcssDescriptor(
            a = b, expectedA = expectedB, updateA = this,
            b = outcome as Ref<Any>, expectedB = DStatus.UNDECIDED
        )
        if (b.v.compareAndSet(expectedB, rdcssDescriptor)) {
            rdcssDescriptor.complete()
            if(rdcssDescriptor.outcome.value == DStatus.SUCCESS) {
                outcome.cas(DStatus.UNDECIDED, DStatus.SUCCESS)
                updateWhenSuccess()
            }
            if(rdcssDescriptor.outcome.value == DStatus.FAIL) {
                outcome.cas(DStatus.UNDECIDED, DStatus.FAIL)
                updateWhenFail()
            }
        } else {
            if (b.v.value === this) {
                outcome.cas(DStatus.UNDECIDED, DStatus.SUCCESS)
                updateWhenSuccess()
            } else {
                outcome.cas(DStatus.UNDECIDED, DStatus.FAIL)
                a.v.compareAndSet(this, expectedA)
            }
        }
    }

    fun updateWhenSuccess() {
        a.v.compareAndSet(this, updateA)
        b.v.compareAndSet(this, updateB)
    }
    fun updateWhenFail() {
        a.v.compareAndSet(this, expectedA)
        b.v.compareAndSet(this, expectedB)
    }
}

private class RdcssDescriptor(
    val a: Ref<Any>, val expectedA: Any, val updateA: Any,
    val b: Ref<Any>, val expectedB: Any,
    val outcome: AtomicRef<DStatus> = atomic(DStatus.UNDECIDED)
): Descriptor() {
    override fun complete() {
        when (outcome.value) {
            DStatus.UNDECIDED -> {
                val update =
                    if (b.v.value == expectedB) updateA
                    else expectedA
                val status = if (update == updateA) {
                    DStatus.SUCCESS
                } else {
                    DStatus.FAIL
                }
                if (outcome.compareAndSet(DStatus.UNDECIDED, status)) {
                    a.v.compareAndSet(this, updateA)
                }
            }
            DStatus.FAIL -> a.v.compareAndSet(this, expectedA)
            DStatus.SUCCESS -> a.v.compareAndSet(this, updateA)
        }
    }
}

class Ref<T>(init: Any?) {
    val v = atomic<Any?>(init)
    var forceValue: T
        get() {
            v.loop { cur ->
                when(cur) {
                    is Descriptor -> cur.complete()
                    else -> return cur as T
                }
            }
        }
        set(upd) {
            v.loop { cur ->
                when(cur) {
                    is Descriptor -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd)) return
                }
            }
        }
    fun cas(expected: T, update: T): Boolean {
        val x = v.value
        v.loop {
            if (it is Descriptor) {
                it.complete()
            } else if (x != expected) {
                return false
            } else {
                return v.compareAndSet(expected, update)
            }
        }
    }

    override fun toString(): String {
        return "Ref<" + v.value.toString() + ">"
    }
}

//