@file:Suppress("UNCHECKED_CAST")

import kotlinx.atomicfu.*

sealed interface NodeValue {}
class Descriptor<E>(
    a: E, val expectA: E, val updateA: E,
    b: E, val expectB: E
): NodeValue {

    val a = atomic(a)
    val b = atomic(b)
    fun complete() {
        val update = if (b.value === expectB) updateA else expectA

        a.compareAndSet(this.a.value, update)
    }
}

class SimpleValue<E>(value: E): NodeValue {
    val value: AtomicRef<E> = atomic(value)
}

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<NodeValue>(size)

    init {
        for (i in 0 until size) a[i].value = SimpleValue(initialValue)
    }

    fun get(index: Int): E {
        while (true) {
            val value = a[index].value

            when (value) {
                is Descriptor<*> -> value.complete()
                else -> {
                    val returnValue = value as SimpleValue<*>
                    return returnValue.value.value as E
                }
            }
        }
    }
    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(SimpleValue(expected), SimpleValue(update))

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {

        val value1 = a[index1].value as SimpleValue<E>
        val value2 = a[index2].value as SimpleValue<E>

        while (true) {
            val descriptor = Descriptor(value1.value.value, expected1, update1, value2.value.value, expected2)
            if (a[index1].compareAndSet(SimpleValue(expected1), descriptor)) {
                if (a[index2].value == expected2) {
                    a[index1].compareAndSet(descriptor, SimpleValue(update1))
                    return true
                } else {
                    a[index1].compareAndSet(descriptor, SimpleValue(expected1))
                    return false
                }
            } else {
                return false
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