import kotlinx.atomicfu.*

class Descriptor<E> {
    fun complete() {
        return
    }
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
            val cur = v.value
            when (cur) {
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

val status = atomic("undecided")

//class RDCSSDescriptor<E>(
//    private val a: Ref<E>, private val expectA: E, private val updateA: Any?,
//    private val b: Ref<E>, private val expectB: E
//) : Descriptor<E> {
//    override fun complete() {
//        val update =
//            if (b.value == expectB) {
//                status.compareAndSet("undecided", "success")
//                updateA
//            } else {
//                status.compareAndSet("undecided", "fail")
//                expectA
//            }
//        if (status.value == "success") {
//            a.v.compareAndSet(this, update)
//        }
//    }
//}

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = arrayOfNulls<Ref<E>>(size)

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
//            val d = RDCSSDescriptor(Ref(a[index1]), expected1, update1, Ref(a[index2]), expected2)
            if (a[index1]?.cas(expected1, Descriptor<E>()) == true) {
                if (status.value == "undecided") {
                    if (a[index2]?.cas(expected2, Descriptor<E>()) == true) {
                        status.compareAndSet("undecided", "success")
                        if (a[index1]?.cas(Descriptor<E>(), update1) == true && a[index2]?.cas(Descriptor<E>(), update2) == true) {
                            return true
                        }
                    }
                }
            }
            return false
        }
    }
}
