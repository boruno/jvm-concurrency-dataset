package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class MSQueue<E> {
  private val head: AtomicRef<Node<E>>
  private val tail: AtomicRef<Node<E>>

  init {
    val dummy = Node<E>(null)
    head = atomic(dummy)
    tail = atomic(dummy)
  }

  /**
   * Adds the specified element [x] to the queue.
   */
  fun enqueue(x: E) {
    val node = Node(x)
    while (true) {
      with(tail) {
        if (value.next.compareAndSet(null, node)) {
          compareAndSet(value, node)
          return
        } else {
          compareAndSet(value, value.next.value!!)
        }
      }
    }
  }

  /**
   * Retrieves the first element from the queue
   * and returns it; returns `null` if the queue
   * is empty.
   */
  fun dequeue(): E? {
    while (true) {
      val currentHead = head
      currentHead.value.next.value?.let {
        if (currentHead.compareAndSet(currentHead.value, it)) {
          return it.x
        }
      } ?: return null
    }
  }

  fun isEmpty(): Boolean {
    return head.value.next.value == null
  }
}

private class Node<E>(val x: E?) {
  val next = atomic<Node<E>?>(null)
}