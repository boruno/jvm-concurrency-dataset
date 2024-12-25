//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
@Suppress("UNCHECKED_CAST")
class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    private val time = 2;

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var ind = (ThreadLocalRandom.current().nextInt() % ELIMINATION_ARRAY_SIZE + ELIMINATION_ARRAY_SIZE) % ELIMINATION_ARRAY_SIZE;
        var find = false;
        for (i in 0 until time) {
            if (eliminationArray[ind].compareAndSet(null, x)) {
                find = true;
                break
            }
            ind = (ind + 1) % ELIMINATION_ARRAY_SIZE;
        }
        if (find) {
            for (i in 0 until time) {
                if (!eliminationArray[ind].compareAndSet(x, x)) {
                    return
                }
            }
            if (eliminationArray[ind].compareAndSet(x, null)) {
                push2(x);
            }
        } else {
            push2(x);
        }
    }

    fun push2(x: E) {
        while (true) {
            val curTop = top.value;
            val newTop = Node(x, curTop);
            if (top.compareAndSet(curTop, newTop)) {
                return
            };
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var ind = (ThreadLocalRandom.current().nextInt() % ELIMINATION_ARRAY_SIZE + ELIMINATION_ARRAY_SIZE) % ELIMINATION_ARRAY_SIZE;

        for (i in 0 until time) {
            val node = eliminationArray[ind].value;
            if (node != null && eliminationArray[ind].compareAndSet(node, null)) {
                return (node as E);
            }
            ind = (ind + 1) % ELIMINATION_ARRAY_SIZE;
        }

        return pop2();
    }
    fun pop2(): E? {
        while(true) {
            val curTop = top.value ?: return null;
            val newTop = curTop.next;
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x;
            }
        }
    }

}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT