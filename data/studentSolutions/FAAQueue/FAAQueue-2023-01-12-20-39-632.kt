//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
	private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
	private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
	private val enqIdx = atomic(0L)
	private val deqIdx = atomic(0L)

	object BROKE : Any()

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
			val curTail = tail
			val i = enqIdx.getAndIncrement()
			val s = findSegment(start = curTail.value, id = i / SEGMENT_SIZE)
			moveTailForward(s)
			if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
				return
			}
		}
	}

	private fun moveTailForward(s: Segment) {
		while (true) {
			val curTail = tail.value
			if (s.id > curTail.id) {
				if (tail.compareAndSet(curTail, s)) {
					break
				}
			} else {
				break
			}
		}
	}

	private fun findSegment(start: Segment, id: Long): Segment {
		return if (id > start.id) {
			var segment = start
			while (id > segment.id) {
				segment = segment.next ?: Segment(segment.id + 1)
			}
			segment
		} else {
			start
		}
	}

	/**
	 * Retrieves the first element from the queue and returns it;
	 * returns `null` if the queue is empty.
	 */
	fun dequeue(): E? {
		while (true) {
			if (deqIdx.value <= enqIdx.value) {
				return null
			}
			val curHead = head
			val i = deqIdx.getAndIncrement()
			val s = findSegment(start = curHead.value, id = i / SEGMENT_SIZE)
			moveHeadForward(s)
			if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, BROKE)) {
				continue
			}
			return s.elements[(i % SEGMENT_SIZE).toInt()].value as E
		}
	}

	private fun moveHeadForward(s: Segment) {
		while (true) {
			val curHead = head.value
			if (s.id > curHead.id) {
				if (head.compareAndSet(curHead, s)) {
					break
				}
			} else {
				break
			}
		}
	}

	/**
	 * Returns `true` if this queue is empty, or `false` otherwise.
	 */
	val isEmpty: Boolean
		get() {
			return deqIdx.value <= enqIdx.value
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

