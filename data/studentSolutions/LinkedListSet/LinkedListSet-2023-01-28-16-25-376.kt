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

    private fun abstract (element1: E) : Node<E> {
        var f : Node<E>  = first
        var nval : Node<E>  = f.next
        while(nval != last && element1 > nval.elem){
            f = f.next
            nval = f.next
        }
        return f
    }

    fun add(element1: E): Boolean {
        while(true) {
            val f : Node<E>  = abstract(element1)
            val nval : Node<E>  = f.next
            if(nval.elem == element1) return false
            if (f.casNext(nval, Node(element1, nval))) return true
        }
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element1: E): Boolean {
        while(true) {
            val f : Node<E>  = abstract(element1)
            val nval : Node<E>  = f.next
            if(nval.elem != element1) return false
            if (f.casNext(nval, nval.next)) return true
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element1: E): Boolean {
        val f : Node<E>  = abstract(element1)
        return f.next!!.elem == element1
    }
}

private class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {

    private val _element = element // `null` for the first and the last nodes
    val elem get() = _element!!

    private val _next = atomic(next)
    val next get() = _next.value!!
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}