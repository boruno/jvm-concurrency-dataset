package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class TreiberStackWithElimination<E> {
    private class ConcurrentStack<E> {
        private inner class Node(val x: E, next: Node?) {
            val next = atomic(next)
        }

        private val head = atomic<Node?>(null)
        fun push(x: E) {
            while (true) {
                val curHead = head.value
                val newHeadNode = Node(x, curHead)
                if (head.compareAndSet(curHead, newHeadNode)) {
                    break
                }
            }
        }

        fun pop(): E? {
            while (true) {
                val curHead = head.value ?: return null
                if (head.compareAndSet(curHead, curHead.next.value)) {
                    return curHead.x
                }
            }
        }
    }

    private class EliminationArray<E> {
        private val random = Random()
        private val eliminationArray = atomicArrayOfNulls<Item<E>>(ELIMINATION_ARRAY_SIZE)

        init {
            for (i in 0 until ELIMINATION_ARRAY_SIZE) {
                eliminationArray[i].value = Item<E>(null, Item.State.FREE)
            }
        }

        private class Item<E>(val value: E?, val state: State) {
            enum class State {
                FREE, AWAITING_POP, DONE
            }
        }

        enum class OperationState {
            SUCCESS, FAIL
        }

        fun push(x: E): OperationState {
            var index: Int? = null
            for (i in 0 until pushTries) {
                index = random.nextInt(ELIMINATION_ARRAY_SIZE)
                val item = eliminationArray[index].value!!
                if (item.state == Item.State.FREE && eliminationArray[index].compareAndSet(
                        item,
                        Item(x, Item.State.AWAITING_POP)
                    )
                ) {
                    break
                } else {
                    eliminationArray[index].value = null
                }
            }
            if (index == null) {
                return OperationState.FAIL
            }
            for (i in 0 until spinWaitIterations) {
                if (eliminationArray[index].value!!.state == Item.State.DONE) {
                    break
                }
            }
            val finalState = eliminationArray[index].getAndSet(Item(null, Item.State.FREE))!!.state
            return if (finalState == Item.State.DONE) {
                OperationState.SUCCESS
            } else {
                OperationState.FAIL
            }
        }

        data class PopResult<E>(val operationState: OperationState, val result: E?)

        fun pop(): PopResult<E> {
            for (i in 0 until popTries) {
                val index = random.nextInt(ELIMINATION_ARRAY_SIZE)
                val itemAtomicRef = eliminationArray[index]
                val item = itemAtomicRef.value
                if (item!!.state == Item.State.AWAITING_POP && itemAtomicRef.compareAndSet(
                        item,
                        Item(null, Item.State.DONE)
                    )
                ) {
                    return PopResult(OperationState.SUCCESS, item.value)
                }
            }
            return PopResult(OperationState.FAIL, null)
        }

        companion object {
            private const val pushTries = 3
            private const val popTries = 3
            private const val spinWaitIterations = 1000
        }
    }

    private val concurrentStack = ConcurrentStack<E>()
    private val eliminationArray = EliminationArray<E>()


    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (eliminationArray.push(x) == EliminationArray.OperationState.FAIL) {
            concurrentStack.push(x)
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val eliminationArrayPopResult: EliminationArray.PopResult<E> = eliminationArray.pop()
        return if (eliminationArrayPopResult.operationState == EliminationArray.OperationState.FAIL) {
            concurrentStack.pop()
        } else {
            eliminationArrayPopResult.result
        }
    }
}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT