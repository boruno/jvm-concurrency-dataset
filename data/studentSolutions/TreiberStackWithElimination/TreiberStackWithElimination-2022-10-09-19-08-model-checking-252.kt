package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
//        val i = Random().nextInt(2) % ELIMINATION_ARRAY_SIZE
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            if (eliminationArray[i].compareAndSet(null, x)) {
                var j = 0
                while (eliminationArray[i].value != null && j < 100) {
                    j += 1
                }
                if (j < 1000)
                    return
                else {
                    if (eliminationArray[i].compareAndSet(x, null))
                        continue
                    else
                        return
                }
            }
        }
        while (true) {
            val node = Node(x, top.value)
            if (top.compareAndSet(node.next, node))
                return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
//        val i = Random().nextInt(2) % ELIMINATION_ARRAY_SIZE
//        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
//
//        val result = eliminationArray[i].value
//            if (result != null) {
//                if (eliminationArray[i].compareAndSet(result, null))
//                    return result as E
//            }
//        }
        while (true) {
            val node = top.value ?: return null
            if (top.compareAndSet(node, node.next)){
                System.out.println("pop "+ node.x.toString())
                return node.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT