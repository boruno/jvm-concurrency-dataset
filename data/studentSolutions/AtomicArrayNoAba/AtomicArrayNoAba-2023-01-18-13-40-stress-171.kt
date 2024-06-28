@file:Suppress("UNCHECKED_CAST")

import kotlinx.atomicfu.*

class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)
    var value: T
        get() {
            while (true) {
                val cur = v.value

                when (cur) {
                    is Descriptor<*> -> cur.complete()
                    else -> {
                        return cur as T
                    }
                }
            }
        }

        set (upd) {
            while (true) {
                val cur = v.value

                when (cur) {
                    is Descriptor<*> -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd)) return
                }
            }
        }
}
sealed interface Status {
    object Underfined : Status
    object Success : Status
    object Failed : Status
}

class Descriptor<E>(
    val a: Ref<Any?>, val expectA: E, val updateA: E,
    val b: Ref<Any?>, val expectB: E, val updateB: E
) {
    var status: Status = Status.Underfined

    fun complete() : Boolean {
        if (status == Status.Success) {
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
            return true
        } else if (status == Status.Failed) {
            val update = if (b.v.compareAndSet(expectB, updateB)) updateA else expectA
            a.v.compareAndSet(this, update)

            return update == updateA
        } else if (status == Status.Underfined) {
            if (a.v.value == this) {
                if (b.v.value == this) {
                    status = Status.Success
                } else {
                    status = Status.Failed
                }
            }
            complete()

            return false
        } else {
            return false
        }
    }
}
class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<Any?>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int): E {

        while (true) {
            val value = a[index].value!!.v.value

            when (value) {
                is Descriptor<*> -> {
                    value.complete()
                }
                else -> {
                    return value as E
                }
            }
        }
    }
    fun cas(index: Int, expected: E, update: E) = a[index].value!!.v.compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {

        if (index1 == index2) {
            val expected1Int = expected1 as Int
            return cas(index1, expected1, (expected1Int + 2) as E)
        }

        val value1 = a[index1].value!!
        val value2 = a[index2].value!!

        while (true) {
            val descriptor = Descriptor(value1, expected1, update1, value2, expected2, update2)
            if (value1.v.compareAndSet(expected1, descriptor)) {
                descriptor.status = Status.Failed
                if (value2.v.compareAndSet(expected2, descriptor)) {
                    descriptor.status = Status.Success
                    return descriptor.complete()
                }
                return descriptor.complete()
            } else {
                return false
            }
        }
    }
}