@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

package day3

import day1.MSQueue
import kotlinx.atomicfu.*

class MSQueueWithLinearTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    init {
        val dummy = Node(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val newTail = Node(element)
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
                if (curTail.extractedOrRemoved) {
                    curTail.remove()
                }
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val bebra = curHead.next.value ?: return null
            if (head.compareAndSet(curHead, bebra) && bebra.markExtractedOrRemoved()) {
                return bebra.element
            }
        }
    }

    override fun remove(element: E): Boolean {
        var prevNode = head.value
        var curNode = prevNode.next.value

        while (curNode != null) {
            if (curNode.element == element && curNode.markExtractedOrRemoved()) {
                if (curNode.extractedOrRemoved) {
                    prevNode.next.compareAndSet(curNode, curNode.next.value)
                    curNode.next.value = null
                }
                return true
            } else {
                prevNode = curNode
                curNode = curNode.next.value
            }
        }

        return false
    }

    override fun checkNoRemovedElements() {
        var node = head.value
        while (true) {
            if (node !== head.value && node !== tail.value) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of this queue"
                }
            }
            node = node.next.value ?: break
        }
    }

    override fun toString(): String {
        var node = head.value
        if (tail.value.next.value === node) {
            node = tail.value
        }
        val nodes = arrayListOf<String>()
        while (true) {
            nodes += (if (head.value === node) "HEAD = " else "") +
                    (if (tail.value === node) "TAIL = " else "") +
                    "<${node.element}" +
                    (if (node.extractedOrRemoved) ", extractedOrRemoved" else "") +
                    ">"
            node = node.next.value ?: break
        }
        return nodes.joinToString(", ")
    }

    private inner class Node(
        var element: E?
    ) {
        val next = atomic<Node?>(null)

        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(false, true)

        fun remove(): Boolean {
            val res = markExtractedOrRemoved()
            if (res && this.extractedOrRemoved) {
                var prevNode = head.value
                var curNode = prevNode.next.value

                while (curNode != null) {
                    if (curNode == this) {
                        prevNode.next.compareAndSet(curNode, curNode.next.value)
//                        curNode.next.value = null
                        break
                    } else {
                        prevNode = curNode
                        curNode = curNode.next.value
                    }
                }
            }
            return res
        }
    }
}
