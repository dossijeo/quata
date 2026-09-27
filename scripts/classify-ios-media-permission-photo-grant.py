#!/usr/bin/env python3
import argparse
import json
import re
import sys

EXPECTED_TEST = "testGrantedPhotoReadWritePermissionReflectsSimulatorPrivacyState()"
EXPECTED_IDENTIFIER = f"IosMediaPermissionRuntimeTests/{EXPECTED_TEST}"
EXPECTED_FAILURE = re.compile(
    r"^IosMediaPermissionRuntimeTests\.swift:\d+: XCTAssertTrue failed - Expected Granted, received Denied$"
)


def collect_nodes(nodes):
    collected = []
    for node in nodes or []:
        collected.append(node)
        collected.extend(collect_nodes(node.get("children")))
    return collected


def classify(summary, tests):
    expected_summary = {
        "result": "Failed",
        "totalTestCount": 1,
        "failedTests": 1,
        "passedTests": 0,
        "skippedTests": 0,
        "expectedFailures": 0,
    }
    if any(summary.get(key) != value for key, value in expected_summary.items()):
        raise ValueError("photo_grant_unexpected_summary")

    failures = summary.get("testFailures", [])
    if len(failures) != 1 or failures[0] != {
        "targetName": "QuataIosTests",
        "testName": EXPECTED_TEST,
        "failureText": "XCTAssertTrue failed - Expected Granted, received Denied",
    }:
        raise ValueError("photo_grant_unexpected_summary_failure")

    nodes = collect_nodes(tests.get("testNodes"))
    cases = [node for node in nodes if node.get("nodeType") == "Test Case"]
    if len(cases) != 1 or any((
        cases[0].get("name") != EXPECTED_TEST,
        cases[0].get("nodeIdentifier") != EXPECTED_IDENTIFIER,
        cases[0].get("result") != "Failed",
    )):
        raise ValueError("photo_grant_unexpected_test_case")

    failure_messages = [node for node in nodes if node.get("nodeType") == "Failure Message"]
    if len(failure_messages) != 2 or any(
        EXPECTED_FAILURE.fullmatch(node.get("name", "")) is None for node in failure_messages
    ):
        raise ValueError("photo_grant_unexpected_failure_messages")

    return {
        "classification": "simulator_read_write_grant_unavailable",
        "expectedAssertions": 2,
        "unexpectedFailures": 0,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--summary", required=True)
    parser.add_argument("--tests", required=True)
    args = parser.parse_args()
    with open(args.summary, encoding="utf-8") as source:
        summary = json.load(source)
    with open(args.tests, encoding="utf-8") as source:
        tests = json.load(source)
    print(json.dumps(classify(summary, tests), indent=2))


if __name__ == "__main__":
    try:
        main()
    except ValueError as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
