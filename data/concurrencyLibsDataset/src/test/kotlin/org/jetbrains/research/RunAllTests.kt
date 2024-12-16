package org.jetbrains.research

import org.junit.runner.RunWith

@RunWith(ClassPathSuite::class)
@SuiteClasses("juc", "jctools", "agrona", "guava", "cav23")
class RunAllTests
