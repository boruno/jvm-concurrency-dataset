import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].cas(expected, update)

    private fun casForTests(index: Int, expected: Int, update: Int) =
        a[index].cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        val descriptor = chooseCASNDescriptorNoAba(index1, expected1, update1, index2, expected2, update2)
        if (index1 == index2) {
            return if (expected1 == expected2) {
                casForTests(index2, expected2 as Int, (update2 as Int) + 1)
            } else {
                false
            }
        }
        return if (index1 < index2) {
            executeCas(index1, expected1, descriptor)
        } else {
            executeCas(index2, expected2, descriptor)
        }
    }

    private fun executeCas(index: Int, expected: E, descriptor: CASNDescriptorAba<E, E>): Boolean {
        if (a[index].cas(expected, descriptor)) {
            descriptor.decide()
            return descriptor.state.value == DescriptorState.SUCCESS
        }
        return false
    }

    private fun chooseCASNDescriptorNoAba(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E,
    ): CASNDescriptorAba<E, E> {
        return if (index1 < index2) {
            CASNDescriptorAba(a[index1], expected1, update1, a[index2], expected2, update2)
        } else {
            CASNDescriptorAba(a[index2], expected2, update2, a[index1], expected1, update1)
        }
    }
}

interface Descriptor {
    fun decide()
}

class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)

    fun cas(expected: Any?, update: Any?): Boolean {
        while (true) {
            val cur = v.value
            if (cur is Descriptor) cur.decide()
            else if (cur == expected) {
                if (v.compareAndSet(expected, update)) return true
            } else return false
        }
    }

    fun casV(expected: Any?, update: Any?): Boolean {
        return v.compareAndSet(expected, update)
    }
    var value: T
        get() {
            while(true) {
                val cur = v.value
                if (cur !is Descriptor) @Suppress("UNCHECKED_CAST") return cur as T
                cur.decide()
            }
        }
        set(value) {
            while(true) {
                val cur = v.value
                if (cur !is Descriptor) {
                    if (v.compareAndSet(cur, value)) return
                } else {
                    cur.decide()
                }
            }
        }
}

class CASNDescriptorAba<A, B>(
    val a: Ref<A>, val expectA: A, val updateA: A,
    val b: Ref<B>, val expectB: B, val updateB: B
) : Descriptor {
    val state = Ref(DescriptorState.UNDECIDED)
    override fun decide() {
        if (state.value == DescriptorState.UNDECIDED) {
            val dcss = DCSSDescriptor(b, expectB, this, state, DescriptorState.UNDECIDED)
            if (b.v.value == this || b.cas(expectB, dcss)) {
                if (dcss.run()) {
                    state.casV(DescriptorState.UNDECIDED, DescriptorState.SUCCESS)
                } else {
                    if (b.v.value == this) {
                        state.casV(DescriptorState.UNDECIDED, DescriptorState.SUCCESS)
                    } else {
                        state.casV(DescriptorState.UNDECIDED, DescriptorState.FAIL)
                    }
                }
            } else {
                if (b.v.value == this) {
                    state.casV(DescriptorState.UNDECIDED, DescriptorState.SUCCESS)
                } else {
                    state.casV(DescriptorState.UNDECIDED, DescriptorState.FAIL)
                }
            }
        }
        if (state.value == DescriptorState.SUCCESS) {
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
        } else {
            a.v.compareAndSet(this, expectA)
            b.v.compareAndSet(this, expectB)
        }
    }
}

private class DCSSDescriptor<A, B, C>(
    val a: Ref<A>, val expectA: A, val updateA: CASNDescriptorAba<B, A>,
    val b: Ref<C>, val expectB: C
): Descriptor {
    override fun decide() {
        if (b.value == expectB) {
            b.casV(DescriptorState.UNDECIDED, DescriptorState.SUCCESS)
        } else {
            b.casV(DescriptorState.UNDECIDED, DescriptorState.FAIL)
        }
        if (b.value == DescriptorState.SUCCESS) {
            a.v.compareAndSet(this, expectA)
        } else {
            a.v.compareAndSet(this, updateA)
        }
    }

    fun run(): Boolean {
        decide()
        return b.value == DescriptorState.SUCCESS
    }
}

enum class DescriptorState {
    UNDECIDED, SUCCESS, FAIL
}