import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.io.*
import kotlin.reflect.*

abstract class TestBase(
    val sequentialSpecification: KClass<*>,
    val checkObstructionFreedom: Boolean = true,
) {
    @Test
    fun veryFastModelCheckingTest() = try {
        ModelCheckingOptions()
            .iterations(30)
            .invocationsPerIteration(1_000)
            .actorsBefore(2)
            .threads(3)
            .actorsPerThread(3)
            .actorsAfter(2)
            .checkObstructionFreedom(checkObstructionFreedom)
            .sequentialSpecification(sequentialSpecification.java)
            .check(this::class.java)
    } catch (t: Throwable) {
        throw t
    }

    @Test
    fun modelCheckingTest() = try {
        ModelCheckingOptions()
            .iterations(100)
            .invocationsPerIteration(10_000)
            .actorsBefore(2)
            .threads(3)
            .actorsPerThread(3)
            .actorsAfter(2)
            .checkObstructionFreedom(checkObstructionFreedom)
            .sequentialSpecification(sequentialSpecification.java)
            .check(this::class.java)
    } catch (t: Throwable) {
        throw t
    }

    @Test
    fun stressTest() = try {
        StressOptions()
            .iterations(100)
            .invocationsPerIteration(10_000)
            .actorsBefore(2)
            .threads(3)
            .actorsPerThread(3)
            .actorsAfter(2)
            .sequentialSpecification(sequentialSpecification.java)
            .check(this::class.java)
    } catch (t: Throwable) {
        throw t
    }
}