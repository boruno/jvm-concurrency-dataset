import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import org.junit.runners.*
import kotlin.reflect.*

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
abstract class TestBase(
    val sequentialSpecification: KClass<*>,
    val checkObstructionFreedom: Boolean = true,
    val scenarios: Int = 150,
    val threads: Int = 3,
    val actorsBefore: Int = 1
) {

    @Test
    fun modelCheckingTest() = ModelCheckingOptions()
        .iterations(scenarios)
        .invocationsPerIteration(10000)
        .actorsBefore(actorsBefore)
        .actorsAfter(0)
        .threads(threads)
        .actorsPerThread(3)
        .checkObstructionFreedom(checkObstructionFreedom)
        .sequentialSpecification(sequentialSpecification.java)
        .apply { customConfiguration() }
        .check(this::class.java)

    @Test
    fun stressTest() = StressOptions()
        .iterations(scenarios)
        .invocationsPerIteration(25000)
        .actorsBefore(actorsBefore)
        .actorsAfter(0)
        .threads(threads)
        .actorsPerThread(3)
        .sequentialSpecification(sequentialSpecification.java)
        .apply { customConfiguration() }
        .check(this::class.java)

    protected open fun Options<*, *>.customConfiguration() {}
}