@file:Suppress("UNCHECKED_CAST")

import kotlinx.atomicfu.*

class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)
    var value: T
        get() {
            while (true) {
                val cur = v.value

                when (cur) {
                    is CASNDescriptor<*> -> cur.complete()
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
                    is CASNDescriptor<*> -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd)) return
                }
            }
        }
}
enum class Status {
    Undecided,
    Success,
    Failed
}

class CASNDescriptor<E>(
    val a: Ref<Any?>, val expectA: E, val updateA: E,
    val b: Ref<Any?>, val expectB: E, val updateB: E
) {
    val status = atomic(Status.Undecided)

    fun complete() {
        if (b.v.compareAndSet(expectB, this) || b.v.value == this) {
            status.compareAndSet(Status.Undecided, Status.Success)
        } else {
            status.compareAndSet(Status.Undecided, Status.Failed)
        }

        when (status.value) {
            Status.Success -> {
                a.v.compareAndSet(this, updateA)
                b.v.compareAndSet(this, updateB)
            }

            Status.Failed -> {
//                val update = if (b.v.compareAndSet(expectB, updateB)) updateA else expectA
                if (b.v.value is CASNDescriptor<*>) {
                    status.compareAndSet(Status.Failed, Status.Success)
                    complete()
                } else {
                    a.v.compareAndSet(this, expectA)
                }
            }

            Status.Undecided -> complete()
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
                is CASNDescriptor<*> -> {
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
            val descriptor = CASNDescriptor(value1, expected1, update1, value2, expected2, update2)
            if (value1.v.compareAndSet(expected1, descriptor)) {
                descriptor.complete()

                when (descriptor.status.value) {
                    Status.Success -> return true
                    Status.Failed, Status.Undecided -> return false
                }
            } else {
                return false
            }
        }
    }
}