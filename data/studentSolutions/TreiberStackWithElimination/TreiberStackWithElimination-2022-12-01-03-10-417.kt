//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Classic push() implementation
     */
    fun push0(x: E) {
        while(true) {
            val old = top.value
            val new = Node(x, old)
            if(top.compareAndSet(old, new))
                return
        }
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val elIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val el = eliminationArray[elIndex].value
        if(el == null) {
            if(eliminationArray[elIndex].compareAndSet(null, x)) {
                for(i in 1..10000) {}
                if(eliminationArray[elIndex].compareAndSet(x, null))
                   return push0(x)
            } else return push0(x)
        } else return push0(x)
    }

    /**
     * Classic pop() implementation
     */
    fun pop0(): E? {
        while(true) {
            val old = top.value ?: return null
            if(top.compareAndSet(old, old.next))
                return old.x
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val elIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        while (true) {
            val el = eliminationArray[elIndex].value ?: return pop0()
            if(eliminationArray[elIndex].compareAndSet(el, null))
                return el
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT