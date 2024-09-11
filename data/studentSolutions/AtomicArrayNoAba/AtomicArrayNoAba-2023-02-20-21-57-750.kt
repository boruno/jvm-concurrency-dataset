import kotlinx.atomicfu.*

interface Descriptor<E> {
    fun complete()
}

class Ref<E>(initial: E) {
    val v = atomic<Any?>(initial)
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


class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    inner class RDCSSDescriptor<E>(
        private val a: Int, private val expectA: E, private val updateA: Any?,
        private val b: Int, private val expectB: E, private val updateB: Any?
    ) : Descriptor<E> {
        val status = atomic("undecided")
        override fun complete() {
            if (status.value == "undecided" && array[b]!!.v.compareAndSet(expectB, this)) {
                status.compareAndSet("undecided", "success")
                array[a]!!.v.compareAndSet(this, updateA)
                array[b]!!.v.compareAndSet(this, updateB)
            } else if (array[b]!!.v.value == this) {
                status.compareAndSet("undecided", "success")
                array[a]!!.v.compareAndSet(this, updateA)
                array[b]!!.v.compareAndSet(this, updateB)
            } else if (status.value != "success") {
                status.compareAndSet("undecided", "failed")
                array[a]!!.v.compareAndSet(this, expectA)
            }
        }
    }

    private val array = arrayOfNulls<Ref<E>>(size)

    init {
        (0 until size).forEach { i ->
            array[i] = Ref(initialValue)
        }
    }

    fun get(index: Int): E {
        return array[index]!!.value
    }

    fun set(index: Int, value: E) {
        array[index]!!.value = value
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        return array[index]!!.cas(expected, update)
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 >= array.size || index2 >= array.size || index2 < 0 || index1 < 0) return false

        if (index1 == index2) {
            return if (expected1 == expected2) cas(index1, expected1, ((update2 as Int) +1 ) as E) else false
        } else {
            if (index1 > index2) {
                val d = RDCSSDescriptor(index2, expected2, update2, index1, expected1, update1)
                if (array[index2]!!.v.compareAndSet(expected2, d)) {
                    d.complete()
                } else {
                    return false
                }
                return d.status.value == "success"
            } else {
                val d = RDCSSDescriptor(index1, expected1, update1, index2, expected2, update2)
                if (array[index1]!!.v.compareAndSet(expected1, d)) {
                    d.complete()
                } else {
                    return false
                }
                return d.status.value == "success"
            }
        }
    }
}
