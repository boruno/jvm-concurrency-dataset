import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun set(index: Int, value: E) {
        a[index].value!!.value = value
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.compareAndSet(expected, update) // think

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {

        // for aba
        var ind1 = index1
        var exp1 = expected1
        var upd1 = update1
        var ind2 = index2
        var exp2 = expected2
        var upd2 = update2

        if (ind1 == ind2) {
            return exp1 == exp2 && upd1 == upd2 && cas(ind1, upd1, upd2)
        }

        if (ind2 > ind1) {
            ind1 = index2
            exp1 = expected2
            upd1 = update2
            ind2 = index1
            exp2 = expected1
            upd2 = update1
        }

        val val1 = a[ind1].value!!
        val val2 = a[ind2].value!!

        // переход между 1 -> 2 / 7

        val casnDescriptor = CASNDescriptor(val1 as Ref<Any>, exp1 as Any, upd1 as Any, val2 as Ref<Any>, exp2 as Any, upd2 as Any)

        if (!val1.compareAndSet(exp1, casnDescriptor)) {
            return false
        }
        // переход между 2 -> 4 / 8
        casnDescriptor.complete()
        return casnDescriptor.outcome.value == Outcome.SUCCESS
    }
}

class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial) //внутри либо значение либо дескриптор

    var value: T
        get()  {
            v.loop { cur ->
                when(cur) {
                    is Descriptor -> cur.complete()
                    else -> return cur as T
                }
            }
        }

        set(upd) {
            v.loop { cur ->
                when(cur) {
                    is Descriptor -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd))
                        return
                }
            }
        }

    fun compareAndSet(expect: T, update: T): Boolean {
        v.loop { cur ->
            when(cur) {
                is Descriptor -> cur.complete()
                else -> return v.compareAndSet(expect, update)
            }
        }
    }

}

/*
    class<E>
    Desc<E>:class<E>
    Value<E>:class<E>
    array<Ref<class<E>>
 */


//fun <A,B> dcss(
//    a: Ref<A>, expectA: A, updateA: A,
//    b: Ref<B>, expectB: B) =
//    atomic {
//        if (a.value == expectA && b.value == expectB) {
//            a.value = updateA
//        }
//    }

/*
    Сравниваем А И Б обновляем А

    init descriptor of DCSS(a, expA, updA, b, expB)
        Если А=expA -> A = DCSSDescriptor
            дескриптор виден другим потокам => все потоки помогают тк LockFree
            переходы между 23 25 - терминальны(только эти)
            Поток начавший операцию не поймет как она была завершена -- status/bool?
            Если B=expB - > A = updA
            Иначе A = expA
        Иначе ретрай
 */
// разные регионы
abstract class Descriptor {
    abstract fun complete()
}

enum class Outcome {UNDEFINED, SUCCESS, FAIL }

class RDCSSDescriptorMod(
    val a: Ref<Any>, val expectA: Any, val updateA: Any,
    val b: Ref<Any>, val expectB: Any
) : Descriptor() {
    val outcome = atomic(Outcome.UNDEFINED)
    override fun complete() {
        when (outcome.value) {
            Outcome.UNDEFINED -> {
                val update = if (b.v.value === expectB)
                    updateA else expectA

                var newStatus = Outcome.SUCCESS
                if (update != updateA) {
                    newStatus = Outcome.FAIL
                }
                // переход между 2 -> 3 / 2 -> 6
                if (outcome.compareAndSet(Outcome.UNDEFINED, newStatus)) {
                    // переход между 6 -> 7 или 3 -> 4
                    a.v.compareAndSet(this, update)
                }
            }
            Outcome.SUCCESS -> {
                // переход между 3 -> 4
                a.v.compareAndSet(this, updateA)
            }
            else -> {
                // переход между 6 -> 7
                a.v.compareAndSet(this, expectA)
            }
        }
    }
}

class CASNDescriptor(
    val a: Ref<Any>, val expectA: Any, val updateA: Any,
    val b: Ref<Any>, val expectB: Any, val updateB: Any
): Descriptor() {
    var outcome = Ref(Outcome.UNDEFINED)
    override fun complete() {

        // переход между 4 -> 6 ?
        if (outcome.value == Outcome.SUCCESS) {
            // 4 -> 5
            a.v.compareAndSet(this, updateA)
            // 5 -> 6
            b.v.compareAndSet(this, updateB)
        }
        // переход между 2 -> 9 ?
        if (outcome.value == Outcome.FAIL) {
            // 8 -> 9
            a.v.compareAndSet(this, expectA)
        }
        val bValue = b.v.value

        // переход между 3 -> 4 ?
        if (bValue === this) {
            outcome.compareAndSet(Outcome.UNDEFINED, Outcome.SUCCESS)
            // 4 -> 5
            a.v.compareAndSet(this, updateA)
            // 5 -> 6
            b.v.compareAndSet(this, updateB)
        }

        // переход между 2 -> 3
        val rdcssDescriptorMod = RDCSSDescriptorMod(b, expectB, updateB, outcome as Ref<Any>, Outcome.UNDEFINED)
        if (b.compareAndSet(expectB, rdcssDescriptorMod)) {
            rdcssDescriptorMod.complete()
            if (rdcssDescriptorMod.outcome.value == Outcome.SUCCESS) {
                // 3 -> 4
                outcome.compareAndSet(Outcome.UNDEFINED, Outcome.SUCCESS)
                // 4 -> 5
                a.v.compareAndSet(this, updateA)
                // 5 -> 6
                b.v.compareAndSet(this, updateB)
            } else {
                // 2 -> 8
                outcome.compareAndSet(Outcome.UNDEFINED, Outcome.FAIL)
                // 8 -> 9
                a.v.compareAndSet(this, expectA)
            }
        } else {
            // 2 -> 8
            outcome.compareAndSet(Outcome.UNDEFINED, Outcome.FAIL)
            // 8 -> 9
            a.v.compareAndSet(this, expectA)
        }
    }


}



//fun <A,B> cas2(
//    a: Ref<A>, expectA: A, updateA: A,
//    b: Ref<B>, expectB: B, updateB: B): Boolean =
//    atomic {
//        if (a.value == expectA && b.value == expectB) {
//            a.value = updateA
//            b.value = updateB
//            true
//        } else
//            false
//    }

