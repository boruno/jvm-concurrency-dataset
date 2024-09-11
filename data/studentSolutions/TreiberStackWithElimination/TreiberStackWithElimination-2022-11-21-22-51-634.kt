package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

const val SPECIAL_VALUE = "This is special value";

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var randomCellNumber = Random.nextInt(0, 2);
        if (eliminationArray[randomCellNumber].compareAndSet(null, x)) {
            for (i in 1..10) {
                if (eliminationArray[randomCellNumber].compareAndSet(SPECIAL_VALUE, null)) {
                    return;
                }
            }
            if (!eliminationArray[randomCellNumber].compareAndSet(x, null)) {
                if (eliminationArray[randomCellNumber].compareAndSet(SPECIAL_VALUE, null)) {
                    return;
                }
            }
        }
        while (true){
            val node = Node(x, top.value);
            if (top.compareAndSet(node.next, node)){
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
        var randomCellNumber = Random.nextInt(0, 2);
        for (i in 1..10) {
            if (eliminationArray[randomCellNumber].value != null) {
                var x = eliminationArray[randomCellNumber].value;
                if (eliminationArray[randomCellNumber].compareAndSet(x, SPECIAL_VALUE)) {
                    return x as E;
                }
                else {
                    break;
                }
            }
        }
        while (true){
            val node = top.value;
            if (node == null) {
                return null;
            };
            if (top.compareAndSet(node, node.next)){
                return node.x;
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT