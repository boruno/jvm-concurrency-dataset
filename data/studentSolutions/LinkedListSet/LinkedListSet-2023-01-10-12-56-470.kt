//package mpp.linkedlistset

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic


class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = null, next = Node(element = null, next = null))
    private val last = first.next

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true) {
            val window = findWindow(element)
            if (!window.next!!.nextAndRemoved.value.second
                && window.next.element == element
            ) {
                return false
            }
            val newNode = Node(element, window.next)
            if (window.curNode!!.nextAndRemoved.compareAndSet(window.next, newNode, false, false)) {
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
            val window = findWindow(element)
            if (window.next!!.nextAndRemoved.value.second
                || window.next.element != element
            ) {
                return false
            }
            val nodeToRemove = window.next.nextAndRemoved.value.first
            if (window.next.nextAndRemoved.compareAndSet(nodeToRemove, nodeToRemove, false, true)) {
                window.curNode!!.nextAndRemoved.compareAndSet(window.next, nodeToRemove, false, false)
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val window = findWindow(element)
        return !window.next!!.nextAndRemoved.value.second &&
            window.next.element == element

    }

    fun findWindow(x: E): Window<E> {
       loop@ while (true) {
            var curNode: Node<E>? = first
            var nextNode = curNode!!.nextAndRemoved.value.first
            while (nextNode != last && (curNode == first || nextNode!!.element < x)) {
                val (nextNextNode, nextNextRemoved) = nextNode?.nextAndRemoved?.value ?: Pair(null, false)
                if (nextNextRemoved) {
                    if (!curNode!!.nextAndRemoved.compareAndSet(nextNode, nextNextNode, false, false))
                        continue@loop
                    nextNode = nextNextNode
                } else {
                    curNode = nextNode
                    nextNode = curNode?.nextAndRemoved?.value?.first
                }
            }
           return Window(curNode, nextNode)
        }
    }
}

class AtomicNodeWrapper<E : Comparable<E>>(
    node: Node<E>?,
    isRemoved: Boolean
) {
    private val atomicNode: AtomicRef<NodeWrapper>

    init {
        if (!isRemoved) {
            atomicNode = atomic(Alive(node))
        } else {
            atomicNode = atomic(Dead(node))
        }
    }

    val value: Pair<Node<E>?, Boolean>
        get() {
            val node = atomicNode.value
            return if (node is Alive<*>) {
                val alive = (node as Alive<E>)
                Pair(alive.node, false)
            } else {
                val dead = (node as Dead<E>)
                Pair(dead.node, true)

            }
        }

    fun compareAndSet(
        expected: Node<E>?,
        update: Node<E>?,
        expectedMark: Boolean,
        newMark: Boolean,
    ): Boolean {
        val node = atomicNode.value
        if (!expectedMark) {
            if (atomicNode.value is Alive<*>) {
                if ((atomicNode.value as Alive<*>).node == expected) {
                    return if (!newMark) {
                        atomicNode.compareAndSet(node, Alive(update))
                    } else {
                        atomicNode.compareAndSet(node, Dead(update))
                    }
                }
            }
        } else {
            if ((atomicNode.value as Dead<*>).node == expected) {
                return if (!newMark) {
                    atomicNode.compareAndSet(node, Alive(update))
                } else {
                    atomicNode.compareAndSet(node, Dead(update))
                }
            }
        }
        return false
    }
}

sealed interface NodeWrapper

data class Alive<E : Comparable<E>>(
    val node: Node<E>?
) : NodeWrapper

data class Dead<E : Comparable<E>>(
    val node: Node<E>?
) : NodeWrapper

data class Window<E : Comparable<E>>(
    val curNode: Node<E>?,
    val next: Node<E>?
)

class Node<E : Comparable<E>>(
    element: E?,
    next: Node<E>?,
) {
    var nextAndRemoved: AtomicNodeWrapper<E>
    init {
        nextAndRemoved = AtomicNodeWrapper(next, false)
    }
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!


    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        nextAndRemoved = AtomicNodeWrapper(next, false)
        _next.value = value
    }

    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}