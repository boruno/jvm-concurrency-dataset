/*import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

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
}*/

import kotlinx.atomicfu.atomic

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    fun get(index: Int) =
        a[index].getVal()

    fun set(index: Int, value: E) {
        a[index].setVal(value)
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            if (a[index].getVal() != expected) break
            if (!a[index].v.compareAndSet(expected, update)) continue
            return true
        }
        return false
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E,
    ): Boolean {

        var idx1 = index1
        var idx2 = index2
        var exp1 = expected1
        var exp2 = expected2
        var upd1 = update1
        var upd2 = update2

        if (idx1 > idx2) {
            idx1 = idx2.also { idx2 = idx1 }
            exp1 = exp2.also { exp2 = exp1 }
            upd1 = upd2.also { upd2 = upd1 }
        }

        if (idx1 != idx2) {
            while (true) {
                val desc = CAS2Descriptor(a[idx1], exp1, upd1, a[idx2], exp2, upd2)
                val flag = a[idx1].v.compareAndSet(exp1, desc)
                if (!flag && a[idx1].getVal() != exp1)
                    return false
                if (flag) {
                    desc.complete()
                    return isSuccess(desc.outcome.getVal())
                }
            }
        }
        if (exp1 != exp2) return false
        return cas(idx1, exp1, upd2)
    }
}

enum class Outcome {
    UNDECIDED, SUCCESS, FAIL
}

fun isSuccess(type: Outcome): Boolean {
    return type == Outcome.SUCCESS
}

fun isFail(type: Outcome): Boolean {
    return type == Outcome.FAIL
}

fun isUndecided(type: Outcome): Boolean {
    return type == Outcome.UNDECIDED
}

abstract class Descriptor {
    abstract fun complete()
}

@Suppress("UNCHECKED_CAST")
class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)

    fun getVal(): T {
        while (true) {
            val curVal = v.value
            if (curVal !is Descriptor)
                return curVal as T
            curVal.complete()
        }
    }

    fun setVal(newVal: T) {
        while (true) {
            val curVal = v.value
            if (curVal is Descriptor)
                curVal.complete()
            else if (v.compareAndSet(curVal, newVal)) return
        }
    }
}

class RDCSSDescriptor<A, B>(
    private val a: Ref<A>, private val expectA: A, private val updateA: Any?,
    private val b: Ref<B>, private val expectB: B,
) : Descriptor() {
    private val outcome = atomic(Outcome.UNDECIDED)

    override fun complete() {
        val curRes = outcome.value
        if (isUndecided(curRes)) {
            val changeTo = if (b.getVal() != expectB) (Outcome.FAIL) else Outcome.SUCCESS
            outcome.compareAndSet(curRes, changeTo)
        }
        var changeVal: Any? = null
        if (isSuccess(outcome.value))
            changeVal = updateA
        else if (isFail(outcome.value))
            changeVal = expectA
        a.v.compareAndSet(this, changeVal)
    }
}


class CAS2Descriptor<A, B>(
    private val a: Ref<A>, private val expectA: A, private val updateA: A,
    private val b: Ref<B>, private val expectB: B, private val updateB: B,
) : Descriptor() {
    private val bValue: B = b.getVal()
    val outcome = Ref(Outcome.UNDECIDED)

    override fun complete() {
        while (true) {
            if (!isUndecided(outcome.getVal())) break
            val descB = RDCSSDescriptor(b, expectB, this, outcome, Outcome.UNDECIDED)
            if (!b.v.compareAndSet(expectB, descB)) {
                val oldB = b.v.value
                when {
                    (oldB is Descriptor && oldB == this) -> outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                    oldB is Descriptor -> b.getVal()
                    (oldB != expectB) -> {
                        outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
                    }
                }
                continue
            }
            descB.complete()
            outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
        }

        var changeA: Any? = null
        var changeB: Any? = null
        if (isSuccess(outcome.getVal())) {
            changeA = updateA
            changeB = updateB
        } else if (isFail(outcome.getVal())) {
            changeA = expectA
            changeB = bValue
        }
        a.v.compareAndSet(this, changeA)
        b.v.compareAndSet(this, changeB)
    }
}