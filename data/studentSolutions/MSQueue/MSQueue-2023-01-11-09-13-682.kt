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
    while (true) {
      val node = Node(x)
      with(tail.value) {
        if (next.compareAndSet(null, node)) {
          tail.compareAndSet(this, node)
          return
        } else {
          tail.compareAndSet(this, next.value!!)
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