import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference

sealed interface Box<T>
class WordDescriptor<T>(val address: Int, val old: T, val new: T, val parent: MCASDescriptor<T>) : Box<T>

class Value<T>(val element: T) : Box<T>

enum class StatusType { ACTIVE, SUCCESSFUL, FAILED };
class MCASDescriptor<T>(val status: AtomicReference<StatusType>, var words: List<WordDescriptor<T>>)

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Box<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Value(initialValue)
    }

    fun get(index: Int): E = readInternal(index, null).second

    fun cas(index: Int, expected: E, update: E) : Boolean {
        val descriptor = MCASDescriptor<E>(
            AtomicReference(StatusType.ACTIVE),
            listOf()
        )

        descriptor.words = listOf(
            WordDescriptor(index, expected, update, descriptor)
        )

        return mCAS(descriptor)
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
//        if (a[index1].value != expected1 || a[index2].value != expected2) return false
//        a[index1].value = update1
//        a[index2].value = update2

        if (index1 == index2 && expected1 is Int && expected2 is Int) {
            val a: Int = expected1 + 2
            return cas(index1, expected1, a as E)

        }

        val descriptor = MCASDescriptor<E>(
            AtomicReference(StatusType.ACTIVE),
            listOf()
        )

        descriptor.words = listOf(
            WordDescriptor(index1, expected1, update1, descriptor),
            WordDescriptor(index2, expected2, update2, descriptor)
        )

        return mCAS(descriptor)
    }


    private fun readInternal(address: Int, self: MCASDescriptor<E>?): Pair<Box<E>?, E> {
        while (true) {
            val v = a[address].value
            return if (v !is WordDescriptor<E>) v to (v as Value).element
            else { // found a descriptor
                val parent = v.parent
                if (parent != self && parent.status.get() == StatusType.ACTIVE) {
                    mCAS(parent)
                    continue
                } else {
                    if (parent.status.get() == StatusType.SUCCESSFUL) v to v.new
                    else v to v.old
                }
            }
        }
    }

    private fun mCAS(descriptor: MCASDescriptor<E>): Boolean {
        var success = true
        label@ for (wordDesc in descriptor.words) {
            while (true) {
                val (content, value) = readInternal(wordDesc.address, descriptor)
                when {
                    content === wordDesc -> continue@label

                    value != wordDesc.old -> {
                        success = false
                        break@label
                    }

                    descriptor.status.get() != StatusType.ACTIVE -> break@label
                    a[wordDesc.address].compareAndSet(content, wordDesc) -> continue
                }

            }
        }
        descriptor.status.compareAndSet(
            StatusType.ACTIVE,
            if (success) StatusType.SUCCESSFUL else StatusType.FAILED
        )

        return descriptor.status.get() == StatusType.SUCCESSFUL
    }

}