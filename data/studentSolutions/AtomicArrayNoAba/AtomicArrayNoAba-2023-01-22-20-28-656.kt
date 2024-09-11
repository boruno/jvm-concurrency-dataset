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
        return if (index1 == index2) {
            if (expected1 == expected2) cas(index1, expected1, update2) else false
        } else {
            val part1 = PartOfCas(a[index1], expected1, update1)
            val part2 = PartOfCas(a[index2], expected2, update2)
            return when {
                index1 > index2 ->
                    CAS2Descriptor(arrayOf(part2, part1)).complete()
                else ->
                    CAS2Descriptor(arrayOf(part1, part2)).complete()
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
    fun complete() : Boolean
}
enum class Status {
    UNKNOWN, SUCCESS, FAIL
}

class RDCSSDescriptor<E>(
    private val value1: Ref<E>, private val expected1: E, private val update1: Any?,
    private val otherDescriptor: CAS2Descriptor<E>
) : Descriptor<E> {
    private val status: AtomicRef<Status> = atomic(UNKNOWN)
    override fun complete() : Boolean {
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
        return status.value == SUCCESS
    }
}

class PartOfCas<E>(val value: Ref<E>, val expected: E, val update: E)

class CAS2Descriptor<E>(
    val parts: Array<PartOfCas<E>> ) : Descriptor<E> {
    val status: AtomicRef<Status> = atomic(UNKNOWN)
    fun run(value: Ref<E>, expected: E): Boolean {
        return if (value.cas(expected, this)) this.complete() else false
    }
    override fun complete() : Boolean{
        val part1 = parts[0]
        val part2 = parts[1]
        val res = part2.value.inValue.value == this || part2.value.cas(part2.expected, this)
        if (res) {
            status.compareAndSet(UNKNOWN, SUCCESS)
        } else {
            val outcome = if (part2.value.inValue.value == this) SUCCESS else FAIL
            status.compareAndSet(UNKNOWN, outcome)
        }
        val success = status.value == SUCCESS
        val upd1 = if (success) part1.update else part1.expected
        val upd2 = if (success) part2.update else part2.expected
        part1.value.inValue.compareAndSet(this, upd1)
        part2.value.inValue.compareAndSet(this, upd2)
        return status.value == SUCCESS
        /*var succ = true
        for (i in 0..1) {
            while(true) {

                val part = parts[i]
                val cur = part.value
                if(cur == this) continue
                if (cur != part.expected) {
                    succ = false
                    break
                }
                if (status.value != UNKNOWN) break
                if (part.value.inValue.compareAndSet(cur, this)) break
            }
        }
        val stat = if (succ) SUCCESS else FAIL
        status.compareAndSet(UNKNOWN, stat)
        val success = status.value == SUCCESS
        for (i in 0..1) {
            val part = parts[i]
            val upd = if (success) part.update else part.expected
            part.value.inValue.compareAndSet(this, upd)
        }
        return status.value == SUCCESS*/
        /*val descriptor = RDCSSDescriptor(value2, expected2, this, this)
        val res = value2.inValue.value == this || if (value2.cas(expected2, descriptor)) {
            descriptor.complete()
        } else {
            false
        }
        if (res) {
            status.compareAndSet(UNKNOWN, SUCCESS)
        } else {
            val outcome = if (value2.inValue.value == this) SUCCESS else FAIL
            status.compareAndSet(UNKNOWN, outcome)
        }
        val success = status.value == SUCCESS
        val upd1 = if (success) update1 else expected1
        val upd2 = if (success) update2 else expected2
        value1.inValue.compareAndSet(this, upd1)
        value2.inValue.compareAndSet(this, upd2)
        return status.value == SUCCESS*/
    }
}