package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Cell<E>>(ELIMINATION_ARRAY_SIZE)

    private val done = Cell<E>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val index = Random.nextInt(0, ELIMINATION_ARRAY_SIZE - 1)
        val me = Cell(x)

        if(eliminationArray[index].compareAndSet(null, me)) {
            for (i in 0..1000) {
                if (eliminationArray[index].compareAndSet(done, null)) {
                    return
                }
            }

            if (!eliminationArray[index].compareAndSet(me, null)) return
        }
        while(true){
            val topSnapshot = top.value
            val node = Node(x, topSnapshot)
            if(top.compareAndSet(topSnapshot, node)) return
        }
    }


    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val index = Random.nextInt(0, ELIMINATION_ARRAY_SIZE - 1)
        val him = eliminationArray[index].value
        if(him != null && eliminationArray[index].compareAndSet(him, done)) {
            return him.x
        }
        while (true){
            val topSnapshot = top.value ?: return null
            if(top.compareAndSet(topSnapshot, topSnapshot.next)) return topSnapshot.x
        }
    }
}


private class Cell<E> (var x: E?)

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT