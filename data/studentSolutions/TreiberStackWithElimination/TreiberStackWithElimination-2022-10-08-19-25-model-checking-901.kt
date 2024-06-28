package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Operation<E>?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val eliminationIndex = Random.nextInt(0, ELIMINATION_ARRAY_SIZE);
        if (eliminationArray[eliminationIndex].value == null)
        {
            if(eliminationArray[eliminationIndex].compareAndSet(null, Operation(x, false)))
            {
                for(i in ELIMINATION_WAIT_CYCLES downTo 0)
                {
                    if (eliminationArray[eliminationIndex].value!!.done)
                    {
                        eliminationArray[eliminationIndex].value = null;
                    }
                }
                val currentElimination = eliminationArray[eliminationIndex].value;
                if (!eliminationArray[eliminationIndex].compareAndSet(currentElimination, null))
                    return;
            }
        }
        while(true) {
            if (top.value == null) {
                if(top.compareAndSet(null, Node<E>(x, null)))
                    return;
            }
            else
            {
                val oldTop = top.value;
                val newTop = Node<E>(x, oldTop);
                if (top.compareAndSet(oldTop, newTop))
                    return;
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val eliminationIndex = Random.nextInt(0, ELIMINATION_ARRAY_SIZE);
        if (eliminationArray[eliminationIndex].value != null) {
            var result = eliminationArray[eliminationIndex].value!!.x;
            var currentElimination = eliminationArray[eliminationIndex].value;
            if (eliminationArray[eliminationIndex].compareAndSet(currentElimination, Operation(result, true)))
                return result;
        }
        while(true)
        {
            val oldTop = top.value ?: return null;
            val result = oldTop.x;
            val next = oldTop.next;
            if(top.compareAndSet(oldTop, next))
            {
                return result;
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)
private class Operation<E>(val x: E, val done: Boolean)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val ELIMINATION_WAIT_CYCLES = 100 // DO NOT CHANGE IT