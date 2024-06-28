package mpp.linkedlistset

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic


class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = -1000000 as E, next = null)
    private val last = Node(prev = first, element = 1000000000 as E, next = null)

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
    fun add(element: E): Boolean {
        while (true) {
            val window = findWindow(element)
            if (!window.next!!.nextAndRemoved.value.second
                && window.next.element == element
            ) {
                return false
            }
            val newNode = Node(window.curNode, element, window.next)
            if (window.curNode!!.nextAndRemoved.compareAndSet(window.next, newNode, false, false)) {
                window.curNode.setNext(newNode)
                window.next.setPrev(newNode)
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
            println(curNode.next?.element)
            println(nextNode?.element)
            while ((curNode == first && nextNode != last) || curNode!!.element < x) {
                val (nextNextNode, nextNextRemoved) = nextNode?.nextAndRemoved?.value ?: Pair(null, false)
                if (nextNextRemoved) {
                    if (!curNode.nextAndRemoved.compareAndSet(nextNode, nextNextNode, false, false))
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
    prev: Node<E>?,
    element: E?,
    next: Node<E>?,
) {
    val nextAndRemoved: AtomicNodeWrapper<E>
    init {
        println(next?.element)
        nextAndRemoved = AtomicNodeWrapper(next, false)
    }
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

//class LinkedListSet<E : Comparable<E>> {
//    private class MyAtomicMarkableReference(node: Node?, isRemoved: Boolean) {
//        private var nodeAtomicRef: AtomicRef<NodeWrapper>? = null
//
//        init {
//            nodeAtomicRef = if (!isRemoved) {
//                atomic(Alive(node))
//            } else {
//                atomic(Removed(node))
//            }
//        }
//
//        operator fun get(flag: BooleanArray): Node? {
//            val node = nodeAtomicRef!!.value
//            if (node is Alive) {
//                flag[0] = false
//                return node.node
//            }
//            if (node is Removed) {
//                flag[0] = true
//                return node.node
//            }
//            throw RuntimeException("unexpected case")
//        }
//
//        val reference: Node?
//            get() = get(BooleanArray(1))
//        val isMarked: Boolean
//            get() {
//                val res = BooleanArray(1)
//                get(res)
//                return res[0]
//            }
//
//        fun compareAndSet(
//            expectedNode: Node?, newNode: Node?,
//            expectedMark: Boolean, newMark: Boolean
//        ): Boolean {
//            val node = nodeAtomicRef!!.value
//            if (!expectedMark) {
//                if (node is Alive) {
//                    val node1 = node.node
//                    if (node1 == expectedNode) {
//                        return if (!newMark) {
//                            nodeAtomicRef!!.compareAndSet(node, Alive(newNode))
//                        } else {
//                            nodeAtomicRef!!.compareAndSet(node, Removed(newNode))
//                        }
//                    }
//                }
//            } else {
//                if (node is Removed) {
//                    val node1 = node.node
//                    if (node1 == expectedNode) {
//                        return if (!newMark) {
//                            nodeAtomicRef!!.compareAndSet(node, Alive(newNode))
//                        } else {
//                            nodeAtomicRef!!.compareAndSet(node, Removed(newNode))
//                        }
//                    }
//                }
//            }
//            return false
//        }
//    }
//
//    private interface NodeWrapper
//    private class Removed(val node: Node?) : NodeWrapper
//    private class Alive(val node: Node?) : NodeWrapper
//    private class Node(val x: Int, next: Node?) {
//        val nextAndFlag = MyAtomicMarkableReference(next, false)
//    }
//
//    private class Window(val cur: Node?, val next: Node?)
//
//    private val head = Node(Int.MIN_VALUE, Node(Int.MAX_VALUE, null))
//
//    /**
//     * Returns the [Window], where cur.x < x <= next.x
//     */
//    private fun findWindow(x: Int): Window {
//        retry@ while (true) {
//            var w_cur: Node? = head
//            var w_next = w_cur!!.nextAndFlag.reference
//            val w_next_is_removed = BooleanArray(1)
//            while (w_next!!.x < x) {
//                val w_next_next = w_next.nextAndFlag[w_next_is_removed]
//                if (w_next_is_removed[0]) {
//                    if (!w_cur!!.nextAndFlag.compareAndSet(w_next, w_next_next, false, false)) {
//                        continue@retry
//                    }
//                    w_next = w_next_next
//                } else {
//                    w_cur = w_next
//                    w_next = w_cur.nextAndFlag.reference
//                }
//            }
//            return Window(w_cur, w_next)
//        }
//    }
//
//    fun add(x: Int): Boolean {
//        while (true) {
//            val w = findWindow(x)
//            if (!w.next!!.nextAndFlag.isMarked) {
//                if (w.next.x == x) {
//                    return false
//                }
//            }
//            val node = Node(x, w.next)
//            if (w.cur!!.nextAndFlag.compareAndSet(w.next, node, false, false)) {
//                return true
//            }
//        }
//    }
//
//    fun remove(x: Int): Boolean {
//        while (true) {
//            val w = findWindow(x)
//            if (w.next!!.nextAndFlag.isMarked || w.next.x != x) {
//                return false
//            }
//            val node = w.next.nextAndFlag.reference
//            if (w.next.nextAndFlag.compareAndSet(node, node, false, true)) {
//                w.cur!!.nextAndFlag.compareAndSet(w.next, node, false, false)
//                return true
//            }
//        }
//    }
//
//    operator fun contains(x: Int): Boolean {
//        val w = findWindow(x)
//        return if (!w.next!!.nextAndFlag.isMarked) {
//            w.next.x == x
//        } else false
//    }
//}