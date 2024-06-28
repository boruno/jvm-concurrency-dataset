package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    fun eliminate(node: Any?): Any? {
        if(eliminationArray[0].equals(null)) {
            val old = eliminationArray[0].value as Node<E>
            eliminationArray[0].compareAndSet(old, node)
            return node
        } else if (eliminationArray[1].equals(null)) {
            val old = eliminationArray[1].value as Node<E>
            eliminationArray[1].compareAndSet(old, node)
            return node
        } else {
            val old0 = eliminationArray[0].value as Node<E>
            val old1 = eliminationArray[1].value as Node<E>
            eliminationArray[0].compareAndSet(old0, old1)
            eliminationArray[1].compareAndSet(old1, node)
        }
        return null
    }

    fun deeliminate(): Any? {
        if(eliminationArray[0].equals(null))
            return null
        val node = eliminationArray[0].value as Node<E>
        node.done = true

        val old1 = eliminationArray[1].value as Node<E>

        eliminationArray[0].compareAndSet(node, old1)
        eliminationArray[1].compareAndSet(old1, null)

        return node
    }

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
        val old = top.value
        val new = Node(x, old)
        eliminate(new)

        for(i in 1..100)
            if(new.done) {
            System.out.println("DONE");
                return;
            }

        push0(x)
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
        val eliminated = deeliminate()

        if(eliminated != null)
            return (eliminated as Node<E>).x

        return pop0();
    }
}

private class Node<E>(val x: E, val next: Node<E>?, var done: Boolean = false)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT