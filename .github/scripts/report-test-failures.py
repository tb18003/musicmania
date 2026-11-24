"""Turns failed JUnit test cases into GitHub Actions error annotations.

Annotations are readable on the pull request (and through the public API) without opening the
job logs. Usage: python3 report-test-failures.py <directory with build/test-results>
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

MAX_ANNOTATIONS = 30
MAX_MESSAGE = 400


def escape(text: str) -> str:
    """Workflow-command escaping for annotation messages."""
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def main(root: str) -> None:
    pattern = os.path.join(root, "**", "build", "test-results", "**", "*.xml")
    reported = 0
    for report in sorted(glob.glob(pattern, recursive=True)):
        for case in ET.parse(report).getroot().iter("testcase"):
            for problem in case.findall("failure") + case.findall("error"):
                if reported >= MAX_ANNOTATIONS:
                    return
                detail = (problem.get("message") or problem.text or "no message").strip()
                title = f"{case.get('classname')} > {case.get('name')}"
                print(f"::error title={escape(title)}::{escape(detail[:MAX_MESSAGE])}")
                reported += 1
    if reported == 0:
        print("No failed test cases found in JUnit reports (the failure happened outside the tests).")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
