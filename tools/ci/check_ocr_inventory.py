"""Check the packaged OCR profile against the requested Gradle variant."""
import argparse
import json
from pathlib import Path

PREFIX = 'assets/mlkit-google-ocr-models/'
LANGUAGES = ('Hani_ctc', 'Jpan_ctc', 'Latn_ctc')


def check(entries, enabled):
    if not isinstance(entries, dict) or not entries:
        raise ValueError('Missing APK inventory entries')
    models = {name for name in entries if name.startswith(PREFIX)}
    if not enabled:
        if models:
            raise ValueError('OCR models unexpectedly included in disabled variant')
        return
    for language in LANGUAGES:
        path = (PREFIX + 'gocr/gocr_models/line_recognition_legacy_mobile/'
                + language + '/optical/conv_model.fb')
        entry = entries.get(path)
        if not isinstance(entry, dict) or not isinstance(entry.get('size'), int) or entry['size'] <= 0:
            raise ValueError(f'Missing or empty OCR model: {language}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inventory', type=Path)
    parser.add_argument('enabled', choices=('true', 'false'))
    args = parser.parse_args()
    check(json.loads(args.inventory.read_text())['entries'], args.enabled == 'true')
    print('OCR inventory matches requested build profile')
