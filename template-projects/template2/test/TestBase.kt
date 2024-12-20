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
    val scenarios: Int = 100
) {
    @Test
    fun modelCheckingTest() = try {
        ModelCheckingOptions()
            .iterations(scenarios)
            .invocationsPerIteration(5_000)
            .actorsBefore(2)
            .threads(3)
            .actorsPerThread(2)
            .actorsAfter(2)
            .checkObstructionFreedom(checkObstructionFreedom)
            .sequentialSpecification(sequentialSpecification.java)
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
            .actorsBefore(2)
            .threads(3)
            .actorsPerThread(2)
            .actorsAfter(2)
            .sequentialSpecification(sequentialSpecification.java)
            .apply { customConfiguration() }
            .check(this::class.java)
    } catch (t: Throwable) {
        throw t
    }

    protected open fun Options<*, *>.customConfiguration() {}
}