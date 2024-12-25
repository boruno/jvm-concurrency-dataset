@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

//package day2

import java.util.concurrent.atomic.*

class MSQueueWithLinearTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node>
    private val tail: AtomicReference<Node>

    init {
        val dummy = Node(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        val node = Node(element)
        while (true) {
            val curTail = tail.get()
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node) //note(vg): if we fail here, it's ok, it means that some other thread moved the tail pointer for us
                if (curTail.extractedOrRemoved)
                    curTail.removePhysically()
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.get()) //note(vg): help another thread to move the tail pointer
            }
        }
        // TODO: When adding a new node, check whether the previous tail is logically removed.
        // TODO: If so, remove it physically from the linked list.
    }

    override fun dequeue(): E? {
        // TODO: After moving the `head` pointer forward, mark the node that contains the extracting element as
        // TODO: "extracted or removed", restarting the operation if this node has already been removed.

        while (true){
            val curHead = head.get()
            val curHeadNext = curHead.next.get() ?: return null
            if (head.compareAndSet(curHead, curHeadNext))
                if (curHeadNext.markExtractedOrRemoved()) {
                    val result = curHeadNext.element
                    curHeadNext.element = null
                    return result
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

    // TODO: Node is an inner class for accessing `head` in `remove()`
    private inner class Node(
        var element: E?
    ) {
        val next = AtomicReference<Node?>(null)

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

        fun findPrevious(): Node? {
            var previous: Node? = null
            var node = head.get()
            while (true) {
                if (node == null) return null
                if (node.element == element) return previous
                previous = node
                node = node.next.get()
            }
        }

        fun removePhysically() {
            val curPrev = findPrevious()
            val curNext = next.get()
            // TODO: Do not remove `head` and `tail` physically to make the algorithm simpler. In case a tail node is
            // TODO: logically removed, it will be removed physically by `enqueue(..)`.
            if (curPrev != null) {
                curPrev.next.set(curNext)
                if (curNext!= null && curNext.extractedOrRemoved)
                    curNext.remove()
            }
        }

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            // TODO: The removal procedure is split into two phases.
            // TODO: First, you need to mark the node as "extracted or removed".
            val isLogicallyRemoved = markExtractedOrRemoved()
            // TODO: On success, this node is logically removed, and the operation should return `true` at the end.
            // TODO: In the second phase, the node should be removed physically, updating the `next` field of the
            // TODO: previous node to `this.next.value`.
            return isLogicallyRemoved.also { if (it) removePhysically() }

        }
    }
}
