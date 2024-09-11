package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<EliminationValue<E>>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    private fun pushLockFree(x: E){
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) {
                return
            }
        }
    }

    private fun popLockFree(): E?{
        while (true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }

    fun push(x: E) {
        val value = EliminationValue(x, "push")
        for (i in 0..100 ) {
            val index = Random.nextInt(0, 100)
            if (eliminationArray[index].compareAndSet(null, value)){
                for (j in 0.. 20){
                    eliminationArray[index].value ?: return

                }
                val eliminationState = eliminationArray[index].value
                if ((eliminationState != null) &&
                    (eliminationState.command == "pop") &&
                    eliminationState.x == null &&
                    eliminationArray[index].compareAndSet(eliminationState, EliminationValue(x, "pop"))
                ){
                    return
                }
                if (!eliminationArray[index].compareAndSet(value, null)){
                    return
                }
                break
            }
        }
        pushLockFree(x)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        for (i in 0..100){
            val index = Random.nextInt(0, 100)
            val element = eliminationArray[index].value
            if (element != null && element.command =="push" && element.x != null && eliminationArray[index].compareAndSet(element, null)) {
                return element.x
            }
        }
        return popLockFree()
    }
}

private class Node<E>(val x: E, val next: Node<E>?)
private class EliminationValue<E>(val x: E, val command: String)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT