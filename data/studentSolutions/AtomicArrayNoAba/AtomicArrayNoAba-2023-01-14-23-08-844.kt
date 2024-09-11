import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    // null - not in progress
    // false - progress
    // true - end
    private val lockArray = atomicArrayOfNulls<Boolean>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }


    private class WordDescriptor<E>(val address: Int, val old: E?, val new: E?, var parent: MCASDescriptor<E>? = null)

    private enum class StatusType {
        ACTIVE, SUCCESSFUL, FAILED
    }

    private class MCASDescriptor<E>(
        val word1: WordDescriptor<E>,
        val word2: WordDescriptor<E>
    ) {
        val status: AtomicRef<StatusType> = atomic(StatusType.ACTIVE)
        val words = mutableListOf(word1, word2)
    }

    private fun readInternal(address: Int, self: MCASDescriptor<E>?): Pair<E, E> {
        while (true) {
            val value = read(address)
            if (value !is WordDescriptor<*>) return Pair(value as E, value as E)
            else {
                val parent = (value as WordDescriptor<E>).parent ?: continue
                if (parent != self && parent.status.value == StatusType.ACTIVE) {
                    mcas(parent)
                } else {
                    return if (parent.status.value == StatusType.SUCCESSFUL) Pair(value as E, value.new as E) else Pair(
                        value as E,
                        value.old as E
                    )
                }
            }
        }
    }

    private fun mcas(desc: MCASDescriptor<E>): Boolean {
        var success = true
        for (wordDesc in desc.words) {
            val interRes = readInternal(wordDesc.address, desc)
            val content = interRes.first
            val value = interRes.second

            if (content == wordDesc) continue
            if (value != wordDesc.old) {
                success = false
                break
            }
            if (desc.status.value != StatusType.ACTIVE) break
            if (a[wordDesc.address].compareAndSet(content, wordDesc)) {
                // goto
            }
            if (desc.status.compareAndSet(
                    StatusType.ACTIVE,
                    if (success) StatusType.SUCCESSFUL else StatusType.FAILED
                )
            ) {

            }
        }

        return (desc.status.value == StatusType.SUCCESSFUL)
    }

    fun get(index: Int): E =
        read(index)

    private fun read(index: Int): E {
        val pair = readInternal(index, null)
        return pair.second
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        val desc = MCASDescriptor(
            WordDescriptor(index1, expected1, update1),
            WordDescriptor(index2, expected2, update2)
        ).apply {
            word1.parent = this
            word2.parent = this
        }
        return mcas(desc)
    }

//    fun cas2(
//        index1: Int, expected1: E, update1: E,
//        index2: Int, expected2: E, update2: E
//    ): Boolean {
//        // TODO this implementation is not linearizable,
//        // TODO a multi-word CAS algorithm should be used here.
//        var currentSuccess = true
//
//        var finished1 = false
//        var finished2 = false
//        lockArray[index1].value = false
//        lockArray[index2].value = false
//        while (true) {
//            if (finished1 && finished2) break
//            val currFirst = get(index1)
//            val currSecond = get(index2)
//            if (currFirst != update1) {
//                if (currFirst != expected1) {
//                    currentSuccess = false
//                    break
//                }
//                if (lockArray[index1].value != false) break
//
//                if (cas(index1, currFirst, update1)) finished1 = true
//            }
//            if (currSecond != update2) {
//                if (currSecond != expected2) {
//                    currentSuccess = false
//                    break
//                }
//                if (lockArray[index2].value != false) break
//
//                if (cas(index2, currSecond, update2)) finished2 = true
//            }
//        }
//
//        return currentSuccess
//
//    }

}