"""Fail closed on missing JaCoCo evidence; summarize full-backend unit/combined coverage."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def read_report(path):
    root = ET.parse(path).getroot()
    counters = {}
    for counter in root.findall('counter'):
        missed, covered = int(counter.get('missed')), int(counter.get('covered'))
        total = missed + covered
        counters[counter.get('type')] = dict(covered=covered, missed=missed, total=total,
            ratio=covered / total if total else 1)
    if counters.get('LINE', {}).get('total', 0) == 0:
        raise ValueError('Coverage report has no production lines')
    return root, counters


def main(target, minimum):
    for name in ['jacoco.exec', 'jacoco-e2e.exec', 'jacoco-combined.exec']:
        path = target / name
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError('Missing execution evidence: ' + name)
    _, unit = read_report(target / 'site/jacoco-unit/jacoco.xml')
    root, combined = read_report(target / 'site/jacoco/jacoco.xml')
    if unit['LINE']['total'] != combined['LINE']['total']:
        raise ValueError('Unit and combined reports do not cover the same production code')
    packages = []
    for package in root.findall('package'):
        line = package.find("counter[@type='LINE']")
        if line is None: continue
        covered, missed = int(line.get('covered')), int(line.get('missed'))
        packages.append(dict(name=package.get('name').replace('/', '.'), covered=covered, missed=missed,
            ratio=covered/(covered+missed) if covered+missed else 1))
    result = dict(scope='All production backend classes; no configured package/class exclusions', unit=unit,
        combined=combined, packages=packages, gate=dict(counter='LINE', minimum=minimum, passed=combined['LINE']['ratio'] >= minimum))
    output = ROOT / 'qa-automation/results/coverage-summary.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(f"JUnit/context line coverage: {100*unit['LINE']['ratio']:.2f}%")
    print(f"Combined line coverage: {100*combined['LINE']['ratio']:.2f}% ({combined['LINE']['covered']}/{combined['LINE']['total']})")
    print(f"Combined branch coverage: {100*combined['BRANCH']['ratio']:.2f}%")
    print(f"Required combined line coverage: {100*minimum:.0f}%")
    assert result['gate']['passed'], 'Coverage quality gate failed'


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', type=Path, default=ROOT/'smart-shift-be/target')
    parser.add_argument('--minimum', type=float, default=0.71)
    args = parser.parse_args()
    main(args.target, args.minimum)
