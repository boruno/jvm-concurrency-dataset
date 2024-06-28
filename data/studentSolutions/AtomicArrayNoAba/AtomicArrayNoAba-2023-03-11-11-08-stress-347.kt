import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val arr = Array<Ref<E>>(size) {Ref(initialValue)}

//    init {
//        for (i in 0 until size) arr[i].value = Ref(initialValue)
//    }

    fun get(index: Int) =
        arr[index].value

    fun cas(index: Int, expected: E, update: E) =
        arr[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        var a = arr[index1]
        var expectedA = expected1
        var updateA = update1
        var b = arr[index2]
        var expectedB = expected2
        var updateB = update2
        if (index1 > index2) {
            a = arr[index2]
            expectedA = expected2
            updateA = update2
            b = arr[index1]
            expectedB = expected1
            updateB = update1
        }
        val descriptor = cas2Desc(a, expectedA, updateA, b, expectedB, updateB)
        if (a.compareAndSet(expectedA, descriptor)) {
            return descriptor.complete()
        } else {
            return false
        }
    }
}


class cas2Desc<T>(val a: Ref<T>, val expectedA: T, val updateA: T, val b: Ref<T>, val expectedB: T, val updateB: T) {
    val outcome = atomic<Int>(UNDECIDED)

    fun complete(): Boolean {
        if (b.compareAndSet(expectedB, this)) {
            outcome.compareAndSet(UNDECIDED, SUCCESS)
        } else {
            outcome.compareAndSet(UNDECIDED, FAIL)
        }

        val outcomeValue = outcome.value
        val aVal: T
        if (outcomeValue == SUCCESS) {
            aVal = updateA
        } else {
            aVal = expectedA
        }
        a.compareAndSet(this, aVal)
        val bVal: T
        if (outcomeValue == SUCCESS) {
            bVal = updateB
        } else {
            bVal = expectedB
        }
        b.compareAndSet(this, bVal)
        return outcomeValue == SUCCESS
    }
}


abstract class Descriptor {
    abstract fun complete()
}


class Ref<T>(init: T) {
    val v = atomic<Any?>(init)

    var value: T
        get() {
            v.loop { cur ->
                when (cur) {
                    is cas2Desc<*> -> cur.complete()
                    else -> return cur as T
                }
            }
        }
        set(upd) {
            v.loop { cur ->
                when (cur) {
                    is cas2Desc<*> -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd)) return
                }
            }
        }

    fun compareAndSet(expected: Any?, update: Any?): Boolean {
        v.loop {
            cur -> when(cur) {
                is cas2Desc<*> -> if (cur === update) return true else cur.complete()
                else -> if (expected != cur) return false else if (v.compareAndSet(cur, update)) return true
            }
        }
    }
}


val SUCCESS = 1
val UNDECIDED = 0
val FAIL = 2