//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
//            try elimination array
//            try to find random empty spot in eliminationArray
            repeat(4) {
                val randomInd = Random(RAND_SEED).nextInt(ELIMINATION_ARRAY_SIZE)
                if (eliminationArray[randomInd].compareAndSet(null, x)) {
//                  wait until someone takes the value
                    repeat(100) {
                        if (eliminationArray[randomInd].value == null) {
                            return
                        }
                    }
//                  in no one came –– clear the spot
                    eliminationArray[randomInd].compareAndSet(x, null)
                }
            }
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            //            try to find random non-empty spot in eliminationArray
            repeat(4) {
                val randomInd = Random(RAND_SEED).nextInt(ELIMINATION_ARRAY_SIZE)
                if (eliminationArray[randomInd].value != null) {
                    val retVal = eliminationArray[randomInd].value
                    if (eliminationArray[randomInd].compareAndSet(retVal, null)) {
                        return retVal as E
                    }
                }
            }
            val curTop: Node<E> = top.value ?: return null
            val newTop = curTop.next
            if( top.compareAndSet(curTop, newTop)) return curTop.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val RAND_SEED = 42 // DO NOT CHANGE IT