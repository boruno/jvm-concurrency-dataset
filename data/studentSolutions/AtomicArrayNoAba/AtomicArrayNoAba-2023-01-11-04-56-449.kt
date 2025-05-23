import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.get() as E


//    fun dcssMod(a: Ref, expectA: E, updateA: E, b: Ref, expectB: E) : Boolean {
//        if (a.get() == expectA && b.get() == expectB) {
//            a.set(updateA)
//            return true
//        } else {
//            return false
//        }
//    }

    fun dcssMod(a: Ref, expectA: E, updateA: Any, b: Ref, expectB: E, aVal: Any, bVal: Any): Boolean {
        if (aVal == expectA && bVal == expectB) {
            a.set(updateA)
            return true
        } else {
            return false
        }
    }


    fun cas(index: Int, expected: E, update: E): Boolean =
        a[index].value!!.v.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {

        val first: Ref
        val second: Ref
        val exp1: E
        val exp2: E
        val upd1: E
        val upd2: E
        if (index1 < index2) {
            first = a[index1].value!!
            second = a[index2].value!!
            exp1 = expected1
            exp2 = expected2
            upd1 = update1
            upd2 = update2
        } else {
            first = a[index2].value!!
            second = a[index1].value!!
            exp1 = expected2
            exp2 = expected1
            upd1 = update2
            upd2 = update1
        }

        val firstVal = first.get()
        val secondVal = second.get()

        val descr = CASNDescriptor(first, exp1, upd1, second, exp2, secondVal)
        val descr2 = CASNDescriptor(second, exp2, upd2, first, exp1, firstVal)
//        if (firstVal == exp1) {
//            if (!first.v.compareAndSet(exp1, descr)) {
//                return false
//            }
//        } else {
//            return false
//        }

        if (!first.v.compareAndSet(exp1, descr)) {
            return false
        }

        if (descr.outcome.value == "UNDECIDED") {
            //if (dcssMod(second, exp2, upd2, first, exp1, secondVal , firstVal )) {
            if (dcssMod(second, exp2, descr2, first, exp1, secondVal, firstVal)) {
                if (descr.outcome.compareAndSet("UNDECIDED", "SUCCESS")) {
//                    cas(index1, descr, update1)
//                    cas(index2, descr, update2)
                    first.v.compareAndSet(descr, upd1)
                    second.v.compareAndSet(descr2, upd2)
                    return true
                } else {
                    descr2.outcome.compareAndSet("UNDECIDED", descr.outcome.value)
                    if (descr.outcome.value == "SUCCESS") {
                        first.v.compareAndSet(descr, upd1)
                        second.v.compareAndSet(descr2, upd2)
                    } else {
                        first.v.compareAndSet(descr, exp1)
                        second.v.compareAndSet(descr2, exp2)
                    }
                    return descr.outcome.value == "SUCCESS"
                }
            } else {
                descr.outcome.compareAndSet("UNDECIDED", "FAILED")
                //cas(index1, descr, expected1)
                first.v.compareAndSet(descr, exp1)
                return false
            }
        }
        descr2.outcome.compareAndSet("UNDECIDED", descr.outcome.value)
        if (descr.outcome.value == "SUCCESS") {
            second.v.compareAndSet(descr2, upd2)
            first.v.compareAndSet(descr, upd1)
        } else {
            second.v.compareAndSet(descr2, exp2)
            first.v.compareAndSet(descr, exp1)
        }
        return descr.outcome.value == "SUCCESS"

    }
}

class Ref(initial: Any) {
    val v = atomic<Any>(initial)

    fun get(): Any {
        while (true) {
            val cur = v.value
            if (cur is Descriptor) {
                cur.complete()
            } else {
                return cur
            }
        }
    }

    fun set(upd: Any) {
        while (true) {
            val cur = v.value
            if (cur is Descriptor) {
                cur.complete()
            } else {
                if (v.compareAndSet(cur, upd)) {
                    return
                }
//                v.compareAndSet(cur, upd)
//                return
            }
        }
    }
}

abstract class Descriptor {

    abstract fun complete()
}

class CASNDescriptor(
    val a: Ref, val expectA: Any, val updateA: Any, val b: Ref, val expectB: Any, val bVal: Any
) : Descriptor() {

    val outcome = atomic<String>("UNDECIDED")
    override fun complete() {
        val update: Any
        val flag: Boolean

//        if (outcome.value == "SUCCESS") {
//            a.v.compareAndSet(this, updateA)
//        } else if (outcome.value == "FAILED") {
//            a.v.compareAndSet(this, expectA)
//        } else {

            if (bVal == expectB) {
                outcome.compareAndSet("UNDECIDED", "SUCCESS")
                update = updateA
                flag = true
            } else {
                outcome.compareAndSet("UNDECIDED", "FAILED")
                update = expectA
                flag = false
            }
            a.v.compareAndSet(this, update)
//            if (flag) {
//                outcome.compareAndSet("UNDECIDED", "SUCCESS")
//            } else {
//                outcome.compareAndSet("UNDECIDED", "FAILED")
//            }
      //  }
    }
}