"""Offline configuration preview prototype. No file writes or network access."""
import json
import re
import argparse
from pathlib import Path

def preview(text, kind, key, value, section=None):
    if len(text) > 1024 * 1024:
        raise ValueError('Configuration too large')
    if kind == 'json':
        data = json.loads(text)
        if not isinstance(data, dict) or key not in data:
            raise ValueError('Select an existing top-level key')
        old = data[key]
        new = json.loads(value)
        if type(old) is not type(new) or isinstance(old, (dict, list)):
            raise ValueError('Only existing scalar values of the same type are supported')
        data[key] = new
        return json.dumps(data, ensure_ascii=False, indent=2) + '\n'
    if kind not in ('ini', 'cfg') or any(c in value for c in '\r\n\x00'):
        raise ValueError('Unsupported format or value')
    active = None
    matches = []
    lines = text.splitlines(keepends=True)
    for i, line in enumerate(lines):
        s = line.strip()
        if s.startswith('[') and s.endswith(']'):
            active = s[1:-1]
        elif not s.startswith(('#', ';')) and '=' in s:
            if s.split('=', 1)[0].strip() == key and (section is None or active == section):
                matches.append(i)
    if len(matches) != 1:
        raise ValueError('Setting must exist exactly once in selected scope')
    i = matches[0]
    m = re.fullmatch(r'([^=]*=)([^\r\n]*)(\r\n|\n|\r)?', lines[i])
    if ';' in m[2] or '#' in m[2]:
        raise ValueError('Inline comments require manual review')
    lines[i] = m[1] + value + (m[3] or '')
    return ''.join(lines)

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('file', type=Path)
    p.add_argument('key')
    p.add_argument('value')
    p.add_argument('--section')
    a = p.parse_args()
    if a.file.stat().st_size > 1024 * 1024:
        p.error('Configuration too large')
    with a.file.open(encoding='utf-8', newline='') as f:
        original = f.read()
    print(preview(original, a.file.suffix.lower()[1:], a.key, a.value, a.section), end='')
