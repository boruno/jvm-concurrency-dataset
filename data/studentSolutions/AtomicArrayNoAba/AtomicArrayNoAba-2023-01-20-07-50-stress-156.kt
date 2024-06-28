//import kotlinx.atomicfu.*
//
//class AtomicArrayNoAba<T>(size: Int, initialValue: T) {
//    private val a = Array(size) { Ref(initialValue) }
//
//    fun get(index: Int): T = a[index].getValue()
//
//    fun cas(index: Int, expect: T, update: T) = a[index].cas(expect, update)
//
//    fun cas2(
//        index1: Int, expected1: T, update1: T,
//        index2: Int, expected2: T, update2: T,
//    ): Boolean {
//        if (index1 > index2) {
//            return cas2(index2, expected2, update2, index1, expected1, update1)
//        }
//        if (index2 == index1) {
//            if (expected1 !== expected2) {
//                return false
//            }
//            return cas(index1, expected1, (expected1.toString().toInt() + 2) as T)
//        }
//
//        val desc = CASNDescriptor(a[index1], expected1, update1, a[index2], expected2, update2)
//        return if (a[index1].casDescriptor(expected1, desc)) {
//            desc.complete()
//        } else {
//            false
//        }
//    }
//}
//
//@Suppress("UNCHECKED_CAST")
//class Ref<T>(initial: T) {
//    val v = atomic<Any?>(initial)
//
//    private fun casInternal(expected: Any?, update: Any?): Boolean {
//        while (true) {
//            if (v.compareAndSet(expected, update)) {
//                return true
//            }
//
//            val actual = v.value
//            if (actual is Descriptor) {
//                actual.complete()
//            } else if (actual != expected) {
//                return false
//            }
//        }
//    }
//
//    fun cas(expected: T, update: T): Boolean = casInternal(expected, update)
//    fun casDescriptor(expected: T, update: Descriptor): Boolean = casInternal(expected, update)
//
//    fun getValue(): T {
//        v.loop {
//            when (it) {
//                is Descriptor -> it.complete()
//                else -> return it as T
//            }
//        }
//    }
//
//    fun setValue(value: T) {
//        v.loop {
//            when (it) {
//                is Descriptor -> it.complete()
//                else -> if (v.compareAndSet(it, value)) return
//            }
//        }
//    }
//
//    fun <B> dcssDescriptor(
//        expectA: T, updateA: Descriptor,
//        b: Ref<B>, expectB: B,
//    ): Boolean {
//        val descriptor = DCSSDescriptor(this, expectA, updateA, b, expectB)
//        if (!casDescriptor(expectA, descriptor)) return false
//
//        return descriptor.complete()
//    }
//}
//
//abstract class Descriptor {
//    abstract fun complete(): Boolean
//}
//
//class DCSSDescriptor<A, T, B>(
//    private val a: Ref<A>, private val expectA: A, private val updateA: T,
//    private val b: Ref<B>, private val expectB: B,
//    private val outcome: AtomicRef<Consensus> = atomic(Consensus.UNDECIDED),
//) : Descriptor() {
//    override fun complete(): Boolean {
//        val res = b.getValue() === expectB
//        if (res) {
//            outcome.compareAndSet(Consensus.UNDECIDED, Consensus.SUCCESS)
//        } else {
//            outcome.compareAndSet(Consensus.UNDECIDED, Consensus.FAIL)
//        }
//        return if (outcome.value === Consensus.SUCCESS) {
//            a.v.compareAndSet(this, updateA)
//            true
//        } else {
//            a.v.compareAndSet(this, expectA)
//            false
//        }
//    }
//}
//
//class CASNDescriptor<A, B>(
//    private val a: Ref<A>, private val expectA: A, private val updateA: A,
//    private val b: Ref<B>, private val expectB: B, private val updateB: B,
//    private val outcome: Ref<Consensus> = Ref(Consensus.UNDECIDED),
//) : Descriptor() {
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
//}
//
//enum class Consensus {
//    UNDECIDED, SUCCESS, FAIL,
//}


import kotlinx.atomicfu.atomic

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    fun get(index: Int) =
        a[index].getVal()

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