import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.paramgen.*
import org.jetbrains.kotlinx.lincheck.verifier.*
import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.io.*
import kotlin.reflect.*

@Param(name = "key", gen = IntGen::class, conf = "1:8")
@Param(name = "value", gen = IntGen::class, conf = "1:10")
class IntIntHashMapTest {
    private val map = IntIntHashMap()

    @Operation
    fun put(@Param(name = "key") key: Int, @Param(name = "value") value: Int): Int = map.put(key, value)

    @Operation
    fun remove(@Param(name = "key") key: Int): Int = map.remove(key)

    @Operation
    fun get(@Param(name = "key") key: Int): Int = map.get(key)

    @Test
    fun modelCheckingTest() = try {
        ModelCheckingOptions()
            .iterations(100)
            .invocationsPerIteration(10000)
            .actorsBefore(0)
            .actorsAfter(0)
            .threads(3)
            .actorsPerThread(3)
            .checkObstructionFreedom(true)
            .sequentialSpecification(IntIntHashMapSequential::class.java)
            .check(this::class.java)
    } catch (t: Throwable) {
        throw t
    }

    @Test
    fun stressTest() = try {
        StressOptions()
            .iterations(100)
            .invocationsPerIteration(10000)
            .actorsBefore(0)
            .actorsAfter(0)
            .threads(3)
            .actorsPerThread(3)
            .sequentialSpecification(IntIntHashMapSequential::class.java)
            .check(this::class.java)
    } catch (t: Throwable) {
        throw t
    }
}

class IntIntHashMapSequential : VerifierState() {
    private val map = HashMap<Int, Int>()

    fun put(key: Int, value: Int): Int = map.put(key, value) ?: 0
    fun remove(key: Int): Int = map.remove(key) ?: 0
    fun get(key: Int): Int = map.get(key) ?: 0

    override fun extractState() = map
}