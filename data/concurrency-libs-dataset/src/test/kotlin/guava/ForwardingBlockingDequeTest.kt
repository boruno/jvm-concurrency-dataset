@file:Suppress("HasPlatformType")

package guava

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import com.google.common.collect.*
import java.util.concurrent.*

class ForwardingBlockingDequeTest {
    private val deque = CustomForwardingBlockingDeque()

    @Operation
    fun addFirst(e: Int) = deque.addFirst(e)

    @Operation
    fun addLast(e: Int) = deque.addLast(e)

    @Operation
    fun pollFirst() = deque.pollFirst()

    @Operation
    fun pollLast() = deque.pollLast()

    @Operation
    fun peekFirst() = deque.peekFirst()

    @Operation
    fun peekLast() = deque.peekLast()

    @Operation
    fun isEmpty() = deque.isEmpty()

    @Operation
    fun size() = deque.size

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)

    class CustomForwardingBlockingDeque : ForwardingBlockingDeque<Int>() {
        private val delegate = LinkedBlockingDeque<Int>()

        override fun delegate(): BlockingDeque<Int> = delegate
    }
}
