import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import org.junit.runners.*
import java.io.*
import kotlin.reflect.*

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
abstract class TestBase(
    val sequentialSpecification: KClass<*>,
    val checkObstructionFreedom: Boolean = true,
    val scenarios: Int = 150
) {
    @Test
    fun modelCheckingTest() = try {
        ModelCheckingOptions()
            .iterations(scenarios)
            .invocationsPerIteration(5_000)
            .actorsBefore(1)
            .threads(3)
            .actorsPerThread(2)
            .actorsAfter(0)
            .checkObstructionFreedom(checkObstructionFreedom)
            .sequentialSpecification(sequentialSpecification.java)
            .logLevel(LoggingLevel.INFO)
            .apply { customConfiguration() }
            .check(this::class.java)
    } catch (t: Throwable) {
        throw t
    }

    @Test
    fun stressTest() = try {
        StressOptions()
            .iterations(scenarios)
            .invocationsPerIteration(5_000)
            .actorsBefore(1)
            .threads(3)
            .actorsPerThread(2)
            .actorsAfter(0)
            .sequentialSpecification(sequentialSpecification.java)
            .logLevel(LoggingLevel.INFO)
            .apply { customConfiguration() }
            .check(this::class.java)
    } catch (t: Throwable) {
        throw t
    }

    protected open fun Options<*, *>.customConfiguration() {}
}