package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
  private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
  private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
  private val enqIdx = atomic(0L)
  private val deqIdx = atomic(0L)
  private val DONE = Object()

  init {
    val firstNode = Segment()
    head = atomic(firstNode)
    tail = atomic(firstNode)
  }

  /**
   * Adds the specified element [x] to the queue.
   */
  fun enqueue(element: E) {
    while (true) {
      val idx = enqIdx.getAndIncrement().toInt()
      if (head.value.elements[idx].compareAndSet(null, element)) {
        return
      }
    }
  }

  /**
   * Retrieves the first element from the queue and returns it;
   * returns `null` if the queue is empty.
   */
  fun dequeue(): E? {
    while (true) {
      if (deqIdx.value >= enqIdx.value) {
        return null
      }
      val idx = deqIdx.getAndIncrement().toInt()
      if (head.value.elements[idx].compareAndSet(null, DONE)) {
        continue
      }
      return head.value.elements[idx].value as? E ?: continue
    }

  }

  /**
   * Returns `true` if this queue is empty, or `false` otherwise.
   */
  val isEmpty: Boolean
    get() {
      return deqIdx.value >= enqIdx.value
    }
}

private class Segment {
  var next: Segment? = null
  val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

  private fun get(i: Int) = elements[i].value
  private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
  private fun put(i: Int, value: Any?) {
    elements[i].value = value
  }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

