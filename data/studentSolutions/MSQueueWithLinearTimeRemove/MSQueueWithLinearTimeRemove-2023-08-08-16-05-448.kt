@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

package day2

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MSQueueWithLinearTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node>
    private val tail: AtomicReference<Node>

    init {
        val dummy = Node(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val newNode = Node(element)
            val currTail = tail.get()
            if (currTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currTail, newNode)
                if (currTail.extractedOrRemoved) {
                    val prev = findPreviousNode(currTail)
                    prev?.next?.set(newNode)
//                    val prev = findPreviousNode(currTail)
//                    var newNext = currTail
//                    val tailNode = tail.get()
//                    while (newNext != null && (newNext != tailNode || newNext.extractedOrRemoved)) {
//                        newNext = newNext.next.get()
//                    }
//                    prev?.next?.set(newNext)
                }
                return
            } else {
                tail.compareAndSet(currTail, currTail.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currHead = head.get()
            val currHeadNext = currHead.next.get() ?: return null
            if (head.compareAndSet(currHead, currHeadNext)) {
                if (currHeadNext.markExtractedOrRemoved()) {
                    return currHeadNext.element
                }
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.get()
        while (true) {
            val next = node.next.get() ?: return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun validate() {
        check(tail.get().next.get() == null) {
            "tail.next must be null"
        }
        var node = head.get()
        // Traverse the linked list
        while (true) {
            if (node !== head.get() && node !== tail.get()) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of this queue"
                }
            }
            node = node.next.get() ?: break
        }
    }

    private inner class Node(var element: E?) {
        val next = AtomicReference<Node?>(null)

        private val _extractedOrRemoved = AtomicBoolean(false)
        val extractedOrRemoved: Boolean
            get() = _extractedOrRemoved.get()

        fun markExtractedOrRemoved(): Boolean {
            return _extractedOrRemoved.compareAndSet(false, true)
        }

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully removed,
         * or `false` if it has already been removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            val isMarked = markExtractedOrRemoved()
            val tailNode = tail.get()
            if (this != tailNode) {
                val prev = findPreviousNode(this)
                val next = next.get()
                prev?.next?.set(next)

                if (next?.extractedOrRemoved == true) {
                    val prev2 = findPreviousNode(next)
                    val next2 = next.next.get()
                    prev2?.next?.set(next2)
                }
            }

            return isMarked
        }
    }

    private fun findPreviousNode(findNode: Node): Node? {
        var node = head.get()
        while (true) {
            val next = node.next.get() ?: return null
            if (next == findNode) return node
            node = next
        }
    }
}