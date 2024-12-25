//package mpp.stack

import kotlinx.atomicfu.atomic
import kotlin.random.Random

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomic(ArrayList<E?>())

    init {
        repeat(ELIMINATION_ARRAY_SIZE) {
            eliminationArray.value.add(null)
        }
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun pushInStack(x: E) {
        while (true) {
            val cur_top = top.value
            val new_top = Node(x, cur_top)
            if (top.compareAndSet(cur_top, new_top))
                return
        }
    }

    fun push(x: E) {
        while (true) {
            val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            val curArray = eliminationArray.value
            val copyArray = curArray.toList() as ArrayList<E?>
            val randomElement = copyArray[index]
            if (randomElement == null) {
                copyArray[index] = x
                if (eliminationArray.compareAndSet(curArray, copyArray)) {
                    repeat(WAIT_STEPS) {
                        if (eliminationArray.value[index] != x) {
                            return
                        }
                    }
                    if (eliminationArray.value[index] == x) {
                        val curArray2 = eliminationArray.value
                        val copyArray2 = curArray2.toList() as ArrayList<E?>
                        copyArray2[index] = null
                        if (eliminationArray.compareAndSet(curArray2, copyArray2)) {
                            pushInStack(x)
                        }
                    }
                    return
                } else {
                    continue
                }
            } else {
                pushInStack(x)
                return
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun popFromStack(): E? {
        while (true) {
            val cur_top = top.value
            if (cur_top == null)
                return null
            val new_top = cur_top.next
            if (top.compareAndSet(cur_top, new_top))
                return cur_top.x
        }
    }

    fun pop(): E? {
        while (true) {
            val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            val curArray = eliminationArray.value
            val copyArray = curArray.toList() as ArrayList<E?>
            val randomElement = copyArray[index]
            if (randomElement == null) {
                return popFromStack()
            }
            copyArray[index] = null
            if (eliminationArray.compareAndSet(curArray, copyArray)) {
                return randomElement
            }
        }
    }

}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val WAIT_STEPS = 1000