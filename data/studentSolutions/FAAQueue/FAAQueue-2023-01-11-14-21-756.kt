//package mpp.faaqueue

import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
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
      if (idx >= SEGMENT_SIZE) {
        val newSegment = Segment()
        newSegment.put(0, element)
        val currentTail = tail.value
        if (tail.compareAndSet(currentTail, newSegment)) {
          currentTail.next = newSegment
          enqIdx.value = 1
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
      val idx = deqIdx.getAndIncrement().toInt()
      if (idx >= SEGMENT_SIZE) {
        val next = head.value.next ?: return null
        deqIdx.value = 1
        if (!head.compareAndSet(head.value, next)) continue
        return head.value.get(0) as E
      } else {
        val res = head.value.get(idx)
        if (res != null) {
          if (head.value.cas(idx, res, DONE)) {
            return res as E
          }
        }
      }
    }

  }

  /**
   * Returns `true` if this queue is empty, or `false` otherwise.
   */
  val isEmpty: Boolean
    get() {
      return head.value.get(enqIdx.value.toInt()) == DONE
    }
}

private class Segment {
  var next: Segment? = null
  val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

  fun get(i: Int) = elements[i].value
  fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
  fun put(i: Int, value: Any?) {
    elements[i].value = value
  }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

