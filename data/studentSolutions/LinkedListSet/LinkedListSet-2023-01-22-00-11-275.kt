//package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Head<E>()
    private val last = Tail<E>()
    init {
        first.setNext(last)
        last.setPrev(first)
    }

    private val head = atomic<Node<E>>(first)

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        if (contains(element)) {
            return false
        }

        val (parent, found) = findEqOrHigher(element)
        val insertion = Node(parent, element, found)
        found.casPrev(parent, insertion)

        if (!parent.casNext(found, insertion)) {
            return add(element)
        }

        return true
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        val (_, found) = findEqOrHigher(element)

        if (found is Tail || found.element != element) {
            return false
        }

        if (!found.casNext(found, found.next) || !found.alive) {
            return remove(element)
        }

        return true
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val (_, node) = findEqOrHigher(element)

        if (node is Tail) {
            return false
        }

        return node.element == element
    }

    private fun findEqOrHigher(element: E): Pair<Node<E>, Node<E>> {
        var res: Node<E> = head.value

        while (res !is Tail && (res is Head || res.element < element)) {
            res = res.next!!
        }

        return Pair(res.prev!!, res)
    }
}

private open class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?, alive: Boolean = true) {
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

    private val _alive = atomic(alive)
    val alive get() = _alive.value
    fun setAlive(alive: Boolean) {
        _alive.value = alive
    }
    fun casAlive(expected: Boolean, update: Boolean) {
        _alive.compareAndSet(expected, update)
    }
}

private class Head<E : Comparable<E>> : Node<E>(null, null, null)
private class Tail<E : Comparable<E>> : Node<E>(null, null, null)
