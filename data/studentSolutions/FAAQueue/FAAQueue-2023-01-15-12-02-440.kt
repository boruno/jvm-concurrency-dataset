//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
  private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
  private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
  private val DONE = Any()

  init {
    val firstNode = Segment()
    firstNode.enqIdx.value = 0
    head = atomic(firstNode)
    tail = atomic(firstNode)
  }

  /**
   * Adds the specified element [x] to the queue.
   */
  fun enqueue(element: E) {
    while (true) {
      val currentTail = tail.value
      val idx = currentTail.enqIdx.value
      if (idx >= SEGMENT_SIZE) {
        currentTail.next.value?.let {
          tail.compareAndSet(currentTail, it)
        } ?: run {
          if (currentTail.next.compareAndSet(null, Segment(element))) {
            tail.compareAndSet(currentTail, currentTail.next.value!!)
            return
          }
        }
      } else {
        if (currentTail.cas(idx.toInt(), null, element)) {
          currentTail.enqIdx.compareAndSet(idx, idx + 1)
          return
        } else {
          currentTail.enqIdx.compareAndSet(idx, idx + 1)
        }
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
      val idx = currentHead.deqIdx.value
      if (idx >= SEGMENT_SIZE) {
        val next = currentHead.next.value ?: return null
        head.compareAndSet(currentHead, next)
      } else {
        val res = currentHead.get(idx.toInt())
        if (currentHead.cas(idx.toInt(), res, DONE)){
          currentHead.deqIdx.compareAndSet(idx, idx + 1)
          return res as E
        } else {
          currentHead.deqIdx.compareAndSet(idx, idx + 1)
        }
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
  val next: AtomicRef<Segment?> = atomic(null)
  val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
  val enqIdx = atomic(1L)
  val deqIdx = atomic(0L)

  constructor(x: Any?) : this() {
    put(0, x)
  }

  fun get(i: Int) = elements[i].value
  fun getAndSet(i: Int, v: Any): Any? = get(i).also { put(i, v) }

  fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
  fun put(i: Int, value: Any?) {
    elements[i].value = value
  }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
