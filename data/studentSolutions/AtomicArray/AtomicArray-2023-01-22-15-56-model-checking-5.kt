import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = Array<Ref<E>>(size) { Ref(initialValue) }

    /*init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }*/

    fun get(index: Int) =
        a[index].value

    fun set(index: Int, value: E) {
        a[index].value = value
    }

    fun cas(index: Int, expected: E, update: E): Boolean =
        a[index].cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        fun getValues(): Triple<Int, E, CAS2Descriptor<E>> = when {
            index1 > index2 ->
                Triple(index2, expected2, CAS2Descriptor(a[index2], expected2, update2, a[index1], expected1, update1))
            else ->
                Triple(index1, expected1, CAS2Descriptor(a[index1], expected1, update1, a[index2], expected2, update2))
        }

        return if (index1 == index2) {
            if (expected1 == expected2) cas(index1, expected1, update2) else false
        } else {
            val (index, expected, descriptor) = getValues()
            if (a[index].cas(expected, descriptor)) {
                descriptor.complete()
                descriptor.cellStatus.value == Success
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

interface Descriptor<E> {
    fun complete()
}

sealed interface CellStatus
object Unknown : CellStatus
object Success : CellStatus
object Failure : CellStatus


class RDCSSDescriptor<E>(
    private val ref1: Ref<E>, private val expected1: E, private val update1: Any?,
    private val otherDescriptor: CAS2Descriptor<E>
) : Descriptor<E> {
    val cellStatus: AtomicRef<CellStatus> = atomic(Unknown)

    override fun complete() {
        val out = when (otherDescriptor.cellStatus.value) {
            Unknown -> Success
            else -> Failure
        }
        cellStatus.compareAndSet(Unknown, out)
        val update = when {
            cellStatus.value === Success -> update1
            else -> expected1
        }
        ref1.inValue.compareAndSet(this, update)
    }
}

class CAS2Descriptor<E>(
    private val ref1: Ref<E>, private val expected1: E, private val update1: E,
    private val ref2: Ref<E>, private val expected2: E, private val update2: E
) : Descriptor<E> {
    val cellStatus: AtomicRef<CellStatus> = atomic(Unknown)

    fun dcss(
        ref1: Ref<E>, expected1: E, update1: Any?,
        otherDescriptor: CAS2Descriptor<E>
    ): Boolean {
        val descriptor = RDCSSDescriptor(ref1, expected1, update1, otherDescriptor)
        return ref1.inValue.value?.equals(update1) ?: false || if (ref1.cas(expected1, descriptor)) {
            descriptor.complete()
            descriptor.cellStatus.value == Success
        } else {
            false
        }
    }

    override fun complete() {
        val descriptor = RDCSSDescriptor(ref2, expected2, this, this)
        val res = if(ref2.inValue.value?.equals(update2) == null)
            false
        else if (ref2.cas(expected2, descriptor)) {
            descriptor.complete()
            descriptor.cellStatus.value == Success
        } else {
            false
        }
        //val res = dcss(ref2, expected2, this, this)

        if (res) {
            this.cellStatus.compareAndSet(Unknown, Success)
        } else {
            val outcome = if (ref2.inValue.value != this) Failure else Success
            this.cellStatus.compareAndSet(Unknown, outcome)
        }

        val (first, second) = when (this.cellStatus.value) {
            Failure -> Pair(expected1, expected2)
            else -> Pair(update1, update2)
        }
        ref1.inValue.compareAndSet(this, first)
        ref2.inValue.compareAndSet(this, second)
    }
}