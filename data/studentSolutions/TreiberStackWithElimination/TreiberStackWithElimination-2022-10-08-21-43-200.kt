//package mpp.stackWithElimination

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
                else
                {
                    for (i in ELIMINATION_TRY_FIND_CYCLES downTo 0) {
                        val eliminationIndex = Random.nextInt(0, ELIMINATION_ARRAY_SIZE);
                        if (eliminationArray[eliminationIndex].value != null)
                            continue;
                        else
                        {
                            if(eliminationArray[eliminationIndex].compareAndSet(null, Operation(x, false)))
                            {
                                for (j in ELIMINATION_WAIT_CYCLES downTo 0)
                                {
                                    if (eliminationArray[eliminationIndex].value!!.done)
                                    {
                                        eliminationArray[eliminationIndex].value = null;
                                        return;
                                    }
                                }
                                val eliminationElement = eliminationArray[eliminationIndex].value;
                                if (eliminationArray[eliminationIndex].compareAndSet(eliminationElement, null))
                                {
                                    return;
                                }
                                else
                                {
                                    if (eliminationArray[eliminationIndex].value!!.done) {
                                        eliminationArray[eliminationIndex].value = null;
                                        return;
                                    }
                                }
                            }
                            else
                                break;
                        }
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
        for (i in ELIMINATION_TRY_FIND_CYCLES downTo 0) {
            val eliminationIndex = Random.nextInt(0, ELIMINATION_ARRAY_SIZE);
            if (eliminationArray[eliminationIndex].value == null)
                continue;
            else
            {
                val resultElimination = eliminationArray[eliminationIndex].value
                if (resultElimination == null || resultElimination.done)
                    break;
                if (eliminationArray[eliminationIndex].compareAndSet(resultElimination, Operation(resultElimination.x, true)))
                {
                    return resultElimination.x;
                }
                else
                    break;
            }
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
            else
            {

            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)
private class Operation<E>(val x: E, val done: Boolean)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val ELIMINATION_WAIT_CYCLES = 2

private const val ELIMINATION_TRY_FIND_CYCLES = 2