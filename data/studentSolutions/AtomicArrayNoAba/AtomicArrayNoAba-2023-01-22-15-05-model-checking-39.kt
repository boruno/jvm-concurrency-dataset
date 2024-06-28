import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        val descriptor = chooseCASNDescriptorNoAba(index1, expected1, update1, index2, expected2, update2)
        if (index1 == index2) {
            return if (expected1 == expected2) {
                cas(index2, expected2, update2)
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

    private fun executeCas(index: Int, expected: E, descriptor: CASNDescriptorNoAba<E, E>): Boolean {
        if (a[index].cas(expected, descriptor)) {
            descriptor.decide()
            return descriptor.state.value == DescriptorState.SUCCESS
        }
        return false
    }

    private fun chooseCASNDescriptorNoAba(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E,
    ): CASNDescriptorNoAba<E, E> {
        return if (index1 < index2) {
            CASNDescriptorNoAba(a[index1], expected1, update1, a[index2], expected2, update2)
        } else {
            CASNDescriptorNoAba(a[index2], expected2, update2, a[index1], expected1, update1)
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
            if (cur == expected) {
                if (v.compareAndSet(expected, update)) return true
            } else return false
        }
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

class CASNDescriptorNoAba<A, B>(
    val a: Ref<A>, val expectA: A, val updateA: A,
    val b: Ref<B>, val expectB: B, val updateB: B
) : Descriptor {
    val state = atomic(DescriptorState.UNDECIDED)
    override fun decide() {
        if (state.value == DescriptorState.UNDECIDED) {
            if (b.cas(expectB, this)) {
                state.compareAndSet(DescriptorState.UNDECIDED, DescriptorState.SUCCESS)
            } else {
                if (b.v.value == this) {
                    state.compareAndSet(DescriptorState.UNDECIDED, DescriptorState.SUCCESS)
                } else {
                    state.compareAndSet(DescriptorState.UNDECIDED, DescriptorState.FAIL)
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

enum class DescriptorState {
    UNDECIDED, SUCCESS, FAIL
}