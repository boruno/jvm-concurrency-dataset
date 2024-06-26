package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Head<E>()
    private val last = Tail<E>()
    init {
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
        if (contains(element)) {
            return false
        }

        val parent = findLower(element)
        val next = parent.next
        val insertion = Node(parent, element, next)

        if (!parent.casNext(next, insertion)) {
            add(element)
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
        if (!contains(element)) {
            return false
        }

        val parent = findLower(element)
        if (parent is Tail) {
            return false
        }

        val me = parent.next

        if (!parent.casNext(me, me?.next ?: Tail()) || !parent.alive) {
            return remove(element)
        }

        return true
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val node = findLower(element)
        val next = node.next

        if (node is Tail || next is Tail) {
            return false
        }
        if (next!!.element == element) {
            return true
        }

        return false
    }

    private fun findLower(element: E): Node<E> {
        var res: Node<E> = first

        while (res.next !is Tail && (res is Head || res.element < element)) {
            res = res.next!!
        }

        return res
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
