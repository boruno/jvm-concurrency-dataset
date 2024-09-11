import kotlinx.atomicfu.*

interface Descriptor<E> {
    fun complete()

}

class Ref<E>(initial: E) {
    val v = atomic<Any?>(initial)

    @Suppress("UNCHECKED_CAST")
    var value: E
        get() {
            while (true) {
                val cur = v.value
                when (cur) {
                    is Descriptor<*> -> cur.complete()
                    else -> return cur as E
                }
            }
        }
        set(upd) {
            while (true) {
                val cur = v.value
                when (cur) {
                    is Descriptor<*> -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd))
                        return
                }
            }
        }

    fun cas(expected: Any?, update: Any?): Boolean {
        while (true) {
            when (val cur = v.value) {
                is Descriptor<*> -> cur.complete()
                expected -> {
                    val res = v.compareAndSet(cur, update)
                    if (res) {
                        return true
                    }
                }
                else -> return false
            }
        }
    }
}

class RDCSSDescriptor<E>(
    private val a: Ref<E>, private val expectA: E, private val updateA: Any?,
    private val b: Ref<E>, private val expectB: E
) : Descriptor<E> {
    val status = atomic("undecided")
    override fun complete() {
//        a.cas(expectA, RDCSSDescriptor(Ref(a[index1]), expected1, update1, Ref(a[index2]), expected2))

        val update =
            if (b.value == expectB) {
                status.compareAndSet("undecided", "success")
                updateA
            } else {
                status.compareAndSet("undecided", "fail")
                expectA
            }
        if (status.value == "success") {
            a.v.compareAndSet(this, update)
        }
    }
}

//fun <E> dcss(a: Ref<E>, expectA: E, updateA: Any?,
//             b: Ref<E>, expectB: E) {
//    if ()
//}


class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = arrayOfNulls<Ref<E>>(size)
//    var status = atomic("undecided")

    init {
        (0 until size).forEach { i ->
            a[i] = Ref(initialValue)
        }
    }

    fun get(index: Int): E = a[index]?.value as E

    fun set(index: Int, value: E) {
        a[index]?.value = value
    }

    fun cas(index: Int, expected: E, update: E): Boolean = a[index]?.cas(expected, update) ?: false

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 >= a.size || index2 >= a.size || index2 < 0 || index1 < 0) return false

        return if (index1 == index2) {
            if (expected1 == expected2) cas(index1, expected1, update2) else false
        } else {
            if (a[index1] == expected1) {
                val d = RDCSSDescriptor(Ref(a[index1]), expected1, update1, Ref(a[index2]), expected2)
                d.complete()
                if (d.status.value != "success") {
                    return false
                }
            } else {
                return false
            }
//            a[index1]?.cas(expected1, d)
            if (a[index2] == expected2) {
                val d2 = RDCSSDescriptor(Ref(a[index2]), expected2, update2, Ref(a[index1]), update1)
                d2.complete()
                if (d2.status.value != "success") {
                    return false
                }
            } else {
                return false
            }
//            if (a[index1] == expected1) {
////                a[index1] = RDCSSDescriptor(a[index1], expected1, update1, a[index2], expected2);
//            }
            return true
        }
    }
}
