"""Compare saved runs only when inputs and experiment settings match."""
import argparse
import csv
import json
from pathlib import Path

from run_benchmarks import write_csv


def compare(before_config, after_config, before_rows, after_rows):
    for key in ('datasets', 'budgets', 'seeds', 'hybridMaxIterations', 'cpSatSearchWorkers', 'containerCpuLimit'):
        if before_config.get(key) != after_config.get(key):
            raise ValueError(f'Cannot pair runs: {key} differs')

    def index(rows):
        indexed = {(r['dataset'], float(r['timeLimitSeconds']), r['algorithm'], int(r['seed'])): r for r in rows}
        if len(indexed) != len(rows):
            raise ValueError('Duplicate run key')
        return indexed

    before, after = index(before_rows), index(after_rows)
    if before.keys() != after.keys():
        raise ValueError('Cannot pair runs: run keys differ')
    records = []
    for key in sorted(before):
        a, b = before[key], after[key]
        valid_a = str(a['valid']).lower() == 'true' and a['status'] != 'ERROR'
        valid_b = str(b['valid']).lower() == 'true' and b['status'] != 'ERROR'
        if valid_a and valid_b:
            score_a = tuple(float(a[k]) for k in ('missing', 'priorityMissing', 'softPenalty'))
            score_b = tuple(float(b[k]) for k in ('missing', 'priorityMissing', 'softPenalty'))
            outcome = 'BETTER' if score_b < score_a else ('WORSE' if score_b > score_a else 'TIE')
        else:
            outcome = 'RECOVERED' if valid_b else ('FAILED' if valid_a else 'BOTH_FAILED')
        record = dict(dataset=key[0], timeLimitSeconds=key[1], algorithm=key[2], seed=key[3],
                      beforeStatus=a['status'], afterStatus=b['status'], outcome=outcome)
        for metric in ('missing', 'priorityMissing', 'softPenalty', 'solverWallMillis', 'processWallMillis', 'iterations'):
            # Failed schedules have no meaningful quality score, but elapsed time is still useful.
            timing = metric in ('solverWallMillis', 'processWallMillis')
            record['before_'+metric] = a.get(metric, '') if valid_a or timing else ''
            record['after_'+metric] = b.get(metric, '') if valid_b or timing else ''
        records.append(record)
    return records


def compare_folders(before, after):
    def read(folder):
        config = json.loads((folder/'run.json').read_text(encoding='utf-8'))
        with (folder/'runs.csv').open(encoding='utf-8-sig', newline='') as stream:
            return config, list(csv.DictReader(stream))
    a, rows_a = read(before)
    b, rows_b = read(after)
    output = after/'comparison-with-previous.csv'
    write_csv(output, compare(a, b, rows_a, rows_b))
    print(output)
    return output


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('before', type=Path)
    parser.add_argument('after', type=Path)
    args = parser.parse_args()
    compare_folders(args.before.resolve(), args.after.resolve())
