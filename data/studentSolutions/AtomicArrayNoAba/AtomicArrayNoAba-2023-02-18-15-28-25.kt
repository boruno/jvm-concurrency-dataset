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

class RDCSSDescriptor<E>(
    private val a: Ref<E>, private val expectA: E, private val updateA: Any?,
    private val b: Ref<E>, private val expectB: E, private val updateB: Any?
) : Descriptor<E> {
    override fun complete() {
        val update: Any?
        val update2: Any?
        if (status.value == "undecided") {
            if (b.value == expectB && a.value == expectA) {
                status.compareAndSet("undecided", "success")
                update = updateA
                update2 = updateB

            } else {
                status.compareAndSet("undecided", "fail")
                update = expectA
                update2 = expectB
            }
            if (status.value == "success") {
                a.v.compareAndSet(this, update)
                b.v.compareAndSet(this, update2)
            }
        }
    }
}

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
            val d = RDCSSDescriptor(Ref(a[index1]), expected1, update1, Ref(a[index2]), expected2, update2)
            if (a[index1]?.cas(expected1, d) == true && a[index2]?.cas(expected2, d) == true) {
                d.complete()
            }
            return a[index1] == update1 && a[index2] == update2
        }
    }
}
