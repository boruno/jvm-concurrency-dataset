//package mpp.linkedlistset

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
        var cur = head.value
        var next = cur.next!!
        while (true) {
            if (next == last) {
                // insert no matter what
                val newNode = Node(prev = cur, next = next, element = element)
                if (cur.casNext(next, newNode)) {
                    next.casPrev(cur, newNode) //todo ???
                    return true
                } else {
                    cur = head.value
                    next = cur.next!!
                    continue
                }
            }
            if (cur == first) {
                if (element < next.element) {
                    val newNode = Node(prev = cur, next = next, element = element)
                    if (cur.casNext(next, newNode)) {
                        next.casPrev(cur, newNode) //todo ???
                        return true
                    } else {
                        continue
                    }
                }
            } else {
                if (cur.element < element && element < next.element) {
                    val newNode = Node(prev = cur, next = next, element = element)
                    if (cur.casNext(next, newNode)) {
                        next.casPrev(cur, newNode) //todo ???
                        return true
                    } else {
                        continue
                    }
                }
            }
            if (next.element == element) return false
            cur = next
            next = cur.next ?: throw Exception("Impossible on add")
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
        var cur = head.value
        var next = cur.next!!
        while (true) {
            if (next == last) return false

            if (next.element == element) {
                next.removed.compareAndSet(expect = false, update = true)
                next.prev!!.casNext(next, next.next)
                next.next!!.casPrev(next, cur)
                return true
            }
            cur = next
            next = cur.next ?: break
        }
        throw Exception("Impossible on add")
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var cur = head.value
        var next = cur.next!!
        while (true) {
            if (cur != first && cur != last && cur.element == element) return true
            cur = next
            next = cur.next ?: break
        }
        return false
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val removed = atomic(false)
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