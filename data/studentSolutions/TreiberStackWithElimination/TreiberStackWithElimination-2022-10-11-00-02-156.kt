package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

import kotlin.random.Random
import java.util.concurrent.locks.ReentrantLock

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val fake = atomic<E?>(null)
    private val fake2 = atomic<E?>(null)
    var lock: ReentrantLock = ReentrantLock()
    
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        lock.lock()
        var value = x
        var index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        lock.unlock()
        if (index == 0) {
            if (fake.compareAndSet(null, value)) {
                while(!fake.compareAndSet(value, null)) {}
            }
        } else {
            if (fake2.compareAndSet(null, value)) {
                while(!fake2.compareAndSet(value, null)) {}
            }
        }

        do {
            var old_top = top.value
        } while (!top.compareAndSet(old_top, Node<E>(x, old_top)))
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        //if (index >= ELIMINATION_ARRAY_SIZE || index < 0)throw java.lang.IllegalStateException("Lol")
        //var value = eliminationArray.get(index).getAndSet(null)
        //if (value != null) {
        //    return value
        //}

        while (true) {
            var old_top = top.value
            if (old_top == null) {
                return null
            }
            if (top.compareAndSet(old_top, old_top.next)) {
                return old_top.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)
private class Done()

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT