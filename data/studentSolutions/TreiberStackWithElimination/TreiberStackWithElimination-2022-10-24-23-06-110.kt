package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val retryTimes = 10
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
      val idx = (0..eliminationArray.size).random()
      val elem = eliminationArray.get(idx).value
      if (eliminationArray.get(idx).compareAndSet(null, x)) {
        for (i in 0..retryTimes) {
          if (eliminationArray.get(idx).value == null) {
            return
          }
        }
        eliminationArray.get(idx).compareAndSet(x, null)
      }
		  while (true) {
			  val T = top.value
			  val newHead = Node(x, T)
			  if (top.compareAndSet(T, newHead)) {
				  return
			  }
		  }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
		  while (true) {
			  val T = top.value
			  if (T == null) return null
			  val next = T.next
			  if (top.compareAndSet(T, next)) {
				  return T.x
			  }
		  }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
