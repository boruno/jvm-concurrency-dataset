package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<EliminationArrayElement?>(ELIMINATION_ARRAY_SIZE)
    private val random = Random(123)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val el = random.nextInt(ELIMINATION_ARRAY_SIZE)
        var eliminationState = 0
        val eliminationValue = EliminationValue(x)
        for (i in 0 until ELIMINATION_CYCLE_COUNT) {
            if (eliminationArray[el].compareAndSet(null, eliminationValue)) {
                eliminationState = 1
                break
            }
        }

        if (eliminationState == 1) {
            for (j in 0 until ELIMINATION_CYCLE_COUNT) {
                if (eliminationArray[j].value != x) {
                    eliminationState = 2
                    break
                }
            }
            if (eliminationState != 2 && eliminationArray[el].compareAndSet(eliminationValue, null)) {
                eliminationState = 0
            }
        }

        if (eliminationState == 0) {
            while (true) {
                val prevTop = top.value
                val newTop = Node(x, prevTop)
                if (top.compareAndSet(prevTop, newTop)) {
                    return
                }
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val el = random.nextInt(ELIMINATION_ARRAY_SIZE)
        var eliminationState = 0
        for (i in 0 until ELIMINATION_CYCLE_COUNT) {
            val y = eliminationArray[el].value
            if (y is TreiberStackWithElimination<*>.EliminationValue && eliminationArray[el].compareAndSet(y, EliminationSuccess)) {
                return y.e as E?
            }
        }

        while (true) {
            val prevNode = top.value ?: return null
            if (top.compareAndSet(prevNode, prevNode.next)) {
                return prevNode.x
            }
        }
    }

    private sealed class EliminationArrayElement
    private object EliminationSuccess : EliminationArrayElement()
    private inner class EliminationValue(val e: E) : EliminationArrayElement()
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val ELIMINATION_CYCLE_COUNT = 1000 // DO NOT CHANGE IT
