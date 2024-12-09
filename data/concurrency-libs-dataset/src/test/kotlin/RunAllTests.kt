import org.junit.runner.RunWith

@RunWith(ClassPathSuite::class)
@SuiteClasses("javautilconcurrent", "jctools", "agrona", "guava", "lincheckpaper")
class RunAllTests
