package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = Int.MIN_VALUE as E, next = null)
    private val last = Node<E>(prev = first, element = Int.MAX_VALUE as E, next = null)
    init {
        first.setNext(last)
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
        var curElem = head.value
        while (curElem.next != null) {
            val curNext = curElem.next
            if (curElem.element == element) {
                return false
            }
            if (element > curElem.element && element < curElem.next!!.element) {
                val newNode = Node(curElem, element, curElem.next)
                if (curElem.casNext(curNext, newNode)) {
                    return true
                } else {
                    curElem = head.value
                    continue
                }
            }
            curElem = curNext!!
        }
        return false
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        var curElem = head.value
        while (curElem.next != null) {
            if (curElem.next == element) {
                if (curElem is Removed) {
                    continue
                }
                var forRemove = Removed(curElem, curElem.next!!.element, curElem.next!!.next)
                curElem.setNext(forRemove)
                if (curElem.casNext(forRemove, forRemove.next)) {
                    return true
                } else {
                    curElem = head.value
                    continue
                }
            }
            curElem = curElem.next!!
        }
        return false
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var curElem = head.value
        while (curElem.next != null) {
            if (curElem.element == element) {
                return true
            }
            curElem = curElem.next!!
        }
        return false
    }
}

private open class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
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
}

private class Removed<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) : Node<E>(prev, element, next) {

}