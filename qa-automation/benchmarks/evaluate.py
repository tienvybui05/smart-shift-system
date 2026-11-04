"""Independent checks and metrics for empty-initial-schedule benchmark fixtures.

This does not import Java solver code. It checks the Smart Shift projection,
not the original NRP rules. Integer hours are hundredths of an hour.
"""
from collections import Counter, defaultdict
import datetime as dt
import math
import statistics


def instant(value):
    return dt.datetime.fromisoformat(value.replace('Z', '+00:00'))


def week(day):
    return day - dt.timedelta(days=day.weekday())


def evaluate(problem, selected):
    if problem['fixedAssignments'] or any(o['dailyBase'] or o['weeklyBase'] for o in problem['options']):
        raise ValueError('Benchmark v1 evaluates empty initial schedules only')
    if problem['zone'] != 'UTC':
        raise ValueError('Benchmark v1 fixtures use UTC')
    workers, demands, options = (problem[key] for key in ('workers', 'demands', 'options'))
    violations = []
    if len(set(selected)) != len(selected):
        violations.append('duplicate selected option')
    if any(type(i) is not int or i < 0 or i >= len(options) for i in selected):
        return dict(valid=False, violations=['invalid option index'], hardViolations=1)
    chosen = [options[i] for i in set(selected)]
    starts = [instant(d['start']) for d in demands]
    ends = [instant(d['end']) for d in demands]
    dates = [s.date() for s in starts]
    weeks = [week(d) for d in dates]
    by_worker = defaultdict(list)
    filled = Counter()
    day_load, week_load, cost_by_week = Counter(), Counter(), Counter()
    for option in chosen:
        w, d = option['worker'], option['demand']
        by_worker[w].append(d)
        filled[d] += 1
        day_load[w, dates[d]] += demands[d]['hours']
        week_load[w, weeks[d]] += demands[d]['hours']
        cost_by_week[weeks[d]] += option['costThousands']
    for d, count in filled.items():
        if count > demands[d]['missing']:
            violations.append(f'demand {d}: over-assigned')
    for (w, day), hours in day_load.items():
        if hours > workers[w]['dailyHours']:
            violations.append(f'worker {w} day {day}: hour limit')
    for (w, wk), hours in week_load.items():
        if hours > workers[w]['maximumHours']:
            violations.append(f'worker {w} week {wk}: hour limit')
    for w, ds in by_worker.items():
        ordered = sorted(ds, key=lambda d: starts[d])
        for at, a in enumerate(ordered):
            for b in ordered[at+1:]:
                if demands[a]['shiftId'] == demands[b]['shiftId']:
                    violations.append(f'worker {w}: duplicate shift')
                if starts[a] < ends[b] and ends[a] > starts[b]:
                    violations.append(f'worker {w}: overlap')
                elif (starts[b] - ends[a]).total_seconds() < workers[w]['restSeconds']:
                    violations.append(f'worker {w}: rest limit')
        run, previous = 0, None
        for day in sorted(set(dates[d] for d in ds)):
            run = run + 1 if previous and day == previous + dt.timedelta(days=1) else 1
            if run > workers[w]['consecutiveDays']:
                violations.append(f'worker {w}: consecutive-day limit')
            previous = day

    # Recompute the soft objective independently, over the same active worker/weeks.
    active = {(o['worker'], weeks[o['demand']]) for o in options}
    grouped_workers, group_demands = defaultdict(list), defaultdict(set)
    for w, worker in enumerate(workers):
        grouped_workers[worker['fairnessGroup']].append(w)
    for option in options:
        d = option['demand']
        group_demands[workers[option['worker']]['fairnessGroup'], weeks[d]].add(d)
    targets = {}
    for (group, wk), ds in group_demands.items():
        required_hours = sum(demands[d]['hours'] * demands[d]['missing'] for d in ds)
        capacity = sum(workers[w]['maximumHours'] for w in grouped_workers[group])
        for w in grouped_workers[group]:
            maximum = workers[w]['maximumHours']
            targets[w, wk] = min(maximum, math.floor(required_hours * maximum / capacity + 0.5)) if capacity else 0
    deficit = overtime = deviation = 0
    for w, wk in active:
        worker = workers[w]
        load = week_load[w, wk]
        minimum = min(worker['minimumHours'], worker['maximumHours'])
        standard = minimum if worker['minimumHours'] > 0 else min(4000, worker['maximumHours'])
        deficit += max(0, minimum - load)
        overtime += max(0, load - standard)
        deviation += abs(load - targets[w, wk])
    budget = problem['weeklyBudgetThousands']
    excess = sum(max(0, cost - budget) for cost in cost_by_week.values()) if budget is not None else 0
    preferred = sum(o['preferred'] for o in chosen)
    cost = sum(o['costThousands'] for o in chosen)
    soft = (len(chosen)-preferred)*400 + cost + 2*deficit + 2*overtime + deviation + 10*excess
    # Group-wise utilization dispersion, including employees assigned zero work.
    dispersions = []
    for wk in sorted(set(weeks)):
        for ws in grouped_workers.values():
            utilization = [week_load[w, wk] / workers[w]['maximumHours'] * 100
                           for w in ws if workers[w]['maximumHours'] > 0]
            if utilization:
                dispersions.append(statistics.pstdev(utilization))
    required = sum(d['missing'] for d in demands)
    missing = sum(max(0, d['missing']-filled[i]) for i, d in enumerate(demands))
    max_priority = max((d['priority'] for d in demands), default=1)
    priority_missing = sum(max(0, d['missing']-filled[i])*(max_priority-d['priority']+1)
                           for i, d in enumerate(demands))
    return dict(valid=not violations, violations=violations, hardViolations=len(violations),
                required=required, assigned=len(chosen), missing=missing, priorityMissing=priority_missing,
                coveragePercent=100*(required-missing)/required if required else 100,
                preferredPercent=100*preferred/len(chosen) if chosen else 0,
                costVnd=cost*1000, minimumDeficitHours=deficit/100, overtimeHours=overtime/100,
                fairnessUtilizationStd=statistics.mean(dispersions) if dispersions else 0,
                softPenalty=soft)
