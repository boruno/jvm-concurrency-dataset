package mpp.stack

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.ReentrantLock

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)
    private val mutex = ReentrantLock()

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
//        while (true) {
//            val curTop = top
//            val newTop = Node(x, curTop.value)
//
//            if (curTop.compareAndSet(curTop.value, newTop)) {
//                return
//            }
//        }
        mutex.lock()

        try {
            val curTop = top
            val newTop = Node(x, curTop.value)
            top.value = newTop
        } catch(_: Exception) {
            mutex.unlock()
        }

    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
//        while (true) {
//            val curTop = top
//
//            if (curTop.value == null) {
//                return null
//            }
//
//            val newTop = curTop.value?.next
//
//            if (curTop.compareAndSet(curTop.value, newTop)) {
//                return curTop.value?.x
//            }
//        }

        mutex.lock()

        try {
            val curTop = top

            if (curTop.value == null) {
                return null
            }

            val newTop = curTop.value?.next
            top.value = newTop

            return curTop.value?.x
        } catch(_: Exception) {
            mutex.unlock()
        }

        return null
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT