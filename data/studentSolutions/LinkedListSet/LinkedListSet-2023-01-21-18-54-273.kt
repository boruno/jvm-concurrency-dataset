//package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference

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
        while(true) {
            var elementLeft = head.value
            var elementRight = head.value.next!!
            if (elementRight != last)
            while (elementRight != last) {
                if (elementLeft == first && elementRight.element > element )
                    break
                if (elementLeft != first && elementLeft.element == element)
                    return false
                if (elementRight.element == element)
                    return false
                if (elementLeft != first && (elementLeft.element < element && elementRight.element > element))
                    break
                elementLeft = elementRight
                elementRight = elementRight.next!!
            }
            val newNode = Node(elementLeft, element, elementRight)
            if (!elementLeft.casNext(elementRight, newNode))
                continue
            elementRight.casPrev(elementLeft, newNode)
            return true
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
        var we_are_removing = false
        while(true) {
            var found = false
            var currentElement = head.value
            while (currentElement.next != last) {
                currentElement = currentElement.next!!
                if (currentElement == last)
                    break
                if (currentElement.element == element) {
                    found = true
                    break
                }
            }
            if(!found)
                return we_are_removing
            if(currentElement.removed.compareAndSet(false, true))
            {
                we_are_removing = true
            }
            val nextElement = currentElement.next
            val prevElement = currentElement.prev
            if (nextElement!!.removed.get())
            {
                currentElement.casNext(nextElement, nextElement.next)
                nextElement.next!!.casPrev(nextElement, currentElement)
                continue
            }
            if (prevElement!!.removed.get())
            {
                currentElement.casPrev(prevElement, prevElement.prev)
                prevElement.prev!!.casNext(prevElement, currentElement)
                continue
            }
            if(!currentElement.prev!!.casNext(currentElement, currentElement.next))
            {
                continue
            }
            if(!currentElement.next!!.casPrev(currentElement, currentElement.prev))
            {
                if (currentElement.next!!.prev!!.removed.get())
                {
                    currentElement.next!!.casPrev(currentElement.next!!.prev, currentElement)
                    continue
                }
                currentElement.prev!!.casNext(currentElement.next, currentElement)
                continue
            }
            return we_are_removing
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var currentElement = head.value.next!!
        while (currentElement != last)
        {
            if (currentElement.element == element)
                return true
            currentElement = currentElement.next!!
        }
        return false
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    val removed: AtomicReference<Boolean> = AtomicReference<Boolean>(false)
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
}