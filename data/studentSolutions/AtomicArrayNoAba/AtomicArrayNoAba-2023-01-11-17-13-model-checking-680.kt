@file:Suppress("UNCHECKED_CAST")

import kotlinx.atomicfu.*

sealed interface NodeValue {
    data class Descriptor<E>(
        var a: E, val expectA: E, val updateA: E,
        var b: E, val expectB: E, val updateB: E
    ): NodeValue {

        val atomicA = atomic(a)
        val atomicB = atomic(b)
        fun complete() : Boolean {
            val update = if (atomicB.compareAndSet(expectB, updateB)) updateA else expectA

            return atomicA.compareAndSet(this.atomicA.value, update)
        }
    }

    data class SimpleValue<E>(val value: E): NodeValue {
        val atomicValue: AtomicRef<E> = atomic(value)
    }
}

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<NodeValue>(size)

    init {
        for (i in 0 until size) a[i].value = NodeValue.SimpleValue(initialValue)
    }

    fun get(index: Int): E {
        while (true) {
            val value = a[index].value

            when (value) {
                is NodeValue.Descriptor<*> -> value.complete()
                else -> {
                    val returnValue = value as NodeValue.SimpleValue<*>
                    return returnValue.atomicValue.value as E
                }
            }
        }
    }
    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(NodeValue.SimpleValue(expected), NodeValue.SimpleValue(update))

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {

        val value1 = a[index1].value as NodeValue.SimpleValue<E>
        val value2 = a[index2].value as NodeValue.SimpleValue<E>

        while (true) {
            val descriptor = NodeValue.Descriptor(value1.atomicValue.value, expected1, update1, value2.atomicValue.value, expected2, update2)
            if (a[index1].compareAndSet(NodeValue.SimpleValue(expected1), descriptor)) {
                if (a[index2].compareAndSet(NodeValue.SimpleValue(expected2), NodeValue.SimpleValue(update2))) {
                    return descriptor.complete()
                } else {
                    a[index1].compareAndSet(descriptor, NodeValue.SimpleValue(expected1))
                }
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