package day1

import kotlinx.atomicfu.*
import kotlin.coroutines.*

class MSQueue<E> : Queue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    override fun toString(): String = """
        Q{head=${head.value.element}; tail=${tail.value.element}}
    """.trimIndent()

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        val node = Node(element)
        while (true) {
            val currentTail = tail.value
            if (currentTail.next.compareAndSet(null, node)) {
                tail.value = node
                // tail.compareAndSet(currentTail, node)
                return
            } else {
                tail.value = currentTail.next.value!!
                // tail.compareAndSet(currentTail, currentTail.next.value!!)
            }
        }
    }

    override fun validate() {
        check(tail.value.next.value == null) {
            "Tail must be null at the end"
        }
    }

    suspend fun ExecutionContext.enqueueTest(element: E) {
        val node = Node(element)
        while (true) {
            awaitStep(1)
            val currentTail = tail.value
            awaitStep(2)
            if (currentTail.next.compareAndSet(null, node)) {
                awaitStep(3)
                tail.value = node
                // tail.compareAndSet(currentTail, node)
                return
            } else {
                awaitStep(4)
                tail.value = currentTail.next.value!!
                // tail.compareAndSet(currentTail, currentTail.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val nextVal = currentHead.next.value ?: return null
            if (head.compareAndSet(currentHead, nextVal)) {
                return nextVal.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}


interface ExecutionContext {
    suspend fun awaitStep(step: Int)
}

class Actor(
    val name: String,
    code: suspend ExecutionContext.() -> Unit
): ExecutionContext {
    var result: Result<Unit>? = null

    private var currentStep: Int = -1
    private var currentContinuation: Continuation<Unit>? = null

    init {
        code.startCoroutine(this, Continuation(EmptyCoroutineContext) { result = it })
    }

    override suspend fun awaitStep(step: Int) {
        suspendCoroutine<Unit> {
            if (currentContinuation != null) error("Someone already awaits the step")
            currentStep = step
            currentContinuation = it
        }
    }

    fun runStep(): Int {
        if (result != null) {
            result!!.getOrThrow()
            error("No steps left")
        }
        val continuation = currentContinuation ?: return currentStep
        val step = currentStep
        currentContinuation = null
        continuation.resume(Unit) // new current continuation and step will be set, or result will be set

        return step
    }
}

data class Step(
    val actorId: String,
    val stepNo: Int
) {
    override fun toString(): String = "$actorId#$stepNo"
}

fun executionStepsOf(stepsString: String) = stepsString.split("\\s+".toRegex()).map {
    val (actorId, stepNoString) = it.split("#")
    Step(actorId, stepNoString.toInt())
}

fun runTestExecution(
    executions: Map<String, suspend ExecutionContext.() -> Unit>,
    stepsSequence: List<Step>,
    onEachStep: (Step) -> Unit
) {
    val actors = executions.mapValues { Actor(it.key, it.value) }

    val actualSteps = mutableListOf<Step>()
    for (step in stepsSequence) {
        val (actorId, expectedStepNumber) = step
        val actor = actors[actorId] ?: error("Unknown actor $actorId")
        val actualStepNumber = actor.runStep()
        onEachStep(step)
        actualSteps += Step(actorId, actualStepNumber)
        if (expectedStepNumber != actualStepNumber) {
            error("""
                Unexpected execution sequence:
                  ${actualSteps.joinToString(" ")}
                Expected:
                  ${stepsSequence.joinToString(" ")}
            """.trimIndent())
        }
    }
}