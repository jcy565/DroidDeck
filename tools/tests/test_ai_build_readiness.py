"""Static regression guards for the independent manual APK build route."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BRANCH = "ai-independent-apk-verification-next"


class IndependentBuildReadinessTest(unittest.TestCase):
    def setUp(self):
        self.workflow = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")
        self.gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

    def test_manual_only_and_ocr_enabled(self):
        push = re.search(r"(?m)^  push:\n    branches: \[([^\n]+)\]", self.workflow)
        self.assertIsNotNone(push)
        self.assertNotIn(BRANCH, [v.strip() for v in push.group(1).split(",")])
        self.assertIn("  workflow_dispatch:\n", self.workflow)
        ocr = re.search(r"ORG_GRADLE_PROJECT_experimentalImageOcr: .*?fromJSON\('([^']+)'\)", self.workflow)
        self.assertIsNotNone(ocr)
        self.assertIn(BRANCH, json.loads(ocr.group(1)))
        self.assertIn("getOrElse('false') == 'true'", self.gradle)
        self.assertRegex(self.gradle, r"experimentalLocalAi = providers\.gradleProperty\('experimentalLocalAi'\)\.getOrElse\('false'\)")
        self.assertIn('applicationId "com.droiddeck.launcher.ai"', self.gradle)

    def test_official_publish_guards_are_retained(self):
        for job in ("sign", "packs", "announce", "catalog"):
            block = re.search(rf"(?ms)^  {job}:\n(.*?)(?=^  [\w-]+:\n|\Z)", self.workflow)
            self.assertIsNotNone(block)
            guard = re.search(r"(?m)^    if: (.+)$", block.group(1))
            self.assertIsNotNone(guard)
            self.assertIn("github.ref == 'refs/heads/main'", guard.group(1))
            self.assertIn("github.event_name != 'pull_request'", guard.group(1))

    def test_failed_test_artifact_stays_diagnostic(self):
        step = self.workflow.split("      - name: Retain checked APK for failed test investigation\n", 1)[1].split("\n      #", 1)[0]
        self.assertIn(BRANCH, step)
        self.assertIn("failure() && steps.unit-tests.outcome == 'failure'", step)
        self.assertIn("name: diagnostic-apk-tests-failed", step)
        self.assertIn("Check human authorship", self.workflow)
        self.assertIn("No private key in the tree", self.workflow)


if __name__ == "__main__":
    unittest.main()
