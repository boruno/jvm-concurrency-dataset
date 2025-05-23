package org.jetbrains.research

import org.jetbrains.research.cav23.AbstractLincheckTest
import org.junit.runner.Description
import org.junit.runner.Request
import org.junit.runner.Runner
import org.junit.runner.notification.RunNotifier
import org.junit.runners.ParentRunner
import org.junit.runners.model.InitializationError
import java.lang.reflect.Modifier
import java.net.URL
import java.util.*
import kotlin.test.Test

class ClassPathSuite(klass: Class<*>) : ParentRunner<Runner>(klass) {
    private val runners: List<Runner>

    private val root_package_name = "org.jetbrains.research."

    init {
        val suiteClasses = getSuiteClasses(klass)
        runners = createGroupedRunnersForClasses(suiteClasses)
    }

    private fun getSuiteClasses(klass: Class<*>): List<Class<*>> {
        val suiteClassesAnnotation = klass.getAnnotation(SuiteClasses::class.java)
            ?: throw InitializationError("ClassPathSuite needs to be annotated with @SuiteClasses")

        return suiteClassesAnnotation.value.flatMap { findClasses(root_package_name + it) }
    }

    private fun findClasses(packageName: String): List<Class<*>> {
        val classLoader = Thread.currentThread().contextClassLoader
        val packageNamePath = packageName.replace('.', '/')
        val resources = classLoader.getResources(packageNamePath)

        return resources.toList().flatMap { findClasses(it, packageName) }
    }

    private fun findClasses(resource: URL, packageName: String): List<Class<*>> {
        val classes: MutableList<Class<*>> = ArrayList()
        val items = resource.openStream().bufferedReader().readLines()

        for (item in items) {
            if (item.endsWith(".class")) {
                val className = item.substring(0, item.length - 6)
                try {
                    val clazz = Class.forName("$packageName.$className")
                    if (isSuitableTestClass(clazz)) {
                        classes.add(clazz)
                    }
                } catch (e: ClassNotFoundException) {
                    throw IllegalStateException(e)
                }
            }
        }

        return classes
    }

    private fun isSuitableTestClass(clazz: Class<*>): Boolean {
        // Check if the class contains @Test annotations (existing logic)
        val containsTestAnnotation = clazz.methods.any { it.isAnnotationPresent(Test::class.java) }

        // Check if the class extends any recognized abstract test base class
        val extendsAbstractTestBase = AbstractLincheckTest::class.java.isAssignableFrom(clazz)

        // Class must either contain @Test annotations or extend the abstract test base class
        return !clazz.isAnonymousClass &&
                !clazz.isLocalClass &&
                !clazz.isMemberClass &&
                !clazz.isSynthetic &&
                !clazz.isInterface &&
                !Modifier.isAbstract(clazz.modifiers) &&
                (containsTestAnnotation || extendsAbstractTestBase)
    }

    private fun createGroupedRunnersForClasses(classes: List<Class<*>>): List<Runner> {
        val classGroups = classes.groupBy { it.simpleName.removeSuffix("Test") }
        return classGroups.map { (name, classGroup) ->
            GroupedTestRunner(name, classGroup)
        }
    }

    override fun getChildren(): List<Runner> = runners

    override fun describeChild(child: Runner): Description = child.description

    override fun runChild(child: Runner, notifier: RunNotifier) = child.run(notifier)

    class GroupedTestRunner(
        private val name: String,
        classes: List<Class<*>>
    ) : Runner() {

        private val runners = classes.map { getRunner(it) }

        private fun getRunner(aClass: Class<*>): Runner =
            Request.aClass(aClass).runner

        override fun getDescription(): Description {
            val groupName = if (name.isEmpty()) "UnnamedGroup" else name
            val description = Description.createSuiteDescription(groupName)
            runners.forEach { description.addChild(it.description) }
            return description
        }

        override fun run(notifier: RunNotifier) {
            runners.forEach { it.run(notifier) }
        }
    }
}

annotation class SuiteClasses(vararg val value: String)