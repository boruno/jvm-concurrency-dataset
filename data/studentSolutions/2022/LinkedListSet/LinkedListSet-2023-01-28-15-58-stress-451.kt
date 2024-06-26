package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = null, next = null)
    private val last = Node<E>(element = null, next = null)
    init {
        first.setNext(last)
    }

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */

    private fun abstract (element: E) : Node<E> {
        var f = first
        var nval = f.next
        while(nval != last && element > nval.element){
            f = f.next
            nval = f.next
        }
        return f
    }

    fun add(element: E): Boolean {
        while(true) {
            var f = first
            var nval = f.next
            while(nval != last && element > nval.element){
                f = f.next
                nval = f.next
            }
            if(nval.element == element) return false
            if (f.casNext(nval, Node(element, nval))) return true
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
        while(true) {
            var f = first
            var nval = f.next
            while(nval != last && element > nval.element){
                f = f.next
                nval = f.next
            }
            if(nval.element != element) return false
            if (f.casNext(nval, nval.next)) return true
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var f = first
        var nval = f.next
        while(nval != last && element > nval.element){
            f = f.next
            nval = f.next
        }
        return f.next.element == element
    }
}

private class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _next = atomic(next)
    val next get() = _next.value!!
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}