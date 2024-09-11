import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.get() as E


    fun dcssMod(a: Ref, expectA: E, updateA: E, b: Ref, expectB: E) : Boolean {
        if (a.get() == expectA && b.get() == expectB) {
            if (a.v.compareAndSet(expectA, updateA)) {
                return true
            }
            return false
        } else {
            return false
        }
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(Ref(expected), Ref(update))

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {


        val first = a[index1].value!!
        val second = a[index2].value!!

        if (index1 > index2) {
            val descr = CASNDescriptor(first, expected1, update1, second, expected2)

            if (!first.v.compareAndSet(expected1, descr)) {
                return false
            }

            if (descr.outcome.value == "UNDECIDED") {
                if (dcssMod(second, expected2, update2, first, expected1)) {
                    if (descr.outcome.compareAndSet("UNDECIDED", "SUCCESS")) {
//                    cas(index1, descr, update1)
//                    cas(index2, descr, update2)
                        first.v.compareAndSet(descr, update1)
                        second.v.compareAndSet(descr, update2)
                        return true
                    }
                } else {
                    descr.outcome.compareAndSet("UNDECIDED", "FAILED")
                    //cas(index1, descr, expected1)
                    first.v.compareAndSet(descr, expected1)
                    return false
                }
            }
            return false
        } else {
            val descr = CASNDescriptor(second, expected2, update2, first, expected1)

            if (!second.v.compareAndSet(expected2, descr)) {
                return false
            }

            if (descr.outcome.value == "UNDECIDED") {
                if (dcssMod(first, expected1, update1, second, expected2)) {
                    if (descr.outcome.compareAndSet("UNDECIDED", "SUCCESS")) {
                        second.v.compareAndSet(descr, update2)
                        first.v.compareAndSet(descr, update1)
                        return true
                    }
                } else {
                    descr.outcome.compareAndSet("UNDECIDED", "FAILED")
                    second.v.compareAndSet(descr, expected2)
                    return false
                }
            }
            return false
        }
    }
}

class Ref (initial: Any) {
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
            }
        }
    }
}

abstract class Descriptor {

    abstract fun complete()
}

class CASNDescriptor(
    val a: Ref, val expectA: Any, val updateA: Any, val b: Ref, val expectB: Any
) : Descriptor() {

    val outcome = atomic<String>("UNDECIDED")
    override fun complete() {
        val update: Any
        if (b.get() === expectB) {
            update = updateA
        } else {
            update = expectA
        }
        a.v.compareAndSet(this, update)
    }
}