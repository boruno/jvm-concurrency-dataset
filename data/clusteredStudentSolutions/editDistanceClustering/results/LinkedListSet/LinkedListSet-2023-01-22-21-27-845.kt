//package mpp.linkedlistset

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class LinkedListSet<E : Comparable<E>> {
    private open class Node<E>(val element: E?, next: Node<E>?) {
        val next: AtomicRef<Node<E>?> = atomic(next)
        //val prev: AtomicRef<Node<E>?> = atomic(prev)
        fun isNotRemoved(): Boolean {
            return next.value !is Removed
        }
        fun isRemoved(): Boolean {
            return next.value is Removed
        }
    }
    private class Removed<E>(element: E?, next: Node<E>?) : Node<E>( element, next)


    private class Block<E> (
        val cur: Node<E>,
        var next: Node<E>
    )

    private val last = Node<E>(element = null, next = null)
    private val first = Node<E>(element = null, next = last)


    private val head = atomic(first)
    init {
        last.next.value = first
    }



    private fun findBlock(key: E): Block<E> {
        while (true) {
            val curHead = head.value
            var block = Block(curHead, curHead.next.value!!)
            while (block.next != last && block.cur.isNotRemoved() && block.next.element!! < key) {
                if (block.next.isRemoved()) {
                    val next = block.next.next.value!!.next.value
                    block.next = if (block.cur.next.compareAndSet(block.next, next)) next!!
                                else block.cur.next.value!!
                } else {
                    val next = block.next
                    block = Block(next, next.next.value!!)
                }
            }
            if (block.cur.isNotRemoved()) return block
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
            val block = findBlock(element)
            if (block.next.element == element) {
                return false
            }
            val newNode = Node(element, block.next)
            if (block.cur.next.compareAndSet(block.next, newNode)) {
                return true
            }
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
        val block = findBlock(element)
        if (block.cur.element != element) {
            return false
        }
        val next: Node<E> = block.next
        if (block.cur.next.compareAndSet(block.next, Removed(null, next))) {
            return true
        }
        return false
    }
    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    operator fun contains(element: E): Boolean {
        return findBlock(element).next.element == element
    }
}