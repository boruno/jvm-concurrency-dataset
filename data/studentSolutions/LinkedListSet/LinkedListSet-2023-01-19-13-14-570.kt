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
       mainLoop@ while(true) {
            if (contains(element))
                return false
            val node = head.value
            val insert = Node(element = element, next = null, prev = null)
            while(node != last){
                if(node == first){
                    if(node.next == last){
                        insert.setNext(last)
                        insert.setPrev(first)
                        if(node.casNext(node.next,insert))
                            break@mainLoop
                    }
                    if(element < node.next!!.element){
                        insert.setNext(node.next)
                        insert.setPrev(first)
                        if(node.casNext(node.next,insert))
                            break@mainLoop
                    }
                }
                if(node.element < element && node.next!!.element > element){
                    insert.setNext(node.next)
                    insert.setPrev(node)
                    if(node.casNext(node.next,insert))
                        break@mainLoop
                }
            }
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
        while(true) {
            if (!contains(element))
                return false
            var node = head.value
            while(node != last)
            {
                if(node == this.first)
                    continue
                if(node.element == element)
                    break
                node = first.next!!
            }
            if(!node.removed.compareAndSet(false,true))
                continue
            val prev = node.prev
            if(prev!!.removed.value)
                continue
            if(prev.casNext(node, node.next))
                return true
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var first = head.value
        while(first != last)
        {
            if(first == this.first) {
                first = first.next!!
                continue
            }
            if(first.element == element)
                return true
            first = first.next!!
        }
        return false
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