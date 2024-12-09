import xml.etree.ElementTree as ET
import re, os


class LincheckReport:
    PATTERN = r"""^{exception_type}:(?P<scenario_table>.*?)(The following interleaving leads to the error:
(?P<short_interleaving_table>.*?)
)?(Detailed trace:
(?P<long_interleaving_table>.*?)
)?(Short LLM trace:
(?P<short_llm_trace>.*?)
)?(Detailed LLM trace:
(?P<detailed_llm_trace>.*?)
)?(Short LLM trace:\(compact format\)
(?P<short_llm_trace_compact>.*?)
)?(Detailed LLM trace:\(compact format\)
(?P<detailed_llm_trace_compact>.*?))?


(?P<stack_trace>[^\|]*?)?$"""

    def __init__(self, failure_info):
        self.class_name = failure_info['class_name'].removesuffix("Test")
        self.testcase_name = failure_info['testcase_name']
        self.exception_type = failure_info['exception_type']
        self.terminal_message = failure_info['terminal_message']

        parsed = re.match(self.PATTERN.format(exception_type=self.exception_type), self.terminal_message, re.DOTALL)

        if parsed is None:
            # Wow! You've caught a bug in Lincheck.
            self.scenario_table = None
            self.short_interleaving_table = None
            self.long_interleaving_table = None
            self.stack_trace = None
            self.short_llm_trace = None
            self.detailed_llm_trace = None
            self.short_llm_trace_compact = None
            self.detailed_llm_trace_compact = None
            return

        self.scenario_table = parsed.group('scenario_table')
        self.short_interleaving_table = parsed.group('long_interleaving_table')
        self.long_interleaving_table = parsed.group('long_interleaving_table')
        self.stack_trace = parsed.group('stack_trace')
        self.short_llm_trace = parsed.group('short_llm_trace')
        self.detailed_llm_trace = parsed.group('detailed_llm_trace')
        self.short_llm_trace_compact = parsed.group('short_llm_trace_compact')
        self.detailed_llm_trace_compact = parsed.group('detailed_llm_trace_compact')


def extract_reports_from_xml(file_name):
    root = ET.parse(file_name)

    reports = {}
    for testcase in root.findall('testcase'):
        failure = testcase.find('failure')
        if failure is not None:
            failure_info = {
                'class_name': testcase.attrib.get('classname'),
                'testcase_name': testcase.attrib.get('name'),
                'exception_type': failure.attrib.get('type'),
                'terminal_message': failure.text,
            }
            reports[failure_info['testcase_name']] = LincheckReport(failure_info)

    return reports


def extract_reports_from_dir(dir):
    reports = {}
    for root, dirs, files in os.walk(dir):
        for file in files:
            reports[file.removesuffix("Test.xml")] = extract_reports_from_xml(os.path.join(root, file))
    return reports


if __name__ == '__main__':
    print(extract_reports_from_dir("cav_test/reportsExtended"))
    # report = extract_reports_from_xml("cav_test/reports/ConcurrencyOptimalTreeMapTest.xml")['modelCheckingTestFast']
    # print("Class name:")
    # print(report.class_name)
    # print("Testcase name:")
    # print(report.testcase_name)
    # print("Exception type:")
    # print(report.exception_type)
    # print("Terminal message:")
    # print(report.terminal_message)
    # print("Failure type:")
    # print(report.failure_type)
    # print("Scenario table:")
    # print(report.scenario_table)
    # print("Scenario comment:")
    # print(report.scenario_comment)
    # print("Short interleaving table:")
    # print(report.short_interleaving_table)
    # print("Long interleaving table:")
    # print(report.long_interleaving_table)
    # print("Stack trace:")
    # print(report.stack_trace)
