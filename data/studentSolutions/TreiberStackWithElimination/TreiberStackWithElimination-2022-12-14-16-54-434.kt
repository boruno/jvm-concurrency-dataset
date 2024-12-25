//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Pair<E?, Node<E>?>>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val cur_top = top.value
            val new_top = Node(x, cur_top)
            if (top.compareAndSet(cur_top, new_top)) {
                return
            } else {
                val index = (0 until ELIMINATION_ARRAY_SIZE).random()
                if (!eliminationArray[index].compareAndSet(null, Pair(x, cur_top))) {
                    val p = eliminationArray[index].value!!
                    if(p.first === null){
                        eliminationArray[index].lazySet(Pair(x, cur_top))
                    }
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
        while (true) {
            val cur_top = top.value
            if (cur_top === null) return null
            val new_top = cur_top.next
            if (top.compareAndSet(cur_top, new_top)) {
                return cur_top.x
            } else {
                val index = (0 until ELIMINATION_ARRAY_SIZE).random()
                if (!eliminationArray[index].compareAndSet(null, Pair(null, cur_top))) {
                    val p = eliminationArray[index].value!!
                    if(p.first !== null){
                        eliminationArray[index].lazySet(null)
                        return p.first
                    }
                }
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT