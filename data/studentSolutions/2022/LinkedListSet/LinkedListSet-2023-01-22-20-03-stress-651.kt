package mpp.linkedlistset

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic


/*
class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)
    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    private fun isRemoved(node: Node<E>?): Boolean {
        return node!!.next is Removed
    }

    inner class Block(var cur: Node<E>? = null, var next: Node<E>? = null)

    private fun findBlock(element: E): Block {
        while (true) {
            val curHead = head.value
            val block = Block(curHead, curHead.next)
            while (!isRemoved(block.cur) && (block.next!!.element < element || isRemoved(block.next))) {
                if (isRemoved(block.next)) {
                    val nextNext = block.next!!.next!!.next
                    block.next = nextNext
                } else {
                    block.cur = block.next
                    block.next = block.cur!!.next
                }
            }
            if (!isRemoved(block.cur)) return block
        }
    }

    *//**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     *//*
    fun add(element: E): Boolean {
        while (true) {
            val block = findBlock(element)
            if (block.next!!.element == element) {
                return false
            }
            if (w.cur!!.next.compareAndSet(w.next, Node(element, w.next))) {
                return true
            }
        }
    }

    *//**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     *//*
    fun remove(element: E): Boolean {
        TODO("implement me")
    }

    *//**
     * Returns `true` if this set contains
     * the specified element.
     *//*
    fun contains(element: E): Boolean {
        TODO("implement me")
    }
}

open class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
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
}}*/
//*
class LinkedListSet<E : Comparable<E>> {
    private open class Node<E>(val element: E?, next: Node<E>?) {
        val next: AtomicRef<Node<E>?> = atomic(next)
    }

    private class Removed<E>(key: E, next: Node<E>?) : Node<E>(key, next)

    private fun isRemoved(node: Node<E>): Boolean {
        return node.next.value is Removed
    }

    private class Window<E> (
        var cur: Node<E>? = null,
        var next: Node<E>? = null
    )

    private val last = Node<E>(/*prev = first,*/ element = null, next = null)
    private val first = Node<E>(/*prev = null,*/ element = null, next = last)


    private val head = atomic(first)



    private fun findWindow(key: E): Window<E> {
        while (true) {
            val w = Window<E>()
            w.cur = head.value
            w.next = w.cur!!.next.value
            while (!isRemoved(w.cur!!) &&
                (w.next!!.element != last && w.next!!.element != first &&
                        ( w.next!!.element!! < key ||
                                isRemoved(w.next!!)))) {
                if (isRemoved(w.next!!)) {
                    val nextNext = w.next!!.next.value!!.next.value
                    w.next = if (w.cur!!.next.compareAndSet(w.next, nextNext)) nextNext else w.cur!!.next.value
                } else {
                    w.cur = w.next
                    w.next = w.cur!!.next.value
                }
            }
            if (!isRemoved(w.cur!!)) return w
        }
    }

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
            if (w.next!!.element == element) {
                return false
            }
            if (w.cur!!.next.compareAndSet(w.next, Node(element, w.next))) {
                return true
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
            val w = findWindow(element)
            if (w.cur!!.element != element) {
                return false
            }
            if (w.cur!!.next.compareAndSet(w.next, Removed(Int.MIN_VALUE as
                    E, w.next))) {
                return true
            }
        }
    }
    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    operator fun contains(element: E): Boolean {
        val w = findWindow(element)
        return w.next!!.element == element
    }

}