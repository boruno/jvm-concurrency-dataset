package mpp.linkedlistset
import java.util.concurrent.atomic.AtomicBoolean

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>( null, null, null)
    private val last = Node<E>(null, null, null)

    constructor() {
        first.setNext(last)
        last.setPrev(first)
    }

    private val head = atomic(first)

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true) {
            val tmp = getPlace(element)!!
            val current = tmp.first
            val next = tmp.second
            if ((next != last) && (next.element == element) && current.removed) {
                return false
            }
            val node = Node(current, element, next)
            if (current.casNext(next, node) && next.casPrev(current, node)) {
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
            val tmp = getPlace(element)
            val current = tmp.first
            val next = tmp.second
            if (next == last || next.element != element || current.removed) {
                return false
            }
            if (next.casRemoved(false, true)) {
                current.casNext(next, next.next)
                next.next!!.casPrev(next, current)
                return true
            }
        }
    }

    private fun getPlace(element: E): Pair<Node<E>, Node<E>> {
        while (true) {
            var current = head.value
            var next = head.value.next!!

            while (next != last && next.element < element) {
                val newCur = current.next
                val newNext = next.next

                if (newCur == null || newCur.next != newNext || newCur.removed) {
                    break
                }
                if (newNext != null && !newNext.removed) {
                    if (newCur.removed) {
                        break
                    }
                    current = newCur
                    next = newCur.next!!
                } else if (newCur.casNext(newNext, newNext!!.next)) {
                    next = newNext
                } else {
                    break
                }
            }

            if (next == last || next.element >= element) {
                return Pair(current, next)
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val tmp = getPlace(element)!!
        val next = tmp.second
        return next.element == element
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    val _removed = atomic(false)

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

    val removed get() = _removed.value
    fun setRemoved(value: Boolean) {
        _removed.value = value
    }
    fun casRemoved(expected: Boolean, update: Boolean) =
        _removed.compareAndSet(expected, update)
}