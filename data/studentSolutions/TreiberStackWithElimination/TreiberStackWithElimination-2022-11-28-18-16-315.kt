package mpp.stackWithElimination

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE) // Any?

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
//        TODO("implement me")
        var elimIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        for (step in 0 until ELIMINATION_ARRAY_SIZE) { // wait_size
            elimIndex = (elimIndex + step) % ELIMINATION_ARRAY_SIZE
            if (eliminationArray[elimIndex].compareAndSet(null, x)) {
                break
            }
        }
        if (!eliminationArray[elimIndex].compareAndSet(null, null)) {
            for (i in 0 until ELIMINATION_ARRAY_SIZE) { // wait_size
//                if (positionRef.compareAndSet(null, null)) {
//                    return
//                }
            }

//            if (!positionRef.compareAndSet(x, null)) {
//                return
//            }
        }
        var old: Node<E>?
        var new: Node<E>
        while (true) {
            old = top.value
            new = Node(x, old)
            if (top.compareAndSet(old, new)) {
                break
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
//        TODO("implement me")
        val elimIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        var elementReference: AtomicRef<E?>

//        for (step in 0 until ELIMINATION_ARRAY_SIZE) { // wait_size
//            elementReference = eliminationArray[(elimIndex + step) % ELIMINATION_ARRAY_SIZE]
//            for (i in 0 until ELIMINATION_ARRAY_SIZE) { // wait_size
//                val element: E? = elementReference.getAndSet(null)
//                if (element != null) {
//                    return element
//                }
//            }
//        }

        var old: Node<E>?
        var new: Node<E>?
        while (true) {
            old = top.value
            if (old == null) {
                return null
            }
            new = old.next
            if (top.compareAndSet(old, new)) {
                break
            }
        }
        return old!!.x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT