@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

package day2

import kotlinx.atomicfu.*

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(element = null, prev = null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value

            val node = Node(element, prev = curTail)

            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                curTail.removePhysicallyIfMarked()

                return
            } else {
                val next = curTail.next.value ?: continue
                tail.compareAndSet(curTail, next)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val next = curHead.next
            val value = next.value ?: return null

            if (head.compareAndSet(curHead, value) && curHead.remove()) {
                value.prev.compareAndSet(curHead, null)
                return value.element
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.value
        while (true) {
            val next = node.next.value
            if (next == null) return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun checkNoRemovedElements() {
        check(head.value.prev.value == null) {
            "`head.prev` must be null"
        }
        check(tail.value.next.value == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = head.value
        while (true) {
            if (node !== head.value && node !== tail.value) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue"
                }
            }
            val nodeNext = node.next.value
            // Is this the end of the linked list?
            if (nodeNext == null) break
            // Is next.prev points to the current node?
            val nodeNextPrev = nodeNext.prev.value
            check(nodeNextPrev != null) {
                "The `prev` pointer of node with element ${nodeNext.element} is `null`, while the node is in the middle of the queue"
            }
            check(nodeNextPrev == node) {
                "node.next.prev != node; `node` contains ${node.element}, `node.next` contains ${nodeNext.element}"
            }
            // Process the next node.
            node = nodeNext
        }
    }

    private class Node<E>(
        var element: E?,
        prev: Node<E>?
    ) {
        val next = atomic<Node<E>?>(null)
        val prev = atomic(prev)

        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(expect = false, update = true)

        fun removePhysicallyIfMarked() {
            if (!extractedOrRemoved) {
                return
            }

            val curPrev = prev.value
            val curNext = next.value

            if (curNext != null) {
                curNext.prev.value = curPrev
            }

            if (curPrev != null) {
                curPrev.next.value = curNext
            }

            if (curPrev?.extractedOrRemoved == true) {
                curPrev.remove()
            }

            if (curNext?.extractedOrRemoved == true) {
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
            val logicallyRemoved = markExtractedOrRemoved()

            if (next.value == null || prev.value == null) {
                return logicallyRemoved
            }

            removePhysicallyIfMarked()

            return logicallyRemoved
        }
    }
}