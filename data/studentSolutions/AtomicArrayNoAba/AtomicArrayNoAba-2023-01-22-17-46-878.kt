import kotlinx.atomicfu.*

import kotlinx.atomicfu.*
import Status.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    fun get(index: Int) =
        a[index].value

    fun set(index: Int, value: E) {
        a[index].value = value
    }

    fun cas(index: Int, expected: E, update: E): Boolean =
        a[index].cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        /*return if (index1 == index2) {
            if (expected1 == expected2) cas(index1, expected1, update2) else false
        } else {*/
        if (index1 == index2 && (expected1 != expected2 || update1 != update2)) return false
        //if (index1 == index2) return a[index1].cas(expected1, update1)
            val (index, expected, descriptor) = when {
                index1 > index2 ->
                    Triple(index2, expected2, CAS2Descriptor(a[index2], expected2, update2, a[index1], expected1, update1))
                else ->
                    Triple(index1, expected1, CAS2Descriptor(a[index1], expected1, update1, a[index2], expected2, update2))
            }
        descriptor.complete()
        return descriptor.status.value == SUCCESS
            /*return if (a[index].cas(expected, descriptor)) {
                descriptor.complete()
                descriptor.status.value == SUCCESS
            } else {
                false
            }*/
        //}
    }
}

@Suppress("UNCHECKED_CAST")
class Ref<E>(init: E) {
    val inValue = atomic<Any?>(init)
    var value: E
        get() {
            inValue.loop {
                when(it) {
                    is Descriptor<*> -> it.complete()
                    else -> return it as E
                }
            }
        }
        set(upd) {
            inValue.loop {
                when(it) {
                    is Descriptor<*> -> it.complete()
                    else -> if (inValue.compareAndSet(it, upd)) return
                }
            }
        }

    fun cas(expected: Any?, update: Any?): Boolean {
        inValue.loop {
            when(it) {
                is Descriptor<*> -> it.complete()
                expected -> if (inValue.compareAndSet(it, update)) return true
                else -> return false
            }
        }
    }
}

interface Descriptor<E>{
    fun complete(): Boolean
}
enum class Status {
    UNKNOWN, SUCCESS, FAIL
}

class RDCSSDescriptor<E>(
    private val value1: Ref<E>, private val expected1: E, private val update1: Any?,
    private val otherDescriptor: CAS2Descriptor<E>
) : Descriptor<E> {
    val status: AtomicRef<Status> = atomic(UNKNOWN)
    override fun complete(): Boolean {
        val curStatus = when (otherDescriptor.status.value) {
            UNKNOWN -> SUCCESS
            else -> FAIL
        }
        status.compareAndSet(UNKNOWN, curStatus)
        val update = when {
            status.value === SUCCESS -> update1
            else -> expected1
        }
        value1.inValue.compareAndSet(this, update)
        return false
    }
}

class CAS2Descriptor<E>(
    private val ref1: Ref<E>, private val expected1: E, private val update1: E,
    private val ref2: Ref<E>, private val expected2: E, private val update2: E,
) : Descriptor<E> {
    val status: AtomicRef<Status> = atomic(UNKNOWN)
    override fun complete() : Boolean {
        if (!ref1.cas(expected1, this)) return false
        val descriptor = RDCSSDescriptor(ref2, expected2, this, this)
        val res = ref2.inValue.value?.equals(this) ?: false || if (ref2.cas(expected2, descriptor)) {
            descriptor.complete()
            descriptor.status.value == SUCCESS
        } else {
            false
        }
        if (res) {
            status.compareAndSet(UNKNOWN, SUCCESS)
        } else {
            val outcome = if (ref2.inValue.value != this) FAIL else SUCCESS
            status.compareAndSet(UNKNOWN, outcome)
        }

        val (first, second) = when (this.status.value) {
            FAIL -> Pair(expected1, expected2)
            else -> Pair(update1, update2)
        }
        ref1.inValue.compareAndSet(this, first)
        ref2.inValue.compareAndSet(this, second)
        return status.value == SUCCESS
    }
}