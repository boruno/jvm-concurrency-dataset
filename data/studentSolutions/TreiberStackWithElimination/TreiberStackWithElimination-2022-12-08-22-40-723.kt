package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

enum class StackOperation {
    PUSH,
    POP,
}

enum class OperationResult {
    ELIMINATED,
    PUT,
    NO_SUCCESS
}

class ThreadInfo<E>(val operation: StackOperation, var availible: Boolean, var value: E?)

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<ThreadInfo<E>>(ELIMINATION_ARRAY_SIZE)

    fun tryEliminate(operation: StackOperation, x: E?): Triple<OperationResult, Int, E?> {
        for (i in 0 until ELIMINATION_ITERS) {
            var pos = Random.nextInt() % ELIMINATION_ARRAY_SIZE
            val curInfo = ThreadInfo(operation, true, x)
            if (eliminationArray[pos].compareAndSet(null, curInfo)) {
                return Triple(OperationResult.PUT, pos, null)
            }
            val otherInfo: ThreadInfo<E>? = eliminationArray[pos].value
            if (otherInfo == null || !otherInfo.availible) {
                continue
            }
            if (otherInfo.operation == operation) {
                continue
            }
            val newInfo = ThreadInfo(operation, false, x)
            if (operation == StackOperation.PUSH) {
                if (eliminationArray[pos].compareAndSet(otherInfo, newInfo)) {
                    return Triple(OperationResult.ELIMINATED, pos, null)
                }
            } else {
                val ret = otherInfo.value
                if (eliminationArray[pos].compareAndSet(otherInfo, newInfo)) {
                    return Triple(OperationResult.ELIMINATED, pos, ret)
                }
            }
        }
        return Triple(OperationResult.NO_SUCCESS, -1, null)
    }

    fun tryGetResult(pos: Int): Pair<Boolean, E?> {
        for (i in 0 until WAIT_MEET_ITERS) {
            val threadInfo = eliminationArray[i].value
            if (threadInfo != null && !threadInfo.availible) {
                val ret = threadInfo.value
                eliminationArray[i].value = null
                return true to ret
            }
        }
        return false to null
    }

    fun removeFromEliminationArray(pos: Int) {
        eliminationArray[pos].value = null
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val (success, pos, _) = tryEliminate(StackOperation.PUSH, x)
        if (success == OperationResult.ELIMINATED) {
            return
        } else if (success == OperationResult.PUT) {
            if (tryGetResult(pos).first) {
                return
            } else {
                removeFromEliminationArray(pos)
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
        val (success, pos, result) = tryEliminate(StackOperation.POP, null)
        if (success == OperationResult.ELIMINATED) {
            return result
        } else if (success == OperationResult.PUT) {
            val (got, res) = tryGetResult(pos)
            if (got) {
                return res
            } else {
                removeFromEliminationArray(pos)
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
private const val ELIMINATION_ITERS = 3
private const val WAIT_MEET_ITERS = 5
