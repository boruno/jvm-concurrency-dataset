import kotlinx.atomicfu.*
import Status.*
import kotlin.math.exp

/*
class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = Array<Ref<E>>(size) { Ref(initialValue) }

    */
/*init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }*//*


    fun get(index: Int) =
        a[index].value

    fun set(index: Int, value: E) {
        a[index].value = value
    }

    fun cas(index: Int, expected: E, update: E): Boolean =
        a[index].cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        return if (index1 == index2) {
            if (expected1 == expected2) cas(index1, expected1, update2) else false
        } else {
            val (index, expected, descriptor) = when {
                index1 > index2 ->
                    Triple(index2, expected2, CAS2Descriptor(a[index2], expected2, update2, a[index1], expected1, update1))
                else ->
                    Triple(index1, expected1, CAS2Descriptor(a[index1], expected1, update1, a[index2], expected2, update2))
            }
            if (a[index].cas(expected, descriptor)) {
                descriptor.complete()
                descriptor.status.value == SUCCESS
            } else {
                false
            }
        }
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
    fun complete()
}
enum class Status {
    UNKNOWN, SUCCESS, FAIL
}

class RDCSSDescriptor<E>(
    private val value1: Ref<E>, private val expected1: E, private val update1: Any?,
    private val otherDescriptor: CAS2Descriptor<E>
) : Descriptor<E> {
    val status: AtomicRef<Status> = atomic(UNKNOWN)
    override fun complete() {
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
    }
}

class CAS2Descriptor<E>(
    private val ref1: Ref<E>, private val expected1: E, private val update1: E,
    private val ref2: Ref<E>, private val expected2: E, private val update2: E,
) : Descriptor<E> {
    val status: AtomicRef<Status> = atomic(UNKNOWN)
    override fun complete() {
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
    }
}*/
class AtomicArray<E>(size: Int, initialValue: E) {
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
        return if (index1 == index2) {
            if (expected1 == expected2) cas(index1, expected1, update2) else false
        } else {
            /*val (index, expected, descriptor) = when {
                index1 > index2 ->
                    Triple(index2, expected2, CAS2Descriptor(a[index2], expected2, update2, a[index1], expected1, update1))
                else ->
                    Triple(index1, expected1, CAS2Descriptor(a[index1], expected1, update1, a[index2], expected2, update2))
            }
            if (a[index].cas(expected, descriptor)) {
                descriptor.complete()
                descriptor.status.value == SUCCESS
            } else {
                false
            }*/
            return when {
                index1 > index2 ->
                    CAS2Descriptor(a[index2], expected2, update2, a[index1], expected1, update1).run(a[index2], expected2)
                else ->
                    CAS2Descriptor(a[index1], expected1, update1, a[index2], expected2, update2).run(a[index1], expected1)
            }
            //return descriptor.run(a[index], expected)
        }
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
    fun complete()
}
enum class Status {
    UNKNOWN, SUCCESS, FAIL
}

class RDCSSDescriptor<E>(
    private val value1: Ref<E>, private val expected1: E, private val update1: Any?,
    private val otherDescriptor: CAS2Descriptor<E>
) : Descriptor<E> {
    val status: AtomicRef<Status> = atomic(UNKNOWN)
    override fun complete() {
        val curStatus = when (otherDescriptor.status.value) {
            UNKNOWN -> SUCCESS
            else -> FAIL
        }
        status.compareAndSet(UNKNOWN, curStatus)
        val update = when (status.value) {
            SUCCESS -> update1
            else -> expected1
        }
        value1.inValue.compareAndSet(this, update)
    }
}

class CAS2Descriptor<E>(
    private val value1: Ref<E>, private val expected1: E, private val update1: E,
    private val value2: Ref<E>, private val expected2: E, private val update2: E,
) : Descriptor<E> {
    val status: AtomicRef<Status> = atomic(UNKNOWN)
    fun run(value: Ref<E>, expected: E): Boolean {
        return if (value.cas(expected, this)) {
            this.complete()
            this.status.value == SUCCESS
        } else {
            false
        }
    }
    override fun complete() {
        val descriptor = RDCSSDescriptor(value1, expected2, this, this)

        val res = value2.inValue.value==this || if (value2.cas(expected2, descriptor)) {
            descriptor.complete()
            descriptor.status.value == SUCCESS
        } else {
            false
        }
        if (res) {
            status.compareAndSet(UNKNOWN, SUCCESS)
        } else {
            val outcome = if (value2.inValue.value != this) FAIL else SUCCESS
            status.compareAndSet(UNKNOWN, outcome)
        }

        val (first, second) = when (this.status.value) {
            FAIL -> Pair(expected1, expected2)
            else -> Pair(update1, update2)
        }
        value1.inValue.compareAndSet(this, first)
        value2.inValue.compareAndSet(this, second)
    }
}