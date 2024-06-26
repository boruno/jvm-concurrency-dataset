@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

package day2

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(element = null, prev = null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val tailNode = tail.get()
            val el = Node(element, tailNode)
            if (tailNode.next.compareAndSet(null, el)) {
                if (tailNode.extractedOrRemoved) tailNode.physicalRemove()
                tail.compareAndSet(tailNode, el)
                return
            } else {
                tail.compareAndSet(tailNode, tailNode.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        // TODO: After moving the `head` pointer forward,
        // TODO: mark the node that contains the extracting
        // TODO: element as "extracted or removed", restarting
        // TODO: the operation if this node has already been removed.

        while (true) {
            val headNode = head.get()
            val next = headNode.next.get() ?: return null

            next.prev.set(null)
            if (head.compareAndSet(headNode, next)) {
                if (next.markExtractedOrRemoved()) return next.element
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.get()
        while (true) {
            val next = node.next.get()
            if (next == null) return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun validate() {
        check(head.get().prev.get() == null) {
            "`head.prev` must be null"
        }
        check(tail.get().next.get() == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = head.get()
        while (true) {
            if (node !== head.get() && node !== tail.get()) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue"
                }
            }
            val nodeNext = node.next.get()
            // Is this the end of the linked list?
            if (nodeNext == null) break
            // Is next.prev points to the current node?
            val nodeNextPrev = nodeNext.prev.get()
            check(nodeNextPrev != null) {
                "The `prev` pointer of node with element ${nodeNext.element} is `null`, while the node is in the middle of the queue"
            }
            check(nodeNextPrev == node) {
                "node.next.prev != node; `node` contains ${node.element}, `node.next` contains ${nodeNext.element}, `node.next.prev` contains ${nodeNextPrev.element}"
            }
            // Process the next node.
            node = nodeNext
        }
    }

    private class Node<E>(
        var element: E?,
        prev: Node<E>?
    ) {
        val next = AtomicReference<Node<E>?>(null)
        val prev = AtomicReference(prev)

        /**
         * TODO: Both [dequeue] and [remove] should mark
         * TODO: nodes as "extracted or removed".
         */
        private val _extractedOrRemoved = AtomicBoolean(false)
        val extractedOrRemoved
            get() =
                _extractedOrRemoved.get()

        fun markExtractedOrRemoved(): Boolean =
            _extractedOrRemoved.compareAndSet(false, true)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            return if (markExtractedOrRemoved()) {
                physicalRemove()
                true
            } else {
                false
            }

//            val curNext = next.get()
//            val curPrev = prev.get()
//            if (curNext != null && curPrev != null) {
//                curPrev.next.compareAndSet(this, curNext)
//                curNext.prev.compareAndSet(this, curPrev)
//                if (curNext.extractedOrRemoved) curNext.remove()
//            }
//            return markedRemoved
        }

        fun physicalRemove() {
            val curPrev = prev.get()
            val curNext = next.get()
            if (curNext != null && curPrev != null) {
                curPrev.next.set(curNext)
                while (true) {
                    val curNextPrev = curNext.prev.get()
                    if (curNextPrev == null) break
                    if (curNext.prev.compareAndSet(curNextPrev, curPrev)) break

                }
                if (curNext.extractedOrRemoved) curNext.physicalRemove()
                if (curPrev.extractedOrRemoved) curPrev.physicalRemove()
            }
        }
    }
}