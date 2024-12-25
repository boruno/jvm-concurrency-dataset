//package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = MINUS_INF, next = null)
    private val last = Node<E>(prev = first, element = PLUS_INF, next = null)
    init {
        first.setNext(last)
    }

    fun getFirst(): Node<E> = first

    fun getLast(): Node<E> = last

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true) {
            val segment = PairSegment.find(element, this)!!
            if (segment.next!!.element == element) return false
            else {
                val node = Node(segment.cur, element, segment.next);
                if (segment.cur!!.casNext(segment.next, node))
                    return true
            }
        }
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        while (true) {
            val segment = PairSegment.find(element, this)
            if (segment!!.next!!.element != element) {
                return false
            } else {
                val nextNext = segment.next!!.next!!
                if (!nextNext.alive)
                    continue
                if (segment.next!!.next!!.casAlive(expected = false, update = true)) {
                    segment.cur!!.casNext(segment.next, nextNext);
                    return true;
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        return PairSegment.find(element, this)!!.cur!!.element == element
    }
}

private class PairSegment<E: Comparable<E>>(var cur: Node<E>?, var next: Node<E>?) {
    companion object {
        @JvmStatic
        fun <E : Comparable<E>> find(element: E, set: LinkedListSet<E>): PairSegment<E>? {
            outer@ while (true) {
                val segment = PairSegment(set.getFirst(), set.getLast())
                while ((segment.next!!.element as E) < element || !segment.next!!.alive) {
                    if(!segment.cur!!.alive) continue@outer
                    if(!segment.next!!.alive) {
                        val nextNext = segment.next!!.next!!.next
                        if(!segment.cur!!.casNext(segment.next, nextNext)) {
                            segment.next = segment.cur!!.next
                            continue
                        }
                        segment.next = nextNext
                    } else {
                        segment.cur = segment.next
                        segment.next = segment.cur!!.next
                    }

                }
                return segment
            }
        }
    }
}

class Node<E : Comparable<E>>(prev: Node<E>?, element: Any?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _prev = atomic(prev)
    val prev get() = _prev.value
    fun setPrev(value: Node<E>?) {
        _prev.value = value
    }
    fun casPrev(expected: Node<E>?, update: Node<E>?) =
        _prev.compareAndSet(expected, update)

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)

    private val _alive = atomic(true)
    val alive get() = _alive.value
    fun casAlive(expected: Boolean, update: Boolean) =
        _alive.compareAndSet(expected, update)
}

const val MINUS_INF = Long.MIN_VALUE
const val PLUS_INF = Long.MAX_VALUE