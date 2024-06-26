package mpp.stack

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.EmptyStackException
import kotlin.random.Random

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    private val elimination = atomicArrayOfNulls<EliminationRecord<E>>(ELIMINATION_ARRAY_SIZE)

    private fun rndElimination() : Int {
        return Random.nextInt(ELIMINATION_ARRAY_SIZE)
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
                throw EmptyStackException()
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
        while (!elimination[i].compareAndSet(null, record) && timeout < TIMEOUT) {
            timeout++
            i = rndElimination()
        }
        if (timeout >= TIMEOUT) {
           return vanillaPush(x)
        }
        timeout = 0

        while (!(record.type.value != EliminationRecordType.Done) && timeout < TIMEOUT) {
            timeout++
        }
        if (timeout >= TIMEOUT) {
            while (!elimination[i].compareAndSet(record, null)) {}
            return vanillaPush(x)
        }

        while (!elimination[i].compareAndSet(record, null)) {}

    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {

        var timeout : Int = 0
        var i = rndElimination()
        var currElim = elimination[i].value
        var currVal = currElim?.node?.x
        while ((currElim == null || !currElim.type.compareAndSet(EliminationRecordType.Push, EliminationRecordType.Done)) && timeout < TIMEOUT) {
            timeout++
            i = rndElimination()
            currElim = elimination[i].value
            currVal = currElim?.node?.x
        }
        if (timeout >= TIMEOUT) {
            return vanillaPop()
        }

        return currVal

    }

}

private class Node<E>(val x: E, val next: Node<E>?)

private enum class EliminationRecordType {
    Push,
    Done
}

private class EliminationRecord<E>(val node: Node<E>, val type: AtomicRef<EliminationRecordType> = atomic<EliminationRecordType>(EliminationRecordType.Push))

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val TIMEOUT = 1000000