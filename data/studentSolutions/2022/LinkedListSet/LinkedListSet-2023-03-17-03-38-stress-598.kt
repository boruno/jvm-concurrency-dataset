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
            val nodes = findWindow(element)
            if (nodes[1] != last && nodes[1].element == element) {
                return false
            } else {
                val newNode = Node(nodes[0], element, nodes[1])
                if (nodes[0].casNext(nodes[1], newNode)) {
                    return true
                } else {
                    continue
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
            val nodes = findWindow(element)
            if (nodes[1] == last || nodes[1].element != element) {
                return false
            } else {
                if (nodes[1].casFlag(expected = false, update = true)) {
                    nodes[0].casNext(nodes[1], nodes[1].next!!)
                    return true
                } else {
                    continue
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val node = findWindow(element)[1]
        return node.element == element
    }

    private fun findWindow(element: E): List<Node<E>> {
        var curHead = head.value
        var headNext = curHead.next!!
        while (headNext != last && headNext.element < element) {
            when (headNext.flag) {
                true -> {
                    curHead.casNext(headNext, headNext.next!!)
                    headNext = curHead.next!!
                }

                else -> {
                    curHead = headNext
                    headNext = headNext.next!!
                }
            }
        }

        return ArrayList<Node<E>>(listOf(curHead, headNext))
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
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

    private val _flag = atomic(false)
    val flag get() = _flag.value
    fun casFlag(expected: Boolean, update: Boolean) =
        _flag.compareAndSet(expected, update)
}