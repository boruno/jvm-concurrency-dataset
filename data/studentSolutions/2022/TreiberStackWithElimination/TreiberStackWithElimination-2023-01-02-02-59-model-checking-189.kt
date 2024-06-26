package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Node<E>?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val newNode = Node(x, null)
        val randPos = Random.nextInt(eliminationArray.size)
        if (eliminationArray[randPos].compareAndSet(null, newNode)) {
            (0 until ITERATIONS).forEach { it -> }
            if (!eliminationArray[randPos].compareAndSet(newNode, null)) return
        }
        while (true) {
            val oldHead = top.value
            val newHead = Node(x, oldHead)
            if (top.compareAndSet(oldHead, newHead)) return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val randPos = Random.nextInt(eliminationArray.size)
        if (!eliminationArray[randPos].compareAndSet(null, null)) {
            val top = eliminationArray[randPos].value
            if (top !== null && eliminationArray[randPos].compareAndSet(top, null)) return top.x
        }
        while (true) {
            val oldHead = top.value
            if (oldHead != null && top.compareAndSet(
                    oldHead,
                    oldHead.next
                )
            ) return oldHead.x else if (oldHead == null) return null
        }
    }
}

private class Node<E>(val x: E? = null, val next: Node<E>? = null)

private const val ITERATIONS = 5

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

//class TreiberStackWithElimination<E> {
//
//
//    // head pointer
//    private val head = AtomicRef<Node>(null)
//
//    private val rand = Random()
//    private val eliminationArray = Collections.nCopies(SIZE, AtomicRef(emptyElement))
//    fun push(x: Int) {
//        val newNode = Node(x, null)
//        val randPos = rand.nextInt(eliminationArray.size - 2 * WINDOW) + WINDOW
//        for (i in randPos - WINDOW until randPos + WINDOW) {
//            if (eliminationArray[i].compareAndSet(emptyElement, newNode)) {
//                for (wait in 0 until WAIT) {
//                }
//                if (!eliminationArray[i].compareAndSet(newNode, emptyElement)) return
//                break
//            }
//        }
//        while (true) {
//            val oldHead = head.value
//            val newHead = Node(x, oldHead)
//            if (head.compareAndSet(oldHead, newHead)) return
//        }
//        //head.setValue(new Node(x, head.getValue()));
//    }
//
//    fun pop(): Int {
//        val randPos = rand.nextInt(eliminationArray.size - 2 * WINDOW) + WINDOW
//        for (i in randPos - WINDOW until randPos + WINDOW) {
//            if (eliminationArray[i].compareAndSet(emptyElement, emptyElement)) continue
//            val top = eliminationArray[i].value
//            if (top !== emptyElement && eliminationArray[i].compareAndSet(top, emptyElement)) return top.x
//        }
//        while (true) {
//            val oldHead = head.value
//            if (oldHead != null && head.compareAndSet(
//                    oldHead,
//                    oldHead.next.value
//                )
//            ) return oldHead.x else if (oldHead == null) return Int.MIN_VALUE
//        }
//        /*Node curHead = head.getValue();
//        if (curHead == null) return Integer.MIN_VALUE;
//        head.setValue(curHead.next.getValue());
//        return curHead.x;*/
//    }
//
//    companion object {
//        private const val SIZE = 50
//        private const val WINDOW = 10
//        private const val WAIT = 5
//    }
//}
//
//private class Node {
//    val next: AtomicRef<Node>
//    val x: Int
//
//    internal constructor() {
//        next = AtomicRef(null)
//        x = Int.MIN_VALUE
//    }
//
//    internal constructor(x: Int, next: Node?) {
//        this.next = AtomicRef(next)
//        this.x = x
//    }
//}