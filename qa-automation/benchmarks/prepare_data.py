"""Download original NRP files and create explicitly labelled Smart Shift projections.

No database access. See EXPERIMENT_PLAN.md for the constraints that are not preserved.
"""
import argparse
import datetime as dt
from decimal import Decimal, ROUND_HALF_UP
import hashlib
import json
from pathlib import Path
import random
import subprocess
import urllib.request

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = 'https://www.schedulingbenchmarks.org/nrp/data/Instance{}.txt'
REVISION = '0d9bf086e1b66dcc5b353ac8cdc1f9b6781f8234'
START = dt.datetime(2030, 1, 7, tzinfo=dt.timezone.utc)  # Monday; solver has no wall-date dependency.
VERSION = 'smartshift-projection-v1'


def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')


def rounded(value):
    return int(Decimal(str(value)).quantize(Decimal('1'), rounding=ROUND_HALF_UP))


def parse_nrp(text):
    sections, section = {}, None
    for original in text.splitlines():
        line = original.split('#', 1)[0].strip()
        if not line:
            continue
        if line.startswith('SECTION_'):
            section = line
            sections[section] = []
        elif section:
            sections[section].append([item.strip() for item in line.split(',')])
    required = {'SECTION_HORIZON', 'SECTION_SHIFTS', 'SECTION_STAFF', 'SECTION_COVER'}
    if not required <= sections.keys():
        raise ValueError('Unsupported NRP document: required sections missing')
    return sections


def demand(shift_id, day, start_minute, minutes, required, priority=1):
    start = START + dt.timedelta(days=day, minutes=start_minute)
    return dict(shiftId=shift_id, start=start.isoformat(), end=(start+dt.timedelta(minutes=minutes)).isoformat(),
                hours=rounded(Decimal(minutes)*100/60), missing=required, priority=priority)


def project(sections, number, checksum):
    days = int(sections['SECTION_HORIZON'][0][0])
    shifts = {row[0]: int(row[1]) for row in sections['SECTION_SHIFTS']}
    staff = sections['SECTION_STAFF']
    if any(length <= 0 or length > 1440 for length in shifts.values()):
        raise ValueError('Shift duration outside Smart Shift domain')
    workers, allowed = [], []
    for i, row in enumerate(staff):
        caps = dict((key, int(value)) for key, value in (item.split('=') for item in row[1].split('|')))
        # Weekly average of original horizon limits; deliberately not the original total-minute rule.
        maximum = min(16800, rounded(Decimal(row[2])*7*100/(60*days)))
        minimum = min(maximum, rounded(Decimal(row[3])*7*100/(60*days)))
        workers.append(dict(id=i+1, minimumHours=minimum, maximumHours=maximum,
                            dailyHours=rounded(Decimal(max(shifts.values()))*100/60),
                            restSeconds=11*3600, consecutiveDays=int(row[4]), fairnessGroup=f'NRP:{maximum}'))
        allowed.append(caps)
    off = {row[0]: {int(x) for x in row[1:] if x} for row in sections.get('SECTION_DAYS_OFF', [])}
    preferred = {(row[0], int(row[1]), row[2]) for row in sections.get('SECTION_SHIFT_ON_REQUESTS', [])
                 if int(row[3]) > 0}
    demands, options, labels = [], [], []
    shift_order = list(shifts)
    for row in sections['SECTION_COVER']:
        day, kind, required = int(row[0]), row[1], int(row[2])
        if required <= 0:
            continue
        d = len(demands)
        # The source has lengths and succession bans, not real clock times.
        start_minute = (360 + shift_order.index(kind)*1440//len(shifts)) % 1440
        demands.append(demand(d+1, day, start_minute, shifts[kind], required))
        labels.append(dict(day=day, sourceShift=kind))
        for w, person in enumerate(staff):
            if day in off.get(person[0], set()) or allowed[w].get(kind, 0) == 0:
                continue
            hours = demands[d]['hours']
            if hours > workers[w]['maximumHours'] or hours > workers[w]['dailyHours']:
                continue
            options.append(dict(worker=w, demand=d, preferred=(person[0], day, kind) in preferred,
                                dailyBase=0, weeklyBase=0, costThousands=0))
    return dict(schemaVersion=1, name=f'nrp{number:02d}_{VERSION}', workers=workers, demands=demands,
                options=options, fixedAssignments=[], zone='UTC', weeklyBudgetThousands=None,
                metadata=dict(kind='PUBLIC_DATA_PROJECTION', version=VERSION, sourceUrl=SOURCE.format(number),
                              rawSha256=checksum, sourceEmployees=len(staff), sourceDays=days,
                              sourceShiftTypes=len(shifts), employeeLabels=[r[0] for r in staff], demandLabels=labels,
                              syntheticClockTimes=True, syntheticRestHours=11,
                              comparableToOriginalBestKnown=False,
                              unsupported=['positive per-type shift-count caps', 'forbidden shift succession',
                                           'minimum consecutive working days', 'minimum consecutive days off',
                                           'maximum weekends', 'original request weights and shift-off penalties',
                                           'horizon-total minute bounds (replaced by weekly averages)',
                                           'original weighted under/over-cover objective'],
                              transformations=['positive on-request becomes PREFERRED boolean',
                                               'costs disabled because the source has no wages',
                                               'coverage capped at requirement; priority=1',
                                               'one-shift-per-day is not separately encoded; Smart Shift daily/rest rules apply']))


def synthetic(count, days, kind, seed=42):
    rng = random.Random(seed)
    workers, positions, rates = [], [], []
    for i in range(count):
        position = i % 3
        part = i % 4 == 3
        workers.append(dict(id=i+1, minimumHours=1600 if part else 3200,
                            maximumHours=2400 if part else 4000, dailyHours=800,
                            restSeconds=11*3600, consecutiveDays=5,
                            fairnessGroup=f'{position}:{"PART_TIME" if part else "FULL_TIME"}'))
        positions.append(position)
        rates.append(rng.choice([25, 30, 35]))  # thousands VND/hour
    demands, options = [], []
    for day in range(days):
        for shift in range(3):
            for position in range(3):
                d = len(demands)
                needed = max(1, rounded(count/20))
                if kind == 'shortage':
                    needed = max(1, rounded(count/10))
                demands.append(demand(d+1, day, (360+shift*480) % 1440, 480, needed, position+1))
                # Break 30 minutes. Absolute end time remains 8 hours after start.
                demands[-1]['hours'] = 750
                for w in range(count):
                    if positions[w] != position or rng.random() < (0.35 if kind == 'tight' else 0.12):
                        continue
                    options.append(dict(worker=w, demand=d, preferred=rng.random() < 0.3,
                                        dailyBase=0, weeklyBase=0, costThousands=rounded(Decimal(rates[w])*Decimal('7.5'))))
    return dict(schemaVersion=1, name=f'synthetic_{count}_{days}_{kind}_s{seed}', workers=workers, demands=demands,
                options=options, fixedAssignments=[], zone='UTC', weeklyBudgetThousands=rounded(count*40*30*0.8),
                metadata=dict(kind='SYNTHETIC', seed=seed, employees=count, days=days, scenario=kind,
                              rawSha256=None, comparableToOriginalBestKnown=False,
                              note='Normalized solver-input fixture; options represent prevalidated availability/position eligibility'))


def prepare(instance_ids, include_synthetic):
    registry = {'preparedAt': dt.datetime.now(dt.timezone.utc).isoformat(), 'files': [],
                'sourceIndex': 'https://www.schedulingbenchmarks.org/nrp/index.html',
                'formatGuide': 'https://www.schedulingbenchmarks.org/nrp/instances1_24.html'}
    for number in instance_ids:
        path = HERE / f'data/raw/Instance{number}.txt'
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.exists():
            request = urllib.request.Request(SOURCE.format(number), headers={'User-Agent': 'SmartShiftAcademicBenchmark/1.0'})
            with urllib.request.urlopen(request, timeout=60) as response:
                raw = response.read()
            parse_nrp(raw.decode('utf-8-sig'))  # refuse HTML/error pages before caching
            path.write_bytes(raw)
        raw = path.read_bytes()
        sha = hashlib.sha256(raw).hexdigest()
        normalized = project(parse_nrp(raw.decode('utf-8-sig')), number, sha)
        output = HERE / f'data/normalized/{normalized["name"]}.json'
        dump(output, normalized)
        registry['files'].append(dict(name=normalized['name'], sourceUrl=SOURCE.format(number), rawSha256=sha,
                                      employees=len(normalized['workers']), demands=len(normalized['demands']),
                                      options=len(normalized['options']), normalizedFile=output.relative_to(HERE).as_posix()))
        print(f'Prepared {normalized["name"]}: {len(normalized["workers"])} employees / {len(normalized["options"])} options', flush=True)
    if include_synthetic:
        for count, days in [(30, 14), (100, 14), (300, 28)]:
            for kind in ('balanced', 'shortage', 'tight'):
                normalized = synthetic(count, days, kind)
                output = HERE / f'data/normalized/{normalized["name"]}.json'
                dump(output, normalized)
                registry['files'].append(dict(name=normalized['name'], sourceUrl=None,
                                              employees=count, demands=len(normalized['demands']), options=len(normalized['options']),
                                              normalizedFile=output.relative_to(HERE).as_posix()))
    source_path = 'smart-shift-be/src/main/java/com/smartshift/service/impl/AutoScheduleServiceImpl.java'
    original = subprocess.check_output(['git', 'show', f'{REVISION}:{source_path}'], cwd=ROOT)
    target = HERE / 'reference/AutoScheduleServiceImpl.greedy.java.txt'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(original)
    registry['greedyReference'] = dict(revision=REVISION, repositoryPath=source_path,
                                     sha256=hashlib.sha256(original).hexdigest())
    dump(HERE / 'data/manifest.json', registry)
    return registry


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--instances', default='1,2,3,8,11,13')
    parser.add_argument('--no-synthetic', action='store_true')
    args = parser.parse_args()
    prepare([int(v) for v in args.instances.split(',')], not args.no_synthetic)
