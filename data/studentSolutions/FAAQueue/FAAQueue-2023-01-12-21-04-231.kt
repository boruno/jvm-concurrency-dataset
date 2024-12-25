//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
	private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
	private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
	private val enqIdx = atomic(0L)
	private val deqIdx = atomic(0L)

	init {
		val firstNode = Segment(0)
		head = atomic(firstNode)
		tail = atomic(firstNode)
	}

	/**
	 * Adds the specified element [x] to the queue.
	 */
	fun enqueue(element: E) {
		while (true) {
			val curTail = tail.value
			val i = enqIdx.getAndIncrement()
			val s = findSegment(start = curTail, id = i / SEGMENT_SIZE)
			moveTailForward(s)
			if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
				return
			}
		}
	}

	private fun moveTailForward(s: Segment) {
		while (true) {
			val curTail = tail.value
			if (curTail.id < s.id) {
				if (tail.compareAndSet(curTail, s)) {
					return
				}
			} else {
				return
			}
		}
	}

	private fun findSegment(start: Segment, id: Long): Segment {
		return if (id > start.id) {
			var s = start
			while (id > s.id) {
				s = s.next ?: Segment(s.id + 1)
			}
			s
		} else {
			start
		}
	}

	object BROKEN

	/**
	 * Retrieves the first element from the queue and returns it;
	 * returns `null` if the queue is empty.
	 */
	fun dequeue(): E? {
		while (true) {
			if (deqIdx.value >= enqIdx.value) {
				return null
			}
			val curHead = head.value
			val i = deqIdx.getAndIncrement()
			val s = findSegment(start = curHead, id = i / SEGMENT_SIZE)
			moveHeadForward(s)
			if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, BROKEN)) {
				continue
			}
			return s.elements[(i % SEGMENT_SIZE).toInt()].value as E
		}
	}

	private fun moveHeadForward(s: Segment) {
		while (true) {
			val curHead = head.value
			if (curHead.id < s.id) {
				if (head.compareAndSet(curHead, s)) {
					return
				}
			} else {
				return
			}
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

private class Segment(val id: Long) {
	var next: Segment? = null
	val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

	private fun get(i: Int) = elements[i].value
	private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
	private fun put(i: Int, value: Any?) {
		elements[i].value = value
	}
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

