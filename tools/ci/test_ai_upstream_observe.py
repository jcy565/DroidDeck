import subprocess
import tempfile
import unittest
from pathlib import Path

from ai_upstream_observe import observe


class ObserveTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name)
        self.git('init', '-q')
        self.git('config', 'user.name', 'Test')
        self.git('config', 'user.email', 'test@example.invalid')
        self.root = self.save('root')
        self.baseline = self.save('baseline')
        self.ai = self.save('ai')
        self.git('checkout', '-q', '--detach', self.baseline)
        self.upstream = self.save('upstream')
        self.policy = 'a' * 64

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.repo), *args]).decode().strip()

    def save(self, value):
        (self.repo / 'file').write_text(value)
        self.git('add', 'file')
        self.git('commit', '-qm', value)
        return self.git('rev-parse', 'HEAD')

    def test_new_update_is_read_only(self):
        refs, head = self.git('show-ref'), self.git('rev-parse', 'HEAD')
        r = observe(self.repo, self.baseline, self.ai, self.upstream, self.policy)
        self.assertEqual(r['status'], 'new_update')
        self.assertTrue(r['requires_preview'])
        self.assertFalse(r['release_approved'])
        self.assertFalse(r['last_good_changed'])
        self.assertEqual(self.git('show-ref'), refs)
        self.assertEqual(self.git('rev-parse', 'HEAD'), head)
        self.assertEqual(self.git('status', '--porcelain'), '')

    def test_unchanged(self):
        r = observe(self.repo, self.baseline, self.ai, self.baseline, self.policy)
        self.assertEqual(r['status'], 'unchanged')
        self.assertFalse(r['requires_preview'])

    def test_rewind_blocks(self):
        r = observe(self.repo, self.baseline, self.ai, self.root, self.policy)
        self.assertEqual(r['status'], 'history_review_required')
        self.assertFalse(r['requires_preview'])

    def test_repeat_is_advisory(self):
        first = observe(self.repo, self.baseline, self.ai, self.upstream, self.policy)
        repeat = observe(self.repo, self.baseline, self.ai, self.upstream, self.policy, first)
        self.assertTrue(repeat['repeat_observation'])
        self.assertTrue(repeat['requires_preview'])

    def test_policy_change_invalidates_repeat(self):
        first = observe(self.repo, self.baseline, self.ai, self.upstream, self.policy)
        new = observe(self.repo, self.baseline, self.ai, self.upstream, 'b' * 64, first)
        self.assertFalse(new['repeat_observation'])
        self.assertNotEqual(first['observation_key'], new['observation_key'])

    def test_invalid_pin_or_policy_rejected(self):
        with self.assertRaises(ValueError):
            observe(self.repo, self.baseline, 'HEAD', self.upstream, self.policy)
        with self.assertRaises(ValueError):
            observe(self.repo, self.baseline, self.ai, self.upstream, 'bad')


if __name__ == '__main__':
    unittest.main()
