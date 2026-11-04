"""Run isolated JVM comparisons and independently audit the returned schedules."""
import argparse
from collections import defaultdict
import csv
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import platform
import random
import shutil
import statistics
import subprocess
import time

from evaluate import evaluate
from prepare_data import HERE, ROOT, dump

ALGORITHMS = ['GREEDY_SHARED_CONSTRAINTS', 'CP_SAT', 'ALNS_CP_SAT']
METRICS = ['missing', 'priorityMissing', 'softPenalty', 'coveragePercent', 'preferredPercent',
           'costVnd', 'minimumDeficitHours', 'overtimeHours', 'fairnessUtilizationStd',
           'solverWallMillis', 'processWallMillis', 'iterations']


def java_home():
    configured = os.environ.get('JAVA_HOME')
    if configured and (Path(configured)/'bin'/('java.exe' if os.name == 'nt' else 'java')).exists():
        return Path(configured)
    executable = shutil.which('java')
    if executable:
        return Path(executable).resolve().parents[1]
    candidates = sorted((Path.home()/'.jdks').glob('*21*'))
    if candidates:
        return candidates[-1]
    raise RuntimeError('Set JAVA_HOME to a JDK 17+ installation')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def summarize(rows):
    groups = defaultdict(list)
    for row in rows:
        groups[row['dataset'], row['timeLimitSeconds'], row['algorithm']].append(row)
    summary = []
    for (dataset, seconds, algorithm), entries in sorted(groups.items()):
        good = [r for r in entries if r['valid'] and r['status'] != 'ERROR']
        record = dict(dataset=dataset, timeLimitSeconds=seconds, algorithm=algorithm,
                      runs=len(entries), validRuns=len(good), failures=len(entries)-len(good))
        for metric in METRICS:
            values = [r[metric] for r in good if metric in r]
            record[metric+'Mean'] = statistics.mean(values) if values else None
            record[metric+'Std'] = statistics.stdev(values) if len(values) > 1 else (0 if values else None)
        summary.append(record)
    return summary


def write_csv(path, records):
    keys = list(dict.fromkeys(k for r in records for k in r))
    with path.open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=keys)
        writer.writeheader()
        writer.writerows(records)


def comparisons(rows):
    """Paired lexicographic outcomes, never treat a failed run as a good empty schedule."""
    results = []
    cases = sorted({(r['dataset'], r['timeLimitSeconds']) for r in rows})
    for dataset, seconds in cases:
        entries = [r for r in rows if (r['dataset'], r['timeLimitSeconds']) == (dataset, seconds)]
        for left, right in [('ALNS_CP_SAT', 'GREEDY_SHARED_CONSTRAINTS'),
                            ('CP_SAT', 'GREEDY_SHARED_CONSTRAINTS'), ('ALNS_CP_SAT', 'CP_SAT')]:
            record = dict(dataset=dataset, timeLimitSeconds=seconds, left=left, right=right,
                          wins=0, ties=0, losses=0, failedPairs=0)
            for a in [r for r in entries if r['algorithm'] == left]:
                b = next((r for r in entries if r['algorithm'] == right and
                          (right == ALGORITHMS[0] or r['seed'] == a['seed'])), None)
                if not b or not a['valid'] or not b['valid']:
                    record['failedPairs'] += 1
                    continue
                score_a = tuple(a[k] for k in ('missing', 'priorityMissing', 'softPenalty'))
                score_b = tuple(b[k] for k in ('missing', 'priorityMissing', 'softPenalty'))
                record['wins' if score_a < score_b else ('losses' if score_a > score_b else 'ties')] += 1
            results.append(record)
    return results


def report(folder, summary, hybrid_iterations=1000):
    text = ['# Kết quả benchmark Smart Shift', '',
            'Kết quả trên dữ liệu đã chuyển đổi sang mô hình Smart Shift; không phải điểm chuẩn NRP gốc.',
            'Chỉ so sánh chất lượng giữa các lịch hợp lệ. Ưu tiên: thiếu người → thiếu theo ưu tiên → soft penalty.',
            'Soft penalty thấp hơn tốt hơn khi hai tiêu chí trước bằng nhau. Mỗi ô là trung bình các lần chạy hợp lệ.', '',
            '| Dữ liệu | Giây | Thuật toán | Hợp lệ/tổng | Thiếu | Phủ (%) | Soft penalty | Thời gian giải (ms) |',
            '|---|---:|---|---:|---:|---:|---:|---:|']
    for row in summary:
        def fmt(key, digits=1):
            value = row.get(key+'Mean')
            return f'{value:,.{digits}f}' if value is not None else '—'
        text.append(f"| {row['dataset']} | {row['timeLimitSeconds']:g} | {row['algorithm']} | "
                    f"{row['validRuns']}/{row['runs']} | {fmt('missing')} | {fmt('coveragePercent')} | "
                    f"{fmt('softPenalty', 0)} | {fmt('solverWallMillis')} |")
    text += ['', 'Greedy là bản tái dựng thứ tự/chấm điểm cũ, áp dụng chung ràng buộc cứng; không chạy lại dịch vụ cũ nguyên trạng.',
             'Greedy chạy một lần mỗi dữ liệu/ngân sách vì không dùng seed. Hai bộ giải chạy từng seed đã cấu hình.',
             f'CP-SAT chạy một luồng. Hybrid cấu hình tối đa {hybrid_iterations} vòng; ứng dụng mặc định 20 vòng.',
             'Thời gian giải bao gồm dựng mô hình/tìm kiếm; processWallMillis còn gồm khởi động JVM và nạp native.',
             'Giới hạn thời gian tìm kiếm không phải hạn chót tuyệt đối của tiến trình. Xem CSV để biết mức vượt thực tế.',
             'Không suy ra hybrid luôn tốt hơn CP-SAT hoặc tối ưu toàn cục từ bảng này.', '',
             'Chi tiết từng lần chạy: `runs.csv`; trung bình/độ lệch chuẩn: `summary.csv`; so sánh từng cặp seed: `comparisons.csv`; cấu hình và checksum: `run.json`.', '']
    (folder/'REPORT.md').write_text('\n'.join(text), encoding='utf-8')


def run(args):
    home = None if args.docker else java_home()
    backend = ROOT/'smart-shift-be'
    if args.build and args.docker:
        subprocess.run(['docker', 'build', '-f', str(HERE/'Dockerfile'), '-t', args.docker_image, str(backend)], check=True)
    elif args.build:
        env = os.environ | {'JAVA_HOME': str(home)}
        command = ['mvnw.cmd' if os.name == 'nt' else './mvnw', '-B', '-DskipTests', 'test-compile',
                   'dependency:build-classpath', '-Dmdep.outputFile=target/benchmark-classpath.txt']
        if os.name == 'nt':
            command = ['cmd', '/d', '/c'] + command
        subprocess.run(command, cwd=backend, env=env, check=True)
    classpath_file = backend/'target/benchmark-classpath.txt'
    if not args.docker and (not classpath_file.exists() or not (backend/'target/test-classes/com/smartshift/benchmark/BenchmarkCli.class').exists()):
        raise RuntimeError('Compile first: rerun with --build')
    paths = sorted((HERE/'data/normalized').glob('*.json'))
    patterns = args.datasets.split(',')
    paths = [p for p in paths if any(p.stem.startswith(pattern) for pattern in patterns)]
    if not paths or any(not any(p.stem.startswith(pattern) for p in paths) for pattern in patterns):
        raise ValueError('Dataset prefix not found; run prepare_data.py first')
    budgets = [float(x) for x in args.seconds.split(',')]
    seeds = [int(x) for x in args.seeds.split(',')]
    if not seeds or len(set(seeds)) != len(seeds) or min(seeds) < 0:
        raise ValueError('Seeds must be unique nonnegative integers')
    if any(not 0.1 <= s <= 60 for s in budgets) or len(set(budgets)) != len(budgets):
        raise ValueError('Time budgets must be unique and in [0.1, 60] seconds')
    if not 1 <= args.hybrid_iterations <= 1000:
        raise ValueError('Hybrid iterations must be in [1, 1000]')
    folder = HERE/'results'/dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    folder.mkdir(parents=True)
    argfile = folder/'java.args'
    if not args.docker:
        classpath = os.pathsep.join([str(backend/'target/test-classes'), str(backend/'target/classes'),
                                   classpath_file.read_text(encoding='utf-8').strip()])
        argfile.write_text('-cp\n"'+classpath.replace('\\', '/')+'"\ncom.smartshift.benchmark.BenchmarkCli\n', encoding='utf-8')
    executable = str(home/'bin'/('java.exe' if os.name == 'nt' else 'java')) if home else None
    jobs = [(p, seconds, algorithm, seed) for p in paths for seconds in budgets for algorithm in ALGORITHMS
            for seed in ([seeds[0]] if algorithm == ALGORITHMS[0] else seeds)]
    random.Random(args.order_seed).shuffle(jobs)
    source_files = [backend/'src/main/java/com/smartshift/scheduling/HybridScheduleOptimizer.java',
                    backend/'src/main/java/com/smartshift/scheduling/SchedulingProblem.java', backend/'pom.xml',
                    backend/'src/test/java/com/smartshift/benchmark/BenchmarkCli.java',
                    HERE/'prepare_data.py', HERE/'evaluate.py', HERE/'run_benchmarks.py']
    metadata = dict(startedAt=dt.datetime.now(dt.timezone.utc).isoformat(), platform=platform.platform(),
                    processor=platform.processor(), logicalCpuCount=os.cpu_count(), javaHome=str(home),
                    budgets=budgets, seeds=seeds, orderSeed=args.order_seed,
                    datasets={p.name: sha(p) for p in paths}, sources={p.name: sha(p) for p in source_files},
                    dependencyClasspathSha256=sha(classpath_file) if not args.docker else None,
                    datasetManifest=json.loads((HERE/'data/manifest.json').read_text(encoding='utf-8')),
                    greedyRunsPerCase=1, cpSatSearchWorkers=1, hybridMaxIterations=args.hybrid_iterations,
                    timing='solver: model/search incl. Java class initialization, excludes native loading; process: entire JVM',
                    jobs=len(jobs))
    if args.docker:
        metadata['dockerImage'] = args.docker_image
        metadata['dockerInspect'] = json.loads(subprocess.check_output(
            ['docker', 'image', 'inspect', args.docker_image, '--format', '{{json .}}'], text=True))
        # Keep reproducibility fields, omit arbitrary image environment variables from the report.
        metadata['dockerInspect'] = {k: metadata['dockerInspect'].get(k) for k in ('Id', 'Architecture', 'Os', 'Created')}
        metadata['dockerInfo'] = json.loads(subprocess.check_output(
            ['docker', 'info', '--format', '{{json .}}'], text=True))
        metadata['dockerInfo'] = {k: metadata['dockerInfo'].get(k) for k in ('NCPU', 'MemTotal', 'OperatingSystem', 'ServerVersion')}
        metadata['containerCpuLimit'] = 1
    dump(folder/'run.json', metadata)
    rows = []
    print(f'Results: {folder}', flush=True)
    for index, (path, seconds, algorithm, seed) in enumerate(jobs, 1):
        stem = f'{path.stem}_{seconds:g}s_{algorithm}_s{seed}'
        output = folder/(stem+'.json')
        if args.docker:
            command = ['docker', 'run', '--rm', '--network', 'none', '--cpus', '1',
                       '--mount', f'type=bind,source={path.parent},target=/data,readonly',
                       '--mount', f'type=bind,source={folder},target=/results', args.docker_image,
                       '/data/'+path.name, algorithm, str(seconds), str(seed), '/results/'+output.name,
                       str(args.hybrid_iterations)]
        else:
            command = [executable, '@'+str(argfile), str(path), algorithm, str(seconds), str(seed), str(output),
                       str(args.hybrid_iterations)]
        started = time.perf_counter()
        with (folder/(stem+'.log')).open('w', encoding='utf-8') as log:
            try:
                proc = subprocess.run(command,
                                      stdout=log, stderr=subprocess.STDOUT, timeout=seconds+180)
                raw = json.loads(output.read_text(encoding='utf-8')) if output.exists() and proc.returncode == 0 else dict(
                    status='ERROR', error=f'JVM exit {proc.returncode}; see log', selectedOptions=[])
            except subprocess.TimeoutExpired:
                raw = dict(status='ERROR', error='JVM timeout; see log', selectedOptions=[])
        process_ms = (time.perf_counter()-started)*1000
        problem = json.loads(path.read_text(encoding='utf-8'))
        metrics = evaluate(problem, raw.get('selectedOptions', []))
        if raw['status'] == 'ERROR':
            metrics['valid'] = False
        row = dict(dataset=path.stem, dataKind=problem['metadata']['kind'], employees=len(problem['workers']),
                   timeLimitSeconds=seconds, algorithm=algorithm, seed=seed, status=raw['status'],
                   **{k: v for k, v in metrics.items() if k != 'violations'},
                   solverWallMillis=raw.get('solverWallMillis'), processWallMillis=process_ms,
                   iterations=raw.get('iterations', 0), error=raw.get('error', ''),
                   hardViolationDetails='; '.join(metrics['violations']))
        rows.append(row)
        dump(folder/(stem+'.audit.json'), metrics)
        write_csv(folder/'runs.csv', rows)
        print(f"[{index}/{len(jobs)}] {path.stem} {algorithm} seed={seed}: {raw['status']}, "
              f"missing={metrics.get('missing', '?')}, violations={metrics['hardViolations']}", flush=True)
    summary = summarize(rows)
    write_csv(folder/'summary.csv', summary)
    write_csv(folder/'comparisons.csv', comparisons(rows))
    report(folder, summary, args.hybrid_iterations)
    metadata['finishedAt'] = dt.datetime.now(dt.timezone.utc).isoformat()
    metadata['failedRuns'] = sum(not row['valid'] for row in rows)
    dump(folder/'run.json', metadata)
    print(f"Report: {folder/'REPORT.md'}", flush=True)
    if metadata['failedRuns']:
        raise SystemExit(f"{metadata['failedRuns']} runs failed or violated hard rules; see CSV and logs")
    return folder


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', action='store_true')
    parser.add_argument('--docker', action='store_true', help='Use isolated Linux JVM containers, no DB/network')
    parser.add_argument('--docker-image', default='smart-shift-benchmark:local')
    parser.add_argument('--datasets', default='nrp01,nrp03,synthetic_30_14_shortage')
    parser.add_argument('--seconds', default='2')
    parser.add_argument('--seeds', default='0,1,2')
    parser.add_argument('--hybrid-iterations', type=int, default=1000,
                        help='Use 20 together with --seconds 8 to test the application defaults')
    parser.add_argument('--order-seed', type=int, default=42)
    run(parser.parse_args())
