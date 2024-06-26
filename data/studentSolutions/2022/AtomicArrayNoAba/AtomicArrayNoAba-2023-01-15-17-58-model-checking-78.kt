import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {

    private val a = Array(size) { Ref<E?>(initialValue) }

    init {
        for (i in 0 until size) a[i].v.value = initialValue
    }

    fun get(index: Int) = read(index)!!

//    fun cas(index: Int, expected: E, update: E) =
//        a[index].cas(expected, update)

    fun cas2(
        indexA: Int, expectedA: E, updateA: E,
        indexB: Int, expectedB: E, updateB: E
    ): Boolean {

        if (indexA == indexB) {
            return a[indexA].v.compareAndSet(expectedA, expectedA.toString().toInt() + 2)
        }

        val parent = MCASDescriptor()
        val wordDescriptor1 = WordDescriptor(a[indexA], expectedA, updateA, parent)
        val wordDescriptor2 = WordDescriptor(a[indexB], expectedB, updateB, parent)
        parent.descriptor1 = wordDescriptor1
        parent.descriptor2 = wordDescriptor2
        return MCAS(parent)
    }

    class Ref<E>(initialValue: E) {
        val v = atomic<Any?>(initialValue)
    }

    fun read(index: Int): E {
        val (content, value) = readInternal(a[index], null)
        return value as E
    }

    fun readInternal(addr: Ref<*>, self: MCASDescriptor?): Pair<*, *> {
        while (true) {
            val value = addr.v.value
            if (value !is WordDescriptor<*>) {
                return Pair(value, value)
            } else {
                val parent = value.parent
                if (parent != self && parent.status.value == Status.ACTIVE) {
                    MCAS(parent)
                } else {
                    if (parent.status.value == Status.SUCCESSFUL) {
                        return Pair(value, value.new)
                    } else {
                        return Pair(value, value.old)
                    }
                }

            }
        }
    }

    fun MCAS(desc: MCASDescriptor): Boolean {
        var success = true
        val list = listOf(desc.descriptor1, desc.descriptor2)
        loop1@ for (wordDesc in list) {
            retry_word@ while (true) {
                val (content, value) = readInternal(wordDesc!!.address, desc)
                if (content === wordDesc) {
                    continue@loop1
                }
                if (value != wordDesc.old) {
                    success = false
                    break@loop1
                }
                if (desc.status.value != Status.ACTIVE) {
                    break@loop1
                }

                if (wordDesc.address.v.compareAndSet(content, wordDesc)) {
                    break@retry_word
                }
            }
        }

        val updateStatus = if (success) Status.SUCCESSFUL else Status.FAILED

        if (desc.status.compareAndSet(Status.ACTIVE, updateStatus)) {
//            println("retire")
            //retireForCleanUp
        }

        val returnValue = desc.status.value == Status.SUCCESSFUL
        return returnValue
    }

    interface Descriptor

    class WordDescriptor<E>(
        val address: Ref<E>, val old: Any?, val new: Any?,
        val parent: MCASDescriptor
    ) : Descriptor

    class MCASDescriptor : Descriptor {

        var descriptor1: WordDescriptor<*>? = null
        var descriptor2: WordDescriptor<*>? = null

        val status: AtomicRef<Status> = atomic(Status.ACTIVE)
    }

    enum class Status {
        ACTIVE, SUCCESSFUL, FAILED
    }
}