import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value


    fun dcssMod(indexA: Int, expectA: E, updateA: E, indexB: Int, expectB: E) : Boolean {
        if (a[indexA].value == expectA && a[indexB].value == expectB) {
            a[indexA].value = Ref(updateA)
            return true
        } else {
            return false
        }
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(Ref(expected), Ref(update))

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {

        val descr = CASNDescriptor(Ref(index1), expected1, update1, Ref(index2), expected2)
//        if (a[index1].compareAndSet(expected1, descr)) {
//            return false
//        }

        if (!a[index1].compareAndSet(Ref(expected1), Ref(descr))) {
            return false
        }

        if (descr.outcome.value == "UNDECIDED") {
            if (dcssMod(index2, expected2, update2, index1, expected1)) {
                if (descr.outcome.compareAndSet("UNDECIDED", "SUCCESS")) {
//                    cas(index1, descr, update1)
//                    cas(index2, descr, update2)
                    a[index1].compareAndSet(Ref(descr), Ref(update1))
                    a[index2].compareAndSet(Ref(descr), Ref(update2))
                    return true
                }
            } else {
                descr.outcome.compareAndSet("UNDECIDED", "FAILED")
                //cas(index1, descr, expected1)
                a[index1].compareAndSet(Ref(descr), Ref(expected1))
                return false
            }
        }
        return false
    }
}

class Ref<E: Any> (initial: Any) {
    val v = atomic<Any?>(initial)

    var value: E
        get() {
            while (true) {
                val cur = v.value
                if (cur is Descriptor) {
                    cur.complete()
                } else {
                    return cur as E
                }
            }
        }

        set(upd) {
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

class CASNDescriptor<E>(
    val a: Ref<Any>, val expectA: E, val updateA: E, val b: Ref<Any>, val expectB: E
) : Descriptor() {

    val outcome = atomic<String>("UNDECIDED")
    override fun complete() {
        val update: E
        if (b.value === expectB) {
            update = updateA
        } else {
            update = expectA
        }
        a.v.compareAndSet(this, update)
    }
}