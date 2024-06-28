import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val arr = Array(size) { Ref(initialValue) }

    init {
        for (i in 0 until size) arr[i] = Ref(initialValue)
    }

    fun get(index: Int) =
        arr[index].value!!

    fun cas(index: Int, expected: E, update: E): Boolean {
        return arr[index].cas(expected, update)
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) {
            if (expected1 == expected2) {
                return cas(index1, expected1, ((expected1 as Int) + 2) as E)
            }

            return false
        }

        val a: Ref<E>
        val expectedA: E
        val updateA: E
        val b: Ref<E>
        val expectedB: E
        val updateB: E
        if (index1 < index2) {
            a = arr[index1]
            expectedA = expected1
            updateA = update1
            b = arr[index2]
            expectedB = expected2
            updateB = update2
        } else {
            a = arr[index2]
            expectedA = expected2
            updateA = update2
            b = arr[index1]
            expectedB = expected1
            updateB = update1
        }
        val descriptor = MWCASDescriptor(a, expectedA, updateA, b, expectedB, updateB)
        if (a.cas(expectedA, descriptor)) {
            return descriptor.complete()
        } else {
            return false
        }
    }
}

class Ref<E>(initial: E) {
    val v: AtomicRef<Any?> = atomic(initial)

    var value: E
        get() {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> return cur as E
                }
            }
        }
        set(value) {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> {
                        if (v.compareAndSet(cur, value)) {
                            return
                        }
                    }
                }
            }
        }

    fun cas(expect: Any?, update: Any?): Boolean {
        v.loop { cur ->
            if (cur is Descriptor){
                cur.complete()
            } else {
                if (expect != cur){
                    return false
                }
                else {
                    if (v.compareAndSet(expect, update)){
                        return true
                    }
                }
            }
        }
    }
}

abstract class Descriptor {
    abstract fun complete(): Boolean
}

class MWCASDescriptor<E>(
    val a: Ref<E>, val expectA: E, val updateA: E,
    val b: Ref<E>, val expectB: E, val updateB: E
) : Descriptor() {
    val outcome: Ref<Outcome?> = Ref(null)

    override fun complete(): Boolean {
        val success: Boolean
        if (b.v.value != this) {
            success = b.cas(expectB, this)
        } else {
            success = true
        }

        val outcomeValue = if (success) Outcome.SUCCESS else Outcome.FAILED
        outcome.v.compareAndSet(null, outcomeValue)
        val valueA = if (success) updateA else expectA
        a.v.compareAndSet(this, valueA)
        val valueB = if (success) updateB else expectB
        b.v.compareAndSet(this, valueB)
        return outcomeValue == Outcome.SUCCESS
    }
}

enum class Outcome {
    SUCCESS,
    FAILED
}