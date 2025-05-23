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
            var prevNode = first
            var nextNode = prevNode.next
            if (nextNode === last) {
                val newNode = Node<E>(prevNode, element, nextNode)
                if (prevNode.casNext(nextNode, newNode)) {
                    nextNode.casPrev(prevNode, newNode)
                    return true
                } else {
                    continue
                }
            } else {
                while (true) {
                    if (prevNode === first && nextNode!!.element > element) {
                        break
                    }
                    if (nextNode === last) break
                    if ((prevNode !== first && prevNode.element == element) || nextNode!!.element == element) return false
                    if ((prevNode !== first && prevNode.element < element) && nextNode.element > element) break


                    prevNode = nextNode
                    nextNode = nextNode.next
                }

                val newNode = Node<E>(prevNode, element, nextNode)
                if (prevNode.casNext(nextNode, newNode)) {
                    nextNode!!.casPrev(prevNode, newNode)
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
        return false
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var node = first.next!!
        while (node !== last) {
            if (node.element == element) return true
            node = node.next!!
        }
        return false
    }
}

public class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
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

    val removed = atomic(false)

    fun tryHelpRemove() {
        if (removed.value) {
            prev!!.casNext(this, next)
            next!!.casPrev(this, prev)
        }
    }
}