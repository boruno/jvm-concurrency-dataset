package mpp.faaqueue

import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
class FAAQueue<E> {
  private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
  private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
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
      val idx = tail.value.enqIdx.getAndIncrement().toInt()
      val currentTail = tail.value
      if (idx >= SEGMENT_SIZE) {
        val newSegment = Segment()
        newSegment.put(0, element)
        currentTail.next = newSegment
        if (tail.compareAndSet(currentTail, newSegment)) {
          return
        }
      } else if (head.value.cas(idx, null, element)) {
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
      val currentHead = head.value
      if (currentHead.deqIdx.value >= SEGMENT_SIZE) {
        val next = currentHead.next ?: return null
        head.compareAndSet(currentHead, next)
      } else {
        val idx = currentHead.deqIdx.getAndIncrement()
        if (idx >= SEGMENT_SIZE) continue
        val res = currentHead.getAndSet(idx.toInt(), DONE) ?: return null
        return res as E?
      }
    }

  }

  /**
   * Returns `true` if this queue is empty, or `false` otherwise.
   */
  val isEmpty: Boolean
    get() {
      return head.value.get(head.value.enqIdx.value.toInt()) == DONE
    }
}

private class Segment() {
  var next: Segment? = null
  val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
  val enqIdx = atomic(1L)
  val deqIdx = atomic(0L)
  constructor(x: Any?): this() {
    put(0, x)
  }
  fun get(i: Int) = elements[i].value
  fun getAndSet(i: Int, v: Any) = elements[i].value.also { elements[i].value = v }
  fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
  fun put(i: Int, value: Any?) {
    elements[i].value = value
  }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

