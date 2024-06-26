import kotlinx.atomicfu.*
import kotlin.math.exp

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Reference<Any?>>(size)

    init {
        for (i in 0 until size) a[i].value = Reference(initialValue)
    }

    fun get(index: Int): E = a[index].value!!.myGetValue() as E
//    fun get(index: Int): E = throw AssertionError("wtf aasdasd")

    fun cas(index: Int, expected: E, update: E): Boolean = a[index].value!!.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E
    ): Boolean {
        var first: SwapDescription<E>? = null
        var second: SwapDescription<E>? = null
        if (index1 < index2) {
            first = SwapDescription(a[index1].value!!, expected1, update1)
            second = SwapDescription(a[index2].value!!, expected2, update2)
        } else if (index1 > index2) {
            first = SwapDescription(a[index2].value!!, expected2, update2)
            second = SwapDescription(a[index1].value!!, expected1, update1)
        } else {
            throw AssertionError("wtf in cas2")
        }

        val desc = casnDescriptor(
            first.actual1, first.expected, first.update, second.actual1, second.expected, second.update
        )
        desc.complete1()
        if (desc.outcome.myGetValue() == Res.success) {
            return true
        } else if (desc.outcome.myGetValue() == Res.nots) {
            return false
        } else {
            throw AssertionError("wtf2 in cas2")
        }
    }

    private class SwapDescription<E>(val actual1: Reference<Any?>, val expected: E, val update: E)

    private class casnDescriptor<E>(
        val actual1: Reference<Any?>,
        val expected1: E,
        val update1: E,
        val actual2: Reference<Any?>,
        val expected2: E,
        val update2: E
    ) {
        val outcome = Reference(Res.undecided as Any?)
        fun complete1() {
            // а если операция?
            var status = Res.success
            while (true) {
                val firstDesc = dcssDescriptor(actual1, expected1, this, outcome, Res.undecided)
                val firstCasRes: Any? = firstDesc.dcssTwoIndexes()

                if (firstCasRes is casnDescriptor<*> && firstCasRes != this) {
                    firstCasRes.complete1()
                    continue
                } else if (firstCasRes !is casnDescriptor<*> && firstCasRes != expected1) {
                    status = Res.nots
                    break
                }

                val secondDesc = dcssDescriptor(actual2, expected2, this, outcome, Res.undecided)
                val secondCasRes: Any? = secondDesc.dcssTwoIndexes()

                if (secondCasRes is casnDescriptor<*> && secondCasRes != this) {
                    secondCasRes.complete1()
                    continue
                } else if (secondCasRes !is casnDescriptor<*> && secondCasRes != expected2) {
                    status = Res.nots
                    break
                }
                break
            }
            if (status == Res.success) {
                if (outcome.compareAndSet(Res.undecided, status)) {
                    actual1.compareAndSet(this, update1)
                    actual2.compareAndSet(this, update2)
                }
            } else if (status == Res.nots) {
                if (outcome.compareAndSet(Res.undecided, status)) {
                    actual1.compareAndSet(this, expected1)
                    actual2.compareAndSet(this, expected2)
                }
            }
        }
    }

    enum class Res {
        undecided, success, nots,
    }

    private class dcssDescriptor<E1, E2>(
        val actual1: Reference<Any?>,
        val expected1: E1,
        val update1: E1,
        val actual2: Reference<Any?>,
        val expected2: E2
    ) {
        val outcome = atomic(Res.undecided)

        fun dcssTwoIndexes(): Any? {
//            val descriptor = dcssDescriptor(actual1, expected1, update1, actual2, expected2)
            var r: Any?
            do {
                r = actual1.cas1(expected1, this)
                if (r is dcssDescriptor<*, *>) {
                    r.complete2()
                }
            } while (r is dcssDescriptor<*, *>)
            if (r == expected1) {
                complete2()
            }
            return r
        }

        fun complete2(): Any? {
//            val update = if (actual2.getValue() == expected2) update1 else expected1
            val actVal = actual2.myGetValue()//остановился тут
            if (actVal == expected2) {
                if (outcome.compareAndSet(Res.undecided, Res.success)) {
                    if (!actual1.compareAndSet(this, update1)) {
//                        throw AssertionError("dcss compelete cas")
                    }
                }
            } else {
                if (outcome.compareAndSet(Res.undecided, Res.nots)) {
                    if (!actual1.compareAndSet(this, expected1)) {
//                        throw AssertionError("dcss compelete cas2")
                    }
                }
            }

            return Unit
        }
    }

    private class Reference<T>(initial: T) {
        val v = atomic<Any?>(initial)

        fun myGetValue(): T {
            v.loop { cur ->
                when (cur) {
                    is dcssDescriptor<*, *> -> cur.complete2()
                    is casnDescriptor<*> -> cur.complete1() // getValue вызвал это, тут casn -> dcss -> casn(тк он лежит в самом низу)
                    else -> return cur as T
                }
            }
        }

        fun cas1(expected: T, upd: T): Any? {
            foo@while(true) {
                val old = v.value
                if (old != expected) {
                    return old
                }
                if (v.compareAndSet(expected, upd)) {
                    return expected
                } else {
                    continue@foo
                }
            }
        }

        fun setValue(upd: T) {
            v.loop { cur ->
                when (cur) {
                    is dcssDescriptor<*, *> -> cur.complete2()
                    is casnDescriptor<*> -> cur.complete1()
                    else -> if (v.compareAndSet(cur, upd)) return
                }
            }
        }

        fun compareAndSet(expected: T, upd: T): Boolean {
            return v.compareAndSet(expected, upd)
        }
    }
}