package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Operation<E?>>(ELIMINATION_ARRAY_SIZE)

    private val random = Random
    private val timeout = 0

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val (isSuccess, _) = tryEliminate("push", x)
        if (isSuccess) {
            return
        }

        pushToStack(x)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val (isSuccessE, valueE) = tryEliminate("pop", null)
        if (isSuccessE) {
            return valueE
        }

        return popFromStack()
    }

    private fun pushToStack(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) {
                return
            }
        }
    }

    private fun popFromStack(): E? {
        while (true) {
            val curTop = top.value ?: return null

            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }

    private fun tryEliminate(type: String, value: E?): Pair<Boolean, E?> {
        val index = random.nextInt(ELIMINATION_ARRAY_SIZE)

        var limitTime = System.currentTimeMillis() + random.nextInt(timeout)
        while (System.currentTimeMillis() < limitTime) {
            val currentSlotValue = eliminationArray[index].value

            if (currentSlotValue == null) {
                if (addFirst(index, value)) {
                    while (System.currentTimeMillis() < limitTime) {
                        val x = clear(index)

                        if (x != null) return Pair(true, x)
                    }

                    eliminationArray[index].getAndSet(null)
                    return Pair(false, null)
                }

                break;
            }
            else if (addSecond(index, currentSlotValue, value)) {
                return Pair(true, currentSlotValue.value)
            }
            else {
                break
            }
        }

        return Pair(false, null)
    }

    // 1. Add 1st value to slot.
    // 2. Set its stamp as WAIT (for 2nd).
    private fun addFirst(index: Int, value: E?): Boolean {
        return eliminationArray[index].compareAndSet(null, Operation("wait", value))
    }

    // 1. Add 2nd value to slot.
    // 2. Set its stamp as BUSY (for 1st to remove).
    private fun addSecond(index: Int, first: Operation<E?>, secondValue: E?): Boolean {
        return eliminationArray[index].compareAndSet(first, Operation("busy", secondValue))
    }

    // 1. If stamp is not BUSY (no 2nd value in slot), exit.
    // 2. Set slot as EMPTY, and get 2nd value from slot.
    private fun clear(index: Int): E? {
        val x = eliminationArray[index].value
        if (x?.status != "busy") return null
        eliminationArray[index].getAndSet(null)
        return x.value
    }
}

private class Node<E>(val x: E, val next: Node<E>?)
private class Operation<E>(val status: String, val value: E?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT