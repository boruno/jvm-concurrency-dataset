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
        val elimIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val positionRef = write(x, elimIndex)
//        if (positionRef != null) {
//            for (i in 0 until ELIMINATION_ARRAY_SIZE) { // wait_size
//                if (positionRef.compareAndSet(null, null)) {
//                    return
//                }
//            }
//
//            if (!positionRef.compareAndSet(x, null)) {
//                return
//            }
//        }
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

    private fun write(elementRef: E, index: Int): AtomicRef<E?>? {
        var positionRef: AtomicRef<E?>
        for (step in 0 until ELIMINATION_ARRAY_SIZE) { // wait_size
            positionRef = eliminationArray[(index + step) % ELIMINATION_ARRAY_SIZE]
            if (positionRef.compareAndSet(null, elementRef)) {
                return positionRef
            }
        }
        return null
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

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            print("Hello")
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT