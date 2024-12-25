//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

enum class StackOperation {
    PUSH,
    POP,
}

enum class OperationResult {
    ELIMINATED,
    PUT,
    NO_SUCCESS
}

class ThreadInfo<E>(val id: Long, val operation: StackOperation, var availible: Boolean, var value: E?)

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<ThreadInfo<E>>(ELIMINATION_ARRAY_SIZE)

    fun tryEliminate(operation: StackOperation, x: E?): Pair<OperationResult, E?> {
        for (loc in 0 until ELIMINATION_ARRAY_SIZE) {
            val curInfo = ThreadInfo(Thread.currentThread().id, operation, true, x)
            if (eliminationArray[loc].compareAndSet(null, curInfo)) {
                return OperationResult.PUT to null
            }
            val otherInfo: ThreadInfo<E>? = eliminationArray[loc].value
            if (otherInfo == null || !otherInfo.availible) {
                continue
            }
            if (otherInfo.operation == operation) {
                continue
            }
            if (operation == StackOperation.PUSH) {
                val otherInfoNew = ThreadInfo(otherInfo.id, otherInfo.operation, false, x)
                if (eliminationArray[loc].compareAndSet(otherInfo, otherInfoNew)) {
                    return OperationResult.ELIMINATED to null
                }
            } else {
                val ret = otherInfo.value
                val otherInfoNew = ThreadInfo(otherInfo.id, otherInfo.operation, false, x)
                if (eliminationArray[loc].compareAndSet(otherInfo, otherInfoNew)) {
                    return OperationResult.ELIMINATED to ret
                }
            }
        }
        return OperationResult.NO_SUCCESS to null
    }

    fun tryGetResult(): Pair<Boolean, E?> {
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            val threadInfo = eliminationArray[i].value
            if (threadInfo != null && !threadInfo.availible && threadInfo.id == Thread.currentThread().id) {
                eliminationArray[i].value = null
                return true to threadInfo.value
            }
        }
        return false to null
    }

    fun removeFromEliminationArray() {
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            val threadInfo = eliminationArray[i].value
            if (threadInfo != null && threadInfo.id == Thread.currentThread().id) {
                eliminationArray[i].value = null
            }
        }
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        for (i in 0..3) {
            val (success, _) = tryEliminate(StackOperation.PUSH, x)
            if (success == OperationResult.ELIMINATED) {
                return
            } else if (success == OperationResult.PUT) {
                for (j in 0..10) {
                    val (got, _) = tryGetResult()
                    if (got) {
                        return
                    }
                }
                removeFromEliminationArray()
            }
        }
        var oldTop: Node<E>?
        val newTop = Node(x, null)
        do {
            oldTop = top.value
            newTop.next = oldTop
        } while (!top.compareAndSet(oldTop, newTop))
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        for (i in 0..3) {
            val (success, result) = tryEliminate(StackOperation.POP, null)
            if (success == OperationResult.ELIMINATED) {
                return result
            } else if (success == OperationResult.PUT) {
                for (j in 0..10) {
                    val (got, res) = tryGetResult()
                    if (got) {
                        return res
                    }
                }
                removeFromEliminationArray()
            }
        }
        var oldTop: Node<E>?
        var newTop: Node<E>?
        do {
            oldTop = top.value
            newTop = oldTop?.next
        } while (!top.compareAndSet(oldTop, newTop))
        return oldTop?.x
    }
}

private class Node<E>(val x: E, var next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT