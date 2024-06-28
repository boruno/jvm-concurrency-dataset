import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    fun get(index: Int): E = a[index].value

    fun cas(index: Int, expect: E, update: E) = a[index].cas(expect, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E,
    ): Boolean {
        if (index1 > index2) {
            return cas2(index2, expected2, update2, index1, expected1, update1)
        }
        if (index2 == index1) {
            require(expected1 === expected2) { "different expected values for same index" }
            require(update1 === update2) { "different update values for same index" }
            return cas(index1, expected1, update1)
        }
        assert(index1 < index2) { "index1 >= index2" }

        while (true) {
            if (a[index1].value != expected1) {
                return false
            }
            val desc = CASNDescriptor(a[index1], expected1, update1, a[index2], expected2, update2)
            if (cas(index1, expected1, update1)) {
                return desc.complete()
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)

    fun cas(expect: T, update: T): Boolean = v.compareAndSet(expect, update)
    var value: T
        get() {
            v.loop {
                when (it) {
                    is Descriptor -> it.complete()
                    else -> return it as T
                }
            }
        }
        set(value) {
            v.loop {
                when (it) {
                    is Descriptor -> it.complete()
                    else -> if (v.compareAndSet(it, value)) return
                }
            }
        }

    fun casDescriptor(expect: T, descriptor: Descriptor): Boolean = v.compareAndSet(expect, descriptor)
    fun getWithDescriptor(descriptor: Descriptor): T? {
        v.loop {
            when (it) {
                is Descriptor -> {
                    if (it === descriptor) {
                        return null
                    } else {
                        it.complete()
                    }
                }

                else -> return it as T
            }
        }
    }
}

abstract class Descriptor {
    abstract fun complete(): Boolean
}

class DCSSDescriptor<A, B>(
    val a: Ref<A>, val expectA: A, val updateA: A,
    val b: Ref<B>, val expectB: B,
    val outcome: AtomicRef<Consensus> = atomic(Consensus.UNDECIDED),
) : Descriptor() {
    override fun complete(): Boolean {
        val update = if (b.value === expectB) {
            outcome.compareAndSet(Consensus.UNDECIDED, Consensus.SUCCESS)
            updateA
        } else {
            outcome.compareAndSet(Consensus.UNDECIDED, Consensus.FAIL)
            expectA
        }
        a.v.compareAndSet(this, update)
        return outcome.value === Consensus.SUCCESS
    }
}

class CASNDescriptor<A, B>(
    val a: Ref<A>, val expectA: A, val updateA: A,
    val b: Ref<B>, val expectB: B, val updateB: B,
    val outcome: AtomicRef<Consensus> = atomic(Consensus.UNDECIDED),
) : Descriptor() {
    override fun complete(): Boolean {
        while (true) {
            val bVal = b.getWithDescriptor(this)
            if (bVal === null) { // a == expectA && b == expectB, but this descriptor setted
                if (outcome.compareAndSet(Consensus.UNDECIDED, Consensus.SUCCESS)) {
                    updateFromDescriptor()
                    return true
                } else if (outcome.value === Consensus.SUCCESS) {
                    updateFromDescriptor()
                    return true
                } else {
                    rollbackFromDescriptor()
                    return false
                }
            }

            if (bVal === expectB) {
                if (b.casDescriptor(expectB, this)) {
                    if (outcome.compareAndSet(Consensus.UNDECIDED, Consensus.SUCCESS)) {
                        updateFromDescriptor()
                        return true
                    } else if (outcome.value === Consensus.SUCCESS) {
                        updateFromDescriptor()
                        return true
                    }
                } else {
                    continue
                }
            } else {
                outcome.compareAndSet(Consensus.UNDECIDED, Consensus.FAIL)
                a.v.compareAndSet(this, expectA)
                return false
            }
        }
    }

    private fun updateFromDescriptor() {
        a.v.compareAndSet(this, updateA)
        b.v.compareAndSet(this, updateB)
    }

    private fun rollbackFromDescriptor() {
        a.v.compareAndSet(this, expectA)
        b.v.compareAndSet(this, expectB)
    }
}

enum class Consensus {
    UNDECIDED,
    SUCCESS,
    FAIL,
}
