//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Type<E>>(ELIMINATION_ARRAY_SIZE)

    private val done: Type<E> = Type(null, TypeEnum.DONE)
    private val empty: Type<E> = Type(null, TypeEnum.EMPTY)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val random: ThreadLocalRandom = ThreadLocalRandom.current();
        while (true) {
            val n: Int = random.nextInt(ELIMINATION_ARRAY_SIZE);
            val tmp: Type<E> = Type(x, TypeEnum.VERT)
            if (eliminationArray[n].compareAndSet(null, tmp)) {
                for (i in 1..10) {
                    if (eliminationArray[n].value == done) {
                        break
                    }
                }
                if (!eliminationArray[n].compareAndSet(tmp, null)) {
                    eliminationArray[n].value = null
                    return
                }

            }

            val oldTop: Node<E>? = top.value
            val newTop: Node<E> = Node(x, oldTop)

            if (top.compareAndSet(oldTop, newTop)) {
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
        val random: ThreadLocalRandom = ThreadLocalRandom.current();
        while (true) {
            val n: Int = random.nextInt(ELIMINATION_ARRAY_SIZE);
            val value: Type<E>? = eliminationArray[n].value
            if (value != null && value != done) {
                if (eliminationArray[n].compareAndSet(value, done)) {
                    return value.x
                }
            }

            val oldTop: Node<E> = top.value ?: return null

            if (top.compareAndSet(oldTop, oldTop.next)) {
                return oldTop.x
            }
        }

    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private class Type<E>(val x: E?, val type: TypeEnum)

private enum class TypeEnum {
    DONE, VERT, EMPTY
}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT