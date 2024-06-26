import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = Array<Ref<Any>>(size) { Ref() }

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E =
        a[index].value!! as E

    fun cas(index: Int, expected: E, update: E): Boolean {
        //println("CAS $index $expected $update")
        while (true) {
            val previous = compareSetAndReturn(a[index], expected as Any, update as Any)

            if (previous == expected) {
               // println("Finished CAS $index $expected $update")
                return true
            }

            if (previous is Descriptor) {
                previous.complete()
                continue
            }
           // println("Finished CAS $index $expected $update")
            return false
        }



    }


    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {

        if (index1 == index2) {
            //println("Finished CAS2 $index1 $expected1 $update1, $index2 $expected2 $update2")
            return false
        }
        if (index1 > index2) {
            return cas2(
                index2, expected2, update2,
                index1, expected1, update1
            )
        }

        println("CAS2 $index1 $expected1 $update1, $index2 $expected2 $update2")
        val res = casN(
            a[index1], expected1 as Any, update1 as Any,
            a[index2], expected2 as Any, update2 as Any
        )

        println("Finished CAS2 $index1 $expected1 $update1, $index2 $expected2 $update2")
        return res
    }


}

fun dcss(
    a: Ref<Any>, expectA: Any, updateA: Any,
    b: Ref<Any>, expectB: Any
): Boolean {
    while (true) {
        val descriptor = DcssDescriptor(a, expectA, updateA, b, expectB)
        val previous = compareSetAndReturn(a, expectA, descriptor)

        if (previous is Descriptor) {
            previous.complete()
            continue
        }
        if (previous == expectA) {
            descriptor.complete()
            return descriptor.result.value == Outcome.SUCCESS
        }

        return false
    }
}

fun casN(
    a: Ref<Any>, expectA: Any, updateA: Any,
    b: Ref<Any>, expectB: Any, updateB: Any
): Boolean {
    val descriptor = Cas2Descriptor(
        a, expectA, updateA,
        b, expectB, updateB
    )

    while (true) {

        val previous = compareSetAndReturn(a, expectA, descriptor)

        if (previous is Descriptor) {
            previous.complete()
            continue
        }

        if (previous == expectA) {
            descriptor.complete()
            return descriptor.result.v.value == Outcome.SUCCESS
        }

        return false
    }

}

interface Descriptor {
    fun complete()
}

class DcssDescriptor(
    val a: Ref<Any>, val expectA: Any, val updateA: Any,
    val b: Ref<Any>, val expectB: Any,
) : Descriptor {
    val result: AtomicRef<Outcome> = atomic(Outcome.UNDECIDED)
    override fun complete() {
        if (expectB == b.value)
            result.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
        else
            result.compareAndSet(Outcome.UNDECIDED, Outcome.FAILED)

        val update = if (result.value == Outcome.SUCCESS) updateA else expectA

        a.v.compareAndSet(this, update)
    }
}

class Cas2Descriptor(
    val a: Ref<Any>, val expectA: Any, val updateA: Any,
    val b: Ref<Any>, val expectB: Any, val updateB: Any,
) : Descriptor {
    val result: Ref<Any> = Ref()

    init {
        result.value = Outcome.UNDECIDED
    }

    override fun complete() {
        if (result.v.value == Outcome.UNDECIDED) {
            if (b.v.value == this || dcss(b, expectB, this, result, Outcome.UNDECIDED)) {
                result.v.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
            } else {
                result.v.compareAndSet(Outcome.UNDECIDED, Outcome.FAILED)
            }
        }

        if (result.v.value == Outcome.SUCCESS) {
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
        } else {
            a.v.compareAndSet(this, expectA)
        }
    }
}

enum class Outcome {
    SUCCESS, FAILED, UNDECIDED
}

class Ref<E> {
    val v = atomic<Any?>(null)
    var value: E?
        get() {
            while (true) {
                when (val it = v.value) {
                    is Descriptor -> {
                        it.complete()
                        continue
                    }

                    else -> return it as E?
                }
            }
        }
        set(value) {
            v.value = value
        }
}

fun <E> compareSetAndReturn(ref: Ref<E>, expected: E, update: E): Any? {
    while (true) {
        val value = ref.v.value
        if (value != expected) {
            return value
        }

        if (ref.v.compareAndSet(expected, update)) {
            return expected
        }
    }
}