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
            val w = findWindow(element)
            if (w.next == element) {
                val nextCheck = w.next.next!!.prev
                if (nextCheck != w.next) {
                    if (!w.next.next!!.casPrev(nextCheck, w.next))
                        continue
                    return true
                }
                return false
            }
            val newNode = Node(w.cur, element, w.next)
            if (!w.cur.casNext(w.next, newNode))
                continue
            if (!w.next.casPrev(w.cur, newNode))
                continue
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
            val w = findWindow(element)
            if (w.next != element) return false
            val updNode = RemovedNode(w.next)
            val updNext = w.next.next
            if (!w.cur.casNext(w.next, updNode) || w.cur is RemovedNode)
                continue
            if (!updNext!!.casPrev(updNode, w.cur) || updNext is RemovedNode)
                continue
            w.cur.casNext(updNode, updNext)
            updNext!!.casPrev(updNode, w.cur)
            return true
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val w = findWindow(element)
        return w.next.element == element
    }

    private fun findWindow(element: E): Window<E> {
        outer@ while (true) {
            var cur = head.value
            var next = cur.next
            while (next!!.next != null && element > next.element || next is RemovedNode) {
                if (next is RemovedNode) {
                    val updNext = next.next
                    if (!cur.casNext(next, updNext))
                        continue@outer
                    if (!updNext!!.casPrev(next, cur))
                        continue@outer
                    next = updNext
                } else {
                    cur = next
                    next = cur.next
                }
            }
            return Window(cur, next)
        }
    }
}

private class Window<E : Comparable<E>> (val cur: Node<E>, val next: Node<E>) {}

private open class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    protected val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    protected val _prev = atomic(prev)
    val prev get() = _prev.value
    fun setPrev(value: Node<E>?) {
        _prev.value = value
    }
    fun casPrev(expected: Node<E>?, update: Node<E>?) =
        _prev.compareAndSet(expected, update)

    protected val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}

private class RemovedNode<E: Comparable<E>> : Node<E> {
    constructor(prev: Node<E>?, element: E?, next: Node<E>?) : super(prev, element, next)
    constructor(node: Node<E>) : super(prev = node.prev, element = node.element, next = node.next)
}
