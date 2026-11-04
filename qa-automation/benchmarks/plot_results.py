"""Render standalone research figures from summary.csv (requires matplotlib)."""
import argparse
import csv
import hashlib
import json
from pathlib import Path

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt


def plot(folder):
    with (folder/'summary.csv').open(encoding='utf-8-sig', newline='') as stream:
        rows = list(csv.DictReader(stream))
    cases = sorted({(r['dataset'], r['timeLimitSeconds']) for r in rows})
    algorithms = ['GREEDY_SHARED_CONSTRAINTS', 'CP_SAT', 'ALNS_CP_SAT']
    labels = ['Greedy (shared rules)', 'CP-SAT', 'ALNS + CP-SAT']
    metrics = [('missing', 'Unfilled slots (lower is better)'),
               ('softPenalty', 'Soft penalty (compare after coverage and priority shortages tie)'),
               ('solverWallMillis', 'Model + search time of successful runs (ms)')]
    fig, axes = plt.subplots(len(metrics), 1, figsize=(max(9, len(cases)*2.4), 10), squeeze=False)
    for ax, (metric, title) in zip(axes[:, 0], metrics):
        for i, (algorithm, label) in enumerate(zip(algorithms, labels)):
            group = {(r['dataset'], r['timeLimitSeconds']): r for r in rows if r['algorithm'] == algorithm}
            xs, ys, errors = [], [], []
            for x, case in enumerate(cases):
                row = group.get(case)
                if row and row[metric+'Mean']:
                    xs.append(x + (i-1)*0.25)
                    ys.append(float(row[metric+'Mean']))
                    errors.append(float(row[metric+'Std']))
                elif row:
                    ax.text(x + (i-1)*0.25, 0, 'FAIL', rotation=90,
                            fontsize=7, color='crimson', ha='center', va='bottom')
            ax.bar(xs, ys, yerr=errors, width=0.24, label=label, capsize=3)
        ax.set_title(title)
        ax.set_xlim(-0.6, len(cases)-0.4)
        ax.set_xticks(range(len(cases)), [f'{d.replace("_smartshift-projection-v1", " (projection)")}\n{t}s'
                                         for d, t in cases], rotation=15, ha='right', fontsize=8)
        ax.grid(axis='y', alpha=0.2)
        ax.set_axisbelow(True)
    axes[0, 0].legend(fontsize=8)
    fig.suptitle('Smart Shift benchmark — mean ± sample SD; inspect failures in summary.csv')
    fig.tight_layout()
    for extension in ('svg', 'png'):
        fig.savefig(folder/f'comparison.{extension}', dpi=180)
    plt.close(fig)
    (folder/'plot-metadata.json').write_text(json.dumps(dict(
        matplotlibVersion=matplotlib.__version__,
        scriptSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        summarySha256=hashlib.sha256((folder/'summary.csv').read_bytes()).hexdigest()), indent=2), encoding='utf-8')
    print(folder/'comparison.svg')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('results_folder', type=Path)
    plot(parser.parse_args().results_folder.resolve())
