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
        while (true) {
            val currentTop = top.value;
            val newTop = Node<E>(x, currentTop)
            if (top.compareAndSet(currentTop, newTop)) {
                return
            }
        }
    }

    private fun vanillaPop() : E? {
        while (true) {
            val currentTop = top.value;
            if (currentTop == null) {
                return null
            }
            val newTop = currentTop.next
            if (top.compareAndSet(currentTop, newTop)) {
                return currentTop.x
            }
        }
    }
    
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val currentTop = top.value;
        val newTop = Node<E>(x, currentTop)
        val record = EliminationRecord(newTop)

        var i : Int = rndElimination()
        var timeout : Int = 0
        while (!elimination[i].compareAndSet(null, record)) {
            if (++timeout >= TIMEOUT) {
                return vanillaPush(x)
            }
            //i = rndElimination()
        }
        timeout = 0

        while (elimination[i].value!!.type.value != EliminationRecordType.Done) {
            if (++timeout >= TIMEOUT) {
                if (elimination[i].value!!.type.compareAndSet(EliminationRecordType.Push, EliminationRecordType.Terminated)) {
                    break
                } else if (elimination[i].value!!.type.compareAndSet(EliminationRecordType.PreDone, EliminationRecordType.Terminated)) {
                    break
                }
            }
        }

        val finalState = elimination[i].value!!.type.value
        assert(finalState == EliminationRecordType.Done || finalState == EliminationRecordType.Terminated)

        while (!elimination[i].compareAndSet(record, null)) {}

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

        var timeout : Int = 0
        var i = rndElimination()
        while (elimination[i].value == null || !elimination[i].value!!.type.compareAndSet(EliminationRecordType.Push, EliminationRecordType.PreDone)) {
            if (++timeout >= TIMEOUT) {
                return vanillaPop()
            }
            //i = rndElimination()
        }
        var currVal = elimination[i].value?.node?.x
        if (elimination[i].value == null || !elimination[i].value!!.type.compareAndSet(EliminationRecordType.PreDone, EliminationRecordType.Done)) {
            return vanillaPop()
        }
        return currVal

    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private enum class EliminationRecordType {
    Push,
    Done,
    PreDone,
    Terminated
}

private class EliminationRecord<E>(val node: Node<E>, val type: AtomicRef<EliminationRecordType> = atomic<EliminationRecordType>(EliminationRecordType.Push))

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val TIMEOUT = 1000