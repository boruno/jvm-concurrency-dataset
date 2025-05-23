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
        while (true) {
            val (first, second) = findWindow(element)
            when {
                !(second.element != element || second == last) -> {
                    return false
                }

                else -> {
                    val node = Node(prev = first, element = element, next = second)
                    if (first.casNext(second, node)) {
                        return true
                    }
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
            val (first, second) = findWindow(element)
            when {
                !(second.element <= element && second != last) -> {
                    return false
                }

                else -> {
                    if (second.casNext(
                            second.next, Wrapper(
                                prev = second.prev,
                                element = second.element,
                                next = second.next
                            )
                        )
                    ) {
                        if (first.casNext(second, second.next)) {
                            return true
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val (_, second) = findWindow(element)
        if (second == last) {
            return false
        }
        return second.element == element
    }

    private fun findWindow(element: E): Pair<Node<E>, Node<E>> {
        var cur = head.value
        var next = cur.next!!
        while (true) {
            val res = cur.next!!.next?.let {
                if (it.isRemoved) {
                    cur.casNext(cur.next!!, it)
                    true
                } else {
                    false
                }
            }
            if (res != null && res) return findWindow(element)
            if (next == last || next.element >= element) {
                return Pair(cur, next)
            } else {
                cur = next
            }
            next = cur.next!!
        }
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


    val isRemoved get() = this is Wrapper
}

private class Wrapper<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) : Node<E>(prev, element, next)