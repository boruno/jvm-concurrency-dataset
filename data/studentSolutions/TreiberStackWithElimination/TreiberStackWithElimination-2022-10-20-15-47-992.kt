//package mpp.stackWithElimination

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val elimination = atomicArrayOfNulls<EliminationRecord<E>>(ELIMINATION_ARRAY_SIZE)


    private fun rndElimination() : Int {
        return ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
    }

    private fun vanillaPush(x: E) {
        while (!tryVanillaPush(x)) {}
    }

    private fun vanillaPop() : E? {
        while (true) {
            val v = tryVanillaPop()
            if (v.first) {
                return v.second
            }
        }
    }

    private fun tryVanillaPush(x: E) : Boolean {
        val currentTop = top.value;
        val newTop = Node<E>(x, currentTop)
        return top.compareAndSet(currentTop, newTop)
    }

    private fun tryVanillaPop() : Pair<Boolean, E?> {
        val currentTop = top.value;
        if (currentTop == null) {
            return Pair(true, null)
        }
        val newTop = currentTop.next
        if (top.compareAndSet(currentTop, newTop)) {
            return Pair(true, currentTop.x)
        } else {
            return Pair(false, null)
        }
    }

    private fun pushRecordForValue(x: E) : EliminationRecord<E> {
        return EliminationRecord(x)
    }


    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {

        if (tryVanillaPush(x)) {
            return
        }

        var i : Int = rndElimination()
        var timeout : Int = 0
        while (!elimination[i].compareAndSet(null, pushRecordForValue(x))) {
            if (++timeout >= TIMEOUT) {
                //while (elimination[i].value != null && !elimination[i].value!!.type.compareAndSet(EliminationRecordType.Push, EliminationRecordType.Terminated)) {}
                return vanillaPush(x)
            }
            //i = rndElimination()
        }
        timeout = 0

        var record = elimination[i].value!!

        while (record.type.value != EliminationRecordType.Done || record.type.value != EliminationRecordType.Terminated) {
            if (++timeout >= TIMEOUT) {
                break
            }
        }

        // record.type == Done || Terminated and is not going to change
        record.type.compareAndSet(EliminationRecordType.Push, EliminationRecordType.Terminated)

        val finalState = elimination[i].value!!.type.value
        assert(finalState == EliminationRecordType.Done || finalState == EliminationRecordType.Terminated)

        if (!elimination[i].compareAndSet(record, null)) {
            assert(false)
        }

        if (finalState == EliminationRecordType.Terminated) {
            return vanillaPush(x)
        }

    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {

        assert(false)

        val v = tryVanillaPop()
        if (v.first) {
            return v.second
        }

        var timeout : Int = 0
        var i = rndElimination()
        var currVal = elimination[i].value?.x
        while (elimination[i].value == null || !elimination[i].value!!.type.compareAndSet(EliminationRecordType.Push, EliminationRecordType.Done)) {
            if (++timeout >= TIMEOUT) {
                return vanillaPop()
            }
            currVal = elimination[i].value?.x
            //i = rndElimination()
        }

        // type is Done or -> null now

        return currVal

    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private enum class EliminationRecordType {
    Push,
    Done,
    Terminated
}

private class EliminationRecord<E>(val x: E) {

    val type: AtomicRef<EliminationRecordType> = atomic<EliminationRecordType>(EliminationRecordType.Push)

}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val TIMEOUT = 1000