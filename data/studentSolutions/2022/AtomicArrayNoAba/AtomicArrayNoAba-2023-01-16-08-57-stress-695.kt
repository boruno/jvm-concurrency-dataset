import kotlinx.atomicfu.*

enum class Status {
    ACTIVE,
    SUCCESSFUL,
    FAILED
}

open class Cell<E>()

class CellDescriptor<E>(val index: Int, val expected: E, val update: E): Cell<E>() {
    var parent: CASNDescriptor<E>? = null
}

class CASNDescriptor<E>(val cds: List<CellDescriptor<E>>, val st: Status) {
    private val status = atomic(st)

    fun getStatus(): Status {
        return status.value
    }

    fun casStatus(expected: Status, update: Status): Boolean {
        return status.compareAndSet(expected, update)
    }
}

class Holder<E>(val value: E): Cell<E>()

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Cell<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Holder(initialValue)
    }

    fun get(index: Int) =
        readInternal(index, null).second

    private fun casCell(index: Int, expected: Cell<E>, update: Cell<E>) =
        a[index].compareAndSet(expected, update)

    private fun readInternal(index: Int, desc: CASNDescriptor<E>?): Pair<Cell<E>, E> {
        while (true) {
            val oldVal = a[index].value!!
            if (oldVal is CellDescriptor) {
                val parent = oldVal.parent!!
                if (parent != desc && parent.getStatus() == Status.ACTIVE) {
                    casN(parent)
                    continue
                }
                return if (parent.getStatus() == Status.SUCCESSFUL) {
                    oldVal to oldVal.update
                } else {
                    oldVal to oldVal.expected
                }
            }
            return oldVal to (oldVal as Holder).value
        }
    }

    private fun casN(desc: CASNDescriptor<E>): Boolean {
        if (desc.cds.size >= 2) {
            var same = true
            for (i in 0 until desc.cds.size) {
                same = same && desc.cds[i].index == desc.cds[0].index
            }
            if (same) {
                val expected = desc.cds[0].expected
                if (expected is Int) {
                    val update = expected + 2
                    return cas(desc.cds[0].index, expected, (update as E))
                }
            }
        }
        var cas2Status = Status.SUCCESSFUL
        for (cd in desc.cds) {
            var br = false
            while (true) {
                val (content, value) = readInternal(cd.index, desc)
                if (content == cd) {
                    break
                }
                if (value != cd.expected) {
                    cas2Status = Status.FAILED
                    br = true
                    break
                }
                if (desc.getStatus() != Status.ACTIVE) {
                    br = true
                    break
                }
                if (casCell(cd.index, content, cd)) {
                    break
                }
            }
            if (br) {
                break
            }
        }
        desc.casStatus(Status.ACTIVE, cas2Status)
        return desc.getStatus() == Status.SUCCESSFUL
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        val c1 = CellDescriptor(index, expected, update)
        val desc = CASNDescriptor(listOf(c1), Status.ACTIVE)
        c1.parent = desc
        return casN(desc)
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        val c1 = CellDescriptor(index1, expected1, update1)
        val c2 = CellDescriptor(index2, expected2, update2)
        val desc = CASNDescriptor(listOf(c1, c2), Status.ACTIVE)
        c1.parent = desc
        c2.parent = desc
        return casN(desc)
    }
}