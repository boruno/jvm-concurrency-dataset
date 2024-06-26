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
            v.value = upd
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
//            println("\n----completion success----")
//            println(a.v.value)
//            println(b.v.value)
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
            return true
        } else if (status == Status.Failed) {
            if (b.v.value == this) {
                status = Status.Success
                complete()
            }
            val update = if (b.v.compareAndSet(expectB, updateB)) updateA else expectA
            return a.v.compareAndSet(this, update)
        } else {
            if (a.v.value == this) {
                if (b.v.value == this) {
                    status = Status.Success
                } else {
                    status = Status.Failed
                }
            }
            complete()
//            if (a.v.compareAndSet(this, updateA)) {
//                b.v.compareAndSet(this, updateB)
//                return true
//            } else {
                return false
//            }
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
//                    println("\n----get before completion----")
//                    println(value)
                    value.complete()
//                    println("\n----get after completion----")
//                    println(value)
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

        val value1 = a[index1].value!!
        val value2 = a[index2].value!!

        while (true) {
            val descriptor = Descriptor(value1, expected1, update1, value2, expected2, update2)
            if (value1.v.compareAndSet(expected1, descriptor)) {
                if (value2.v.compareAndSet(expected2, descriptor)) {
                    descriptor.status = Status.Success
                }
                else {
                    descriptor.status = Status.Failed
                }
                return descriptor.complete()
            } else {
                return descriptor.complete()
            }
        }

        // this implementation is not linearizable,
        // a multi-word CAS algorithm should be used here.

//        if (a[index1].value != expected1 || a[index2].value != expected2) return false
//        a[index1].value = update1
//        a[index2].value = update2
//        return true
    }
}