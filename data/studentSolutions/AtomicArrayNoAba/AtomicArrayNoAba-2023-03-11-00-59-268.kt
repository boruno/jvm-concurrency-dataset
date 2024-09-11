import kotlinx.atomicfu.*

const val UNDECIDED = "UNDECIDED"
const val SUCCESS = "SUCCESS"
const val FAIL = "FAIL"

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array<Ref<E>>(size) { Ref(initialValue) }

    init {
        for (i in 0 until size) a[i] = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: Any?, update: Any?) =
        a[index].cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (index1 < index2) {
            val descriptor = Descriptor(a[index1], expected1, update1, a[index2], expected2, update2)
            if (cas(index1, expected1, descriptor)) {
                descriptor.complete()
                return descriptor.outcome.value == SUCCESS
            }
        } else {
            val descriptor = Descriptor(a[index2], expected2, update2, a[index1], expected1, update1)
            if (cas(index2, expected2, descriptor)) {
                descriptor.complete()
                return descriptor.outcome.value == SUCCESS
            }
        }
        return false
    }
}

class Descriptor<E>(
    private val element1: Ref<E>, private val expected1: E, private val update1: E,
    private val element2: Ref<E>, private val expected2: E, private val update2: E,
    val outcome: Ref<String> = Ref(UNDECIDED)) {
    fun complete() {
        if (if (element2.ref.value != this) element2.cas(expected2, this) else true) {
            outcome.cas(UNDECIDED, SUCCESS)
            element1.cas(this, update1)
            element2.cas(this, update2)
        } else {
            outcome.cas(UNDECIDED, FAIL)
            element1.cas(this, expected1)
            element2.cas(this, expected2)
        }
    }
}

class Ref<E>(initialValue: E) {
    val ref: AtomicRef<Any?> = atomic(initialValue)
    var value: E
        get() {
            while (true) {
                val cur = ref.value
                if (cur !is Descriptor<*>)
                    return cur as E
                cur.complete()
            }
        }
        set(new) {
            while (true) {
                val cur = ref.value
                if (cur !is Descriptor<*>) {
                    if (ref.compareAndSet(cur, new))
                        return
                    continue
                }
                cur.complete()
            }
        }

    fun cas(expect: Any?, update: Any?): Boolean {
        while (true) {
            if (ref.compareAndSet(expect, update))
                return true
            val cur = ref.value
            if (cur !is Descriptor<*>) {
                if (cur != expect)
                    return false
                continue
            }
            cur.complete()
        }
    }
}