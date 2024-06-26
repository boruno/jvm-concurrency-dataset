import kotlinx.atomicfu.*

enum class Status {
    ACTIVE,
    SUCCESSFUL,
    FAILED
}

class CAS2Descriptor<E>(val ref1: Ref<E>, val expected1: E, val update1: E,
                        val ref2: Ref<E>, val expected2: E, val update2: E) {
    private val status = atomic(Status.ACTIVE)

    fun getStatus(): Status {
        return status.value
    }

    fun complete() {
        if (ref2.v.value == this || ref2.cas(expected2, this)) {
            status.compareAndSet(Status.ACTIVE, Status.SUCCESSFUL)
            ref1.v.compareAndSet(this, update1)
            ref2.v.compareAndSet(this, update2)
        } else {
            status.compareAndSet(Status.ACTIVE, Status.FAILED)
            ref1.v.compareAndSet(this, expected1)
            ref2.v.compareAndSet(this, expected2)
        }
    }

    fun casStatus(expected: Status, update: Status): Boolean {
        return status.compareAndSet(expected, update)
    }
}

class Ref<E>(initial: E) {
    val v = atomic<Any?>(initial)

    fun cas(expected: E, update: Any?): Boolean {
        while (true) {
            if (v.compareAndSet(expected, update)) {
                return true
            }
            if (value != expected) {
                return false
            }
        }
    }

    var value: E
        get() {
            v.loop {
                curCell -> when(curCell) {
                    is CAS2Descriptor<*> -> curCell.complete()
                    else -> return curCell as E
                }
            }
        }

        set(update) {
            v.loop {
                curCell -> when(curCell) {
                    is CAS2Descriptor<*> -> curCell.complete()
                    else -> if (v.compareAndSet(curCell, update)) return
                }
            }
        }
}

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size){Ref(initialValue)}

    fun get(index: Int) = a[index].value

    private fun cas2(desc: CAS2Descriptor<E>): Boolean {
        if (desc.ref1.cas(desc.expected1, desc)) {
            desc.complete()
            return desc.getStatus() == Status.SUCCESSFUL
        }
        return false
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        return a[index].cas(expected, update)
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2 && expected1 === expected2 && update1 === update2) {
            if (expected1 is Int) {
                val update = expected1 + 2
                return cas(index1, expected1, (update as E))
            }
        }
        val desc = if (index1 < index2)
                        CAS2Descriptor(a[index1], expected1, update1,
                                       a[index2], expected2, update2)
                   else
                        CAS2Descriptor(a[index2], expected2, update2,
                                       a[index1], expected1, update1)
        return cas2(desc)
    }
}