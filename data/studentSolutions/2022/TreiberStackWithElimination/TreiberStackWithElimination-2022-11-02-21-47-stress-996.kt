package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Operation<E>>(ELIMINATION_ARRAY_SIZE)

    private val random = Random
    private val timeout = 10 // nanos

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            if (tryPush(x)) {
                return
            }

            val (isSuccess, _) = tryEliminate("push", x)
            if (isSuccess) {
                return
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
            val (isSuccess, value) = tryPop()
            if (isSuccess) return value

            val (isSuccessE, valueE) = tryEliminate("pop", null)
            if (isSuccessE) {
                return valueE
            }
        }
    }

    private fun tryPush(x: E): Boolean {
        val curTop = top.value
        val newTop = Node(x, curTop)
        return top.compareAndSet(curTop, newTop)
    }

    private fun tryPop(): Pair<Boolean, E?> {
        val curTop = top.value ?: return Pair(true, null)
        val newTop = curTop.next
        return Pair(top.compareAndSet(curTop, newTop), curTop.x)
    }

    private fun tryEliminate(type: String, value: E?): Pair<Boolean, E?> {
        val index = random.nextInt(ELIMINATION_ARRAY_SIZE)

        val limitTime = System.nanoTime() + timeout
        while (System.nanoTime() < limitTime) {
            val currentSlotValue = eliminationArray[index].value

            if (currentSlotValue == null) {
                if (eliminationArray[index].compareAndSet(null, Operation("wait", type, value))) {
                    while (System.nanoTime() < limitTime) {
                        if (eliminationArray[index].value!!.status == "busy") {
                            var newValue = eliminationArray[index].getAndSet(null)

                            return Pair(true, newValue!!.x)
                        }
                    }

//                    for (i in 1..100) {
//                        if (eliminationArray[index].value!!.status == "busy") {
//                            var newValue = eliminationArray[index].getAndSet(null)
//
//                            return Pair(true, newValue!!.x)
//                        }
//                    }

                    eliminationArray[index].getAndSet(null)
                    return Pair(false, null)
                }
            }
            else if (currentSlotValue.status == "wait") {
                if (type == currentSlotValue.type) {
                    return Pair(false, null)
                }

                if (eliminationArray[index].compareAndSet(currentSlotValue, Operation("busy", currentSlotValue.type, currentSlotValue.x))) {
                    return Pair(true, currentSlotValue.x)
                }
            }
            else if (currentSlotValue.status == "busy") {
                break
            }
        }

        return Pair(false, null)
    }
}

private class Node<E>(val x: E, val next: Node<E>?)
private class Operation<E>(val status: String, val type: String?, val x: E?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT