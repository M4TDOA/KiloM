"""Import the displayed kilometre grid, preserving saved-route location IDs.

Usage: python tools/import-grid.py source.xlsx [--check]
The source workbook is read only. Its 0.0 display format is authoritative.
"""
import hashlib
import json
from pathlib import Path
import sys
import unicodedata
import xml.etree.ElementTree as ET
from zipfile import ZipFile
from decimal import Decimal, ROUND_HALF_UP

NS = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
root = Path(__file__).resolve().parents[1]
target = root / 'app/src/main/assets/distances.json'
source = Path(sys.argv[1])
data = json.loads(target.read_text())

def key(name):
    return ''.join(c.lower() for c in unicodedata.normalize('NFD', name) if c.isalnum())

def column(n):
    result = ''
    while n:
        n, mod = divmod(n - 1, 26)
        result = chr(65 + mod) + result
    return result

with ZipFile(source) as archive:
    workbook = ET.fromstring(archive.read('xl/workbook.xml'))
    sheet = next(s for s in workbook.find('m:sheets', NS) if s.attrib['name'] == 'Grille de kilométrage')
    rel_id = sheet.attrib['{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id']
    rels = ET.fromstring(archive.read('xl/_rels/workbook.xml.rels'))
    path = next(r.attrib['Target'] for r in rels if r.attrib['Id'] == rel_id)
    xml = ET.fromstring(archive.read(path.lstrip('/') if path.startswith('/') else 'xl/' + path))
    strings = [''.join(t.itertext()) for t in ET.fromstring(archive.read('xl/sharedStrings.xml'))]
    cells = {c.attrib['r']: c for c in xml.findall('.//m:c', NS)}
    styles = ET.fromstring(archive.read('xl/styles.xml'))
    formats = {f.attrib['numFmtId']: f.attrib['formatCode'] for f in styles.find('m:numFmts', NS)}
    xfs = styles.find('m:cellXfs', NS)
    def label(ref):
        cell = cells[ref]
        assert cell.attrib.get('t') == 's', ref
        return strings[int(cell.find('m:v', NS).text)]
    count = len(data['names'])
    assert count == 47
    for i, name in enumerate(data['names']):
        assert key(name) == key(label(f'A{i+13}')), (i, name, 'row')
        assert key(name) == key(label(f'{column(i+2)}12')), (i, name, 'column')
    matrix = []
    for i in range(count):
        row = []
        for j in range(count):
            ref = f'{column(j+2)}{i+13}'
            cell = cells[ref]
            fmt = formats[xfs[int(cell.attrib['s'])].attrib['numFmtId']]
            assert fmt == '0.0', (ref, fmt)
            # Remove Excel binary floating-point noise before decimal half-up rounding.
            raw = Decimal(cell.find('m:v', NS).text).quantize(Decimal('0.000001'))
            value = float(raw.quantize(Decimal('0.1'), rounding=ROUND_HALF_UP))
            assert value >= 0
            row.append(value)
        matrix.append(row)

# Explicit user corrections take precedence over the supplied workbook.
# Preserve them on every import so rebuilding never silently restores a known error.
overrides = data.get('source', {}).get('overrides', [])
for item in overrides:
    a, b, value = item['from'], item['to'], item['km']
    assert 0 <= a < count and 0 <= b < count and 0 <= value <= 10000
    matrix[a][b] = value
    if item.get('bidirectional'):
        matrix[b][a] = value
changed = sum(abs(matrix[i][j] - data['matrix'][i][j]) > 1e-9
              for i in range(count) for j in range(count))

if '--check' in sys.argv:
    assert changed == 0, f'{changed} distances differ from displayed grid'
    print(f'PASS: 2209 distances, 47 stable IDs; {len(overrides)} explicit user correction(s) applied')
else:
    data['matrix'] = matrix
    data['source'] = {'sheet': 'Grille de kilométrage', 'range': 'B13:AV59',
                      'precision': 1, 'rounding': 'displayed 0.0, half-up per leg',
                      'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                      'overrides': overrides}
    target.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n')
    print(f'Imported 2209 distances; {changed} changed; location IDs preserved')
