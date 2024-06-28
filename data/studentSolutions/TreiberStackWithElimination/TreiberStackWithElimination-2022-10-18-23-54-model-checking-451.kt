package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    constructor() {
        for (index in 0 until ELIMINATION_ARRAY_SIZE) {
            eliminationArray[index].value = StackOperationInfo(null, StackOperationState.Empty)
        }
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val eliminationResult = makeElimination(x, StackOperationType.Push)

        if (eliminationResult.result == EliminationResultType.Success)
            return

        val currentTop = top.value
        val newTop = Node(x, currentTop)

        top.compareAndSet(currentTop, newTop)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val eliminationResult = makeElimination(null, StackOperationType.Pop)

        if (eliminationResult.result == EliminationResultType.Success)
            return eliminationResult.value

        val currentTop = top.value ?: return null
        val newTop = currentTop.next

        top.compareAndSet(currentTop, newTop)
        return currentTop.x
    }

    private fun makeElimination(x: E?, operation: StackOperationType): EliminationResult<E> {
        val eliminationIndex = ThreadLocalRandom.current().nextInt(0, ELIMINATION_ARRAY_SIZE)

        when (operation) {
            StackOperationType.Push -> {
                val emptyInfo = StackOperationInfo(null, StackOperationState.Empty)
                val info = StackOperationInfo(x, StackOperationState.Waiting)

                if (eliminationArray[eliminationIndex].compareAndSet(emptyInfo, info)) {
                    for (iteration in 0..1000) {
                        val currentInfo = eliminationArray[eliminationIndex].value as StackOperationInfo<*>

                        if (currentInfo.state == StackOperationState.Finished) {
                            val newInfo = StackOperationInfo(null, StackOperationState.Empty)
                            eliminationArray[eliminationIndex].compareAndSet(info, newInfo)

                            return EliminationResult(null, EliminationResultType.Success)
                        }
                    }

                    eliminationArray[eliminationIndex].compareAndSet(info, emptyInfo)
                    return EliminationResult(null, EliminationResultType.Failure)
                } else return EliminationResult(null, EliminationResultType.Failure)
            }
            StackOperationType.Pop -> {
                val info = eliminationArray[eliminationIndex].value as StackOperationInfo<*>

                if (info.state == StackOperationState.Waiting) {
                    val newInfo = StackOperationInfo(x, StackOperationState.Finished)
                    eliminationArray[eliminationIndex].compareAndSet(info, newInfo)

                    return EliminationResult(info.value as E, EliminationResultType.Success)
                } else return EliminationResult(null, EliminationResultType.Failure)
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private data class StackOperationInfo<E>(val value: E?, val state: StackOperationState)
private data class EliminationResult<E>(val value: E?, val result: EliminationResultType)

private enum class StackOperationType {
    Push,
    Pop,
}

private enum class StackOperationState {
    Empty,
    Waiting,
    Finished,
}

private enum class EliminationResultType {
    Success,
    Failure,
}