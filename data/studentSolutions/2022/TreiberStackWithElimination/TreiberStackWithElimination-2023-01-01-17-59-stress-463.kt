package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
@Suppress("UNCHECKED_CAST")
class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    private val time = 100;

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val ind = (ThreadLocalRandom.current().nextInt() % ELIMINATION_ARRAY_SIZE + ELIMINATION_ARRAY_SIZE) % ELIMINATION_ARRAY_SIZE;
        val node = Node(x, null);
        var find = false;
        for (i in 0 until time) {
            if (eliminationArray[ind].compareAndSet(null, node)) {
                find = true;
                break
            }
        }
        if (find) {
            for (i in 0 until time) {
                if (!eliminationArray[ind].compareAndSet(node, node)) {
                    return
                }
            }
            if (eliminationArray[ind].compareAndSet(node, null)) {
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
            if (top.compareAndSet(curTop, newTop)) return;
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val ind = (ThreadLocalRandom.current().nextInt() % ELIMINATION_ARRAY_SIZE + ELIMINATION_ARRAY_SIZE) % ELIMINATION_ARRAY_SIZE;

        for (i in 0 until time) {
            val node = eliminationArray[ind].value;
            if (node != null && eliminationArray[ind].compareAndSet(node, null)) {
                return node as E;
            }
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