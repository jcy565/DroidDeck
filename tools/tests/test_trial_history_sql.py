"""Host SQLite regression for the production trial schema and comparison query.

This checks SQL behavior, not Android SQLiteOpenHelper or Compose lifecycle.
"""
import re
import sqlite3
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = (ROOT / 'app/src/main/java/com/droiddeck/launcher/ui/GamePerformanceTrial.kt').read_text()
CREATE = re.search(r'db\.execSQL\("""(CREATE TABLE trial .*?)"""\)', SOURCE, re.S).group(1)
ADD_SCENE = re.search(r'db\.execSQL\("(ALTER TABLE trial ADD COLUMN scene TEXT)"\)', SOURCE).group(1)
ADD_COVERAGE = re.search(r'db\.execSQL\("(ALTER TABLE trial ADD COLUMN coverage_version [^"]+)"\)', SOURCE).group(1)
COMPARE = re.search(r'db\.rawQuery\("(SELECT fps, p95, gpu, driver, proton FROM trial [^"]+)"', SOURCE).group(1)
PRUNE = re.search(r'db\.execSQL\("(DELETE FROM trial [^"]+)"\)', SOURCE).group(1)
LEGACY = re.sub(r',\s*coverage_version INTEGER NOT NULL DEFAULT 0', '', CREATE)


class TrialHistorySqlTest(unittest.TestCase):
    def insert(self, db, fps=60, **overrides):
        fields = dict(game='steam:1', model='device', scene='menu', width=1280,
                      height=720, recorded=1, frames=600, fps=fps, p95=20)
        fields.update(overrides)
        db.execute('INSERT INTO trial (' + ','.join(fields) + ') VALUES (' +
                   ','.join('?' for _ in fields) + ')', tuple(fields.values()))

    def compare(self, db):
        return db.execute(COMPARE, ('steam:1', 'device', 'menu', 1280, 720)).fetchone()

    def test_v1_and_v2_migrate_without_comparing_legacy_samples(self):
        for version in (1, 2):
            with self.subTest(version=version), sqlite3.connect(':memory:') as db:
                db.execute(LEGACY.replace('scene TEXT, ', '') if version == 1 else LEGACY)
                if version == 1:
                    db.execute("INSERT INTO trial (game, model, recorded, frames, fps, p95) VALUES ('steam:1','device',1,2,900,1)")
                    db.execute(ADD_SCENE)
                    db.execute("UPDATE trial SET scene='menu', width=1280, height=720")
                else:
                    self.insert(db, fps=900, frames=2, p95=1)
                db.execute(ADD_COVERAGE)
                self.assertEqual([(900.0, 0)], db.execute('SELECT fps, coverage_version FROM trial').fetchall())
                self.assertIsNone(self.compare(db))
                self.insert(db, coverage_version=1)
                self.assertEqual((60.0, 20.0, None, None, None), self.compare(db))
                self.assertEqual(2, db.execute('SELECT COUNT(*) FROM trial').fetchone()[0])

    def test_fresh_schema_matches_only_latest_verified_compatible_sample(self):
        with sqlite3.connect(':memory:') as db:
            db.execute(CREATE)
            self.assertIsNone(self.compare(db))
            self.insert(db, coverage_version=1, fps=30)
            self.insert(db, coverage_version=1, fps=50)
            for fields in (dict(game='steam:2'), dict(model='other'), dict(scene='battle'),
                           dict(width=1920), dict(height=1080)):
                self.insert(db, coverage_version=1, fps=100, **fields)
            self.insert(db, fps=900)
            self.insert(db, fps=120, coverage_version=2)
            self.assertEqual((50.0, 20.0, None, None, None), self.compare(db))
            self.assertEqual(9, db.execute('SELECT COUNT(*) FROM trial').fetchone()[0])

    def test_comparison_returns_previous_runtime_metadata_even_when_settings_change(self):
        with sqlite3.connect(':memory:') as db:
            db.execute(CREATE)
            self.insert(db, coverage_version=1, fps=40, gpu='Adreno', driver='Turnip A', proton='GE A')
            self.insert(db, coverage_version=0, fps=900, driver='invalid baseline')
            self.assertEqual((40.0, 20.0, 'Adreno', 'Turnip A', 'GE A'), self.compare(db))
            self.insert(db, coverage_version=1, fps=60, gpu='Adreno', driver='Turnip B', proton='GE B')
            self.assertEqual((60.0, 20.0, 'Adreno', 'Turnip B', 'GE B'), self.compare(db))

    def test_existing_retention_limit_remains_500(self):
        with sqlite3.connect(':memory:') as db:
            db.execute(CREATE)
            for i in range(503):
                self.insert(db, coverage_version=i % 2, fps=i + 1)
            db.execute(PRUNE)
            self.assertEqual((500, 4, 503), db.execute('SELECT COUNT(*), MIN(id), MAX(id) FROM trial').fetchone())
            self.assertEqual((502.0, 20.0, None, None, None), self.compare(db))


if __name__ == '__main__':
    unittest.main()
