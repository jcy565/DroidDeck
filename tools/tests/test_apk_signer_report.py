import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'ci'))
from apk_signer_report import require_certificate, single_certificate

CERT = 'a' * 64
V2 = 'Verified using v2 scheme (APK Signature Scheme v2): true\n'
LEGACY = f'Signer #1 certificate SHA-256 digest: {CERT}\n'
VERSIONED = f'Number of signers: 1\nV3.0 Signer: certificate SHA-256 digest: {CERT}\n'


class SignerReportTest(unittest.TestCase):
    def test_supported_reports(self):
        for report in (LEGACY, VERSIONED, VERSIONED.replace('V3.0', 'V3.1')):
            with self.subTest(report=report):
                self.assertEqual(CERT, single_certificate(report))
                require_certificate(V2 + report, CERT)

    def test_ambiguous_or_missing_signers_rejected(self):
        for report in ('', LEGACY + LEGACY, VERSIONED + LEGACY,
                       VERSIONED.replace('signers: 1', 'signers: 2'),
                       VERSIONED.split('\n', 1)[1], LEGACY.replace('#1', '#2'),
                       VERSIONED.replace(CERT, 'bad'),
                       LEGACY + 'Number of signers: 2\n'):
            with self.subTest(report=report), self.assertRaises(ValueError):
                single_certificate(report)

    def test_v2_and_expected_digest_required(self):
        for report, expected in ((VERSIONED, CERT), (V2 + VERSIONED, 'b' * 64)):
            with self.subTest(report=report), self.assertRaises(ValueError):
                require_certificate(report, expected)
