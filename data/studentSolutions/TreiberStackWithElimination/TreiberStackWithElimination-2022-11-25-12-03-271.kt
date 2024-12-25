//package mpp.stackWithElimination


import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = Array<AtomicReference<WithId>>(ELIMINATION_ARRAY_SIZE) {
        AtomicReference(EmptyWithId(0))
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            if (tryPushToStack(x)) {
                return
            }

            val (indexOfElemInEliminationArray, valueWithId) = definitelyPutElemIntoArray(x)
            repeat(T) {
                if (eliminationArray[indexOfElemInEliminationArray].get() != valueWithId) {
                    return
                }
            }
            val emptyWithId = EmptyWithId(valueWithId.id + 1)
            if (!eliminationArray[indexOfElemInEliminationArray].compareAndSet(valueWithId, emptyWithId)) {
                return
            }
        }
    }

    private fun tryPushToStack(x: E): Boolean {
        val currentTopValue = top.value
        val newTopValue = Node(x, currentTopValue)
        return top.compareAndSet(currentTopValue, newTopValue)
    }

    private fun definitelyPutElemIntoArray(x: E): Pair<Int, ValueWithId<E>> {
        while (true) {
            val indexToPushIn = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            val cellRef = eliminationArray[indexToPushIn]
            val itemInsideCell = cellRef.get()

            if (itemInsideCell !is EmptyWithId) {
                continue
            }
            val newValue = ValueWithId(x, itemInsideCell.id + 1)
            if (cellRef.compareAndSet(itemInsideCell, newValue)) {
                return Pair(indexToPushIn, newValue)
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val maybe = tryPop()
            if (maybe is Just<*>) {
                return (maybe as Just<E>).value
            }
            var indexToFetch: Int = 0
            repeat(T) {
                indexToFetch = Random.nextInt(ELIMINATION_ARRAY_SIZE)
                val cell = eliminationArray[indexToFetch]
                val item = cell.get()
                if (item is ValueWithId<*>) {
                    val newEmptyValue = EmptyWithId(item.id + 1)
                    if (cell.compareAndSet(item, newEmptyValue)) {
                        return (item as ValueWithId<E>).value
                    }
                }
            }
        }
    }

    fun tryPop(): Maybe {
        val currentTopValue = top.value ?: return Just<E>(null)
        val newTopValue = currentTopValue.next
        if (top.compareAndSet(currentTopValue, newTopValue)) {
            return Just(currentTopValue.x)
        }
        return None
    }

    sealed class WithId(val id: Int)
    class ValueWithId<E>(val value: E, id: Int) : WithId(id)
    class EmptyWithId(id: Int) : WithId(id)

    sealed class Maybe {}
    class Just<E>(val value: E?) : Maybe()
    object None : Maybe()

    companion object {
        private const val T = 239
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT