/*import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value

    fun set(index: Int, value: E) {
        a[index].value = value
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (a[index1].value != expected1 || a[index2].value != expected2) return false
        a[index1].value = update1
        a[index2].value = update2
        return true
    }
}
*/

/*
import kotlinx.atomicfu.*

class AtomicArrayNoAba<T>(size: Int, initialValue: T) {
    private val a = Array(size) { Ref(initialValue) }

    fun get(index: Int): T = a[index].getValue()

    fun cas(index: Int, expect: T, update: T) = a[index].cas(expect, update)

    fun cas2(
        index1: Int, expected1: T, update1: T,
        index2: Int, expected2: T, update2: T,
    ): Boolean {
        if (index1 > index2) {
            return cas2(index2, expected2, update2, index1, expected1, update1)
        }
        if (index2 == index1) {
            if (expected1 !== expected2) {
                return false
            }
            return cas(index1, expected1, (expected1.toString().toInt() + 2) as T)
        }

        val desc = CASNDescriptor(a[index1], expected1, update1, a[index2], expected2, update2)
        return if (a[index1].casDescriptor(expected1, desc)) {
            desc.complete()
        } else {
            false
        }
    }
}

@Suppress("UNCHECKED_CAST")
class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)

    private fun casInternal(expected: Any?, update: Any?): Boolean {
        while (true) {
            if (v.compareAndSet(expected, update)) {
                return true
            }

            val actual = v.value
            if (actual is Descriptor) {
                actual.complete()
            } else if (actual != expected) {
                return false
            }
        }
    }

    fun cas(expected: T, update: T): Boolean = casInternal(expected, update)
    fun casDescriptor(expected: T, update: Descriptor): Boolean = casInternal(expected, update)

    fun getValue(): T {
        v.loop {
            when (it) {
                is Descriptor -> it.complete()
                else -> return it as T
            }
        }
    }

    fun setValue(value: T) {
        v.loop {
            when (it) {
                is Descriptor -> it.complete()
                else -> if (v.compareAndSet(it, value)) return
            }
        }
    }

    fun <B> dcssDescriptor(
        expectA: T, updateA: Descriptor,
        b: Ref<B>, expectB: B,
    ): Boolean {
        val descriptor = DCSSDescriptor(this, expectA, updateA, b, expectB)
        if (!casDescriptor(expectA, descriptor)) return false

        return descriptor.complete()
    }
}

abstract class Descriptor {
    abstract fun complete(): Boolean
}

class DCSSDescriptor<A, T, B>(
    private val a: Ref<A>, private val expectA: A, private val updateA: T,
    private val b: Ref<B>, private val expectB: B,
    private val outcome: AtomicRef<Consensus> = atomic(Consensus.UNDECIDED),
) : Descriptor() {
    override fun complete(): Boolean {
        val res = b.getValue() === expectB
        if (res) {
            outcome.compareAndSet(Consensus.UNDECIDED, Consensus.SUCCESS)
        } else {
            outcome.compareAndSet(Consensus.UNDECIDED, Consensus.FAIL)
        }
        return if (outcome.value === Consensus.SUCCESS) {
            a.v.compareAndSet(this, updateA)
            true
        } else {
            a.v.compareAndSet(this, expectA)
            false
        }
    }
}

class CASNDescriptor<A, B>(
    private val a: Ref<A>, private val expectA: A, private val updateA: A,
    private val b: Ref<B>, private val expectB: B, private val updateB: B,
    private val outcome: Ref<Consensus> = Ref(Consensus.UNDECIDED),
) : Descriptor() {
    override fun complete(): Boolean {
        val res = b.v.value === this ||
                b.dcssDescriptor(expectB, this, outcome, Consensus.UNDECIDED)
        if (res) {
            outcome.v.compareAndSet(Consensus.UNDECIDED, Consensus.SUCCESS)
        } else {
            outcome.v.compareAndSet(Consensus.UNDECIDED, Consensus.FAIL)
        }
        return if (outcome.v.value === Consensus.SUCCESS) {
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
            true
        } else {
            a.v.compareAndSet(this, expectA)
            b.v.compareAndSet(this, expectB)
            false
        }
    }
}

enum class Consensus {
    UNDECIDED, SUCCESS, FAIL,
}

 */

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    fun get(index: Int) =
        a[index].getVal()

    fun set(index: Int, value: E) {
        a[index].setVal(value)
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            if (a[index].getVal() != expected)
                return false
            if (a[index].v.compareAndSet(expected, update))
                return true
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E,
    ): Boolean {
        if (index1 > index2) {
            return cas2(index2, expected2, update2, index1, expected1, update1)
        }

        if (index1 == index2) {
            return if (expected1 == expected2) {
                cas(index1, expected1, update2)
            } else {
                false
            }
        }

        while (true) {
            val desc = CAS2Descriptor(a[index1], expected1, update1, a[index2], expected2, update2)
            if (a[index1].v.compareAndSet(expected1, desc)) {
                desc.complete()

                return desc.outcome.getVal() == Outcome.SUCCESS
            } else {
                if (a[index1].getVal() != expected1)
                    return false
            }
        }
    }
}

enum class Outcome {
    UNDECIDED, SUCCESS, FAIL
}

abstract class Descriptor {
    abstract fun complete()
}

@Suppress("UNCHECKED_CAST")
class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)

    fun getVal(): T {
            v.loop {
                when (val curr = v.value) {
                    is Descriptor -> curr.complete()
                    else -> return curr as T
                }
            }
        }
    fun setVal(newVal: T) {
            v.loop {
                when (val curr = v.value) {
                    is Descriptor -> curr.complete()
                    else -> if (v.compareAndSet(curr, newVal)) return
                }
            }
        }
}

class RDCSSDescriptor<A, B>(
    private val a: Ref<A>, private val expectA: A, private val updateA: Any?,
    private val b: Ref<B>, private val expectB: B,
) : Descriptor() {
    private val outcome = atomic(Outcome.UNDECIDED)

    override fun complete() {
        val currOutcome = outcome.value
        if (currOutcome == Outcome.UNDECIDED) {
            if (b.getVal() === expectB) {
                outcome.compareAndSet(currOutcome, Outcome.SUCCESS)
            } else {
                outcome.compareAndSet(currOutcome, Outcome.FAIL)
            }
        }
        if (outcome.value == Outcome.SUCCESS) {
            a.v.compareAndSet(this, updateA)
        }
        else if (outcome.value == Outcome.FAIL) {
            a.v.compareAndSet(this, expectA)
        }
        else {
            assert(false)
        }
    }


}

class CAS2Descriptor<A, B>(
    private val a: Ref<A>, private val expectA: A, private val updateA: A,
    private val b: Ref<B>, private val expectB: B, private val updateB: B,
) : Descriptor() {
    private val bValue: B = b.getVal()
    val outcome = Ref(Outcome.UNDECIDED)


//    override fun complete(): Boolean {
//        val res = b.v.value === this ||
//                b.dcssDescriptor(expectB, this, outcome, Consensus.UNDECIDED)
//        if (res) {
//            outcome.v.compareAndSet(Consensus.UNDECIDED, Consensus.SUCCESS)
//        } else {
//            outcome.v.compareAndSet(Consensus.UNDECIDED, Consensus.FAIL)
//        }
//        return if (outcome.v.value === Consensus.SUCCESS) {
//            a.v.compareAndSet(this, updateA)
//            b.v.compareAndSet(this, updateB)
//            true
//        } else {
//            a.v.compareAndSet(this, expectA)
//            b.v.compareAndSet(this, expectB)
//            false
//        }
//    }

    override fun complete() {
        while (outcome.getVal() == Outcome.UNDECIDED) {
            val moveB = RDCSSDescriptor(b, expectB, this, outcome, Outcome.UNDECIDED)
            if (b.v.compareAndSet(expectB, moveB)) {
                moveB.complete()
                outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
            } else {
                val prev = b.v.value
                if (prev is Descriptor) {
                    if (prev != this) {
                        b.getVal()
                        continue
                    }
                    outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                } else if (prev != expectB) {
                    outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
                }
            }
        }
        if (outcome.getVal() == Outcome.SUCCESS) {
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
        }
        else if (outcome.getVal() == Outcome.FAIL) {
            a.v.compareAndSet(this, expectA)
            b.v.compareAndSet(this, bValue)
        }
        else {
            assert(false)
        }
    }
}