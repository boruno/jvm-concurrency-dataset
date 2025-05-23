@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

//package day3

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
            val newNode = Node(element, curTail)
            if (curTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)
                if (curTail.extractedOrRemoved) {
                    curTail.physicallyRemove()
                }
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
                if (curTail.extractedOrRemoved) {
                    curTail.physicallyRemove()
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val toDelete = curHead.next.value ?: return null
            if (head.compareAndSet(curHead, toDelete)) {
                toDelete.prev.value = null
                if (!toDelete.markExtractedOrRemoved())
                    continue
                return toDelete.element
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

    /**
     * This is a string representation of the data structure,
     * you see it in Lincheck tests when they fail.
     * DO NOT CHANGE THIS CODE.
     */
    override fun toString(): String {
        // Choose the leftmost node.
        var node = head.value
        if (tail.value.next.value === node) {
            node = tail.value
        }
        // Traverse the linked list.
        val nodes = arrayListOf<String>()
        while (true) {
            nodes += (if (head.value === node) "HEAD = " else "") +
                    (if (tail.value === node) "TAIL = " else "") +
                    "<${node.element}" +
                    (if (node.prev.value != null) ", prev = ${node.prev.value!!.element}" else "") +
                    (if (node.extractedOrRemoved) ", extractedOrRemoved" else "") +
                    (if (node.next.value != null) ", next = ${node.next.value!!.element}" else "") +
                    ">"
            // Process the next node.
            node = node.next.value ?: break
        }
        return nodes.joinToString(", ")
    }

    private class Node<E>(
        var element: E?,
        prev: Node<E>?
    ) {
        val next = atomic<Node<E>?>(null)
        val prev = atomic(prev)

        /**
         * TODO: Both [dequeue] and [remove] should mark
         * TODO: nodes as "extracted or removed".
         */
        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved
            get() = _extractedOrRemoved.value



        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(
            expect = false,
            update = true
        )

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            if (!markExtractedOrRemoved())
                return false
            physicallyRemove()
            return true
        }

        fun physicallyRemove() : Boolean {
            val newNext = this.next.value ?: return false
            val curHead = this.prev.value ?: return false

            if (curHead.next.compareAndSet(this, newNext))
                this.prev.value = null
            newNext.prev.compareAndSet(this, curHead)

            if (curHead.extractedOrRemoved) {
                curHead.physicallyRemove()
            }
            if (newNext.extractedOrRemoved) {
                newNext.physicallyRemove()
            }
            return true
        }
    }
}