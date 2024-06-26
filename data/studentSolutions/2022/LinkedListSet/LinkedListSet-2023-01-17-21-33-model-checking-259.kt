package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)

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
        while (true) {
            val (prev, curr) = find(element)
            if (curr != last && curr.element == element) {
                if (!curr.removed.value) {
                    return false
                } else {
                    continue
                }
            } else {
                val newNode = Node(null, element, curr)
                if (prev.casNext(curr, newNode)) {
                    return true
                }
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
            val (prev, curr) = find(element)
            if (curr.element == element) {
                if (curr.removed.compareAndSet(false, true)) {
                    prev.casNext(curr, curr.next!!)
                    curr.next!!.casPrev(curr, prev)
                    if (!prev.removed.value && !curr.next!!.removed.value) {
                        return true
                    }
                }
            } else {
                return false
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val (prev, cur) = find(element)
        return cur !== last && cur.element == element
    }

    private fun find(element: E): Pair<Node<E>, Node<E>> {
        var prev = head.value
        var current: Node<E> = head.value.next!!
        while (current !== last) {
            if (current.removed.value) {
                prev.casNext(current, current.next)
                current.next!!.casPrev(current, prev)
                current = prev.next!!
                continue
            } else {
                if (current.element >= element) {
                    break
                }

                prev = prev.next!!
                current = current.next!!
            }
        }

        return Pair(prev, current)
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!


    val removed = atomic(false)

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