import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {

    private val a = Array(size) { Ref<E?>(initialValue) }

    init {
        for (i in 0 until size) a[i].v.value = initialValue
    }

    fun get(index: Int) = read(index)!!

    fun cas(indexA: Int, expectedA: E, updateA: E): Boolean {
        val parent = MCASDescriptor()
        val wordDescriptor1 = WordDescriptor(a[indexA], expectedA, updateA, parent)
        parent.list.add(wordDescriptor1)
        return MCAS(parent)
    }

    fun cas2(
        indexA: Int, expectedA: E, updateA: E,
        indexB: Int, expectedB: E, updateB: E
    ): Boolean {

        val parent = MCASDescriptor()

        if (indexA == indexB) {
            if (expectedA == expectedB) {
                val wordDescriptor1 = WordDescriptor(a[indexA], expectedA, updateA, parent)
                parent.list.add(wordDescriptor1)
                return MCAS(parent)
            } else {
                return false
            }
        }

        val wordDescriptor1 = WordDescriptor(a[indexA], expectedA, updateA, parent)
        val wordDescriptor2 = WordDescriptor(a[indexB], expectedB, updateB, parent)

        if (indexA > indexB) {
            parent.list.add(wordDescriptor2)
            parent.list.add(wordDescriptor1)
        } else {
            parent.list.add(wordDescriptor1)
            parent.list.add(wordDescriptor2)
        }

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
        loop1@ for (wordDesc in desc.list) {
            retry_word@ while (true) {
                val (content, value) = readInternal(wordDesc.address, desc)
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

        val list = mutableListOf<WordDescriptor<*>>()

        val status: AtomicRef<Status> = atomic(Status.ACTIVE)
    }

    enum class Status {
        ACTIVE, SUCCESSFUL, FAILED
    }
}