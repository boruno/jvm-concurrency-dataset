//package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicMarkableReference

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = null, next = null)
    private val last = Node<E>(element = null, next = null)

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
            val window = find(Node(element, null));
            val pred: Node<E>? = window.cur
            val curr: Node<E>? = window.next
            if (curr!!.element == element) {
                return false
            } else {
                val node: Node<E> = Node(element, null)
                if (pred!!.next.compareAndSet(curr, node)) {
                    return true;
                }
            }
        }
    }

    //    cur.element < element <= next.element
    private fun find(elementNode: Node<E>): Window<E> {
        var cur = head.value
        var next = cur.next.value
        while (next != null && cur.element > elementNode.element) {
            cur = next
            next = cur.next.value
        }
        return Window(cur, next)

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
            val window = find(Node(element, null))
            val prev = window.cur!!
            val rem = window.next!!
            val next = rem.next.value
            if (rem.element != element) {
                return false
            } else {
                if (prev.casNext(rem, next)){
                    return  true
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val window = find(Node(element, null))
        return window.next!!.element == element
    }
}

private class Window<E : Comparable<E>>(val cur: Node<E>?, val next: Node<E>?)

private class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _next = atomic(next)
    val next get() = _next
    fun setNext(value: Node<E>) {
        _next.value = value
    }

    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}