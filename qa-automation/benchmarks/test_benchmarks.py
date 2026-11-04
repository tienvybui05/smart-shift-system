import copy
import unittest

from evaluate import evaluate
from prepare_data import parse_nrp, project, synthetic
from run_benchmarks import comparisons, summarize
from compare_runs import compare


class BenchmarkTests(unittest.TestCase):
    def fixture(self):
        return synthetic(3, 1, 'balanced')

    def test_rejects_overlapping_and_duplicate_assignments(self):
        p = self.fixture()
        p['options'] = [dict(worker=0, demand=0, preferred=True, dailyBase=0, weeklyBase=0, costThousands=188),
                        dict(worker=0, demand=1, preferred=True, dailyBase=0, weeklyBase=0, costThousands=188)]
        result = evaluate(p, [0, 1])
        self.assertFalse(result['valid'])
        self.assertTrue(any('overlap' in s for s in result['violations']))
        self.assertFalse(evaluate(p, [0, 0])['valid'])

    def test_checks_weekly_rest_and_consecutive_limits(self):
        p = self.fixture()
        a = p['demands'][0]
        b = copy.deepcopy(a) | dict(shiftId=100, start='2030-01-08T06:00:00+00:00', end='2030-01-08T14:00:00+00:00')
        p['demands'] = [a, b]
        p['options'] = [dict(worker=0, demand=d, preferred=True, dailyBase=0, weeklyBase=0, costThousands=100)
                        for d in (0, 1)]
        p['workers'][0].update(maximumHours=1000, consecutiveDays=1, restSeconds=20*3600)
        details = ' '.join(evaluate(p, [0, 1])['violations'])
        for expected in ('week', 'rest', 'consecutive'):
            self.assertIn(expected, details)

    def split_fixture(self, first_start, first_end, second_start, second_end):
        p = self.fixture()
        p['demands'] = [dict(shiftId=i+1, start=start, end=end, hours=400, missing=1, priority=1)
                        for i, (start, end) in enumerate([(first_start, first_end), (second_start, second_end)])]
        p['options'] = [dict(worker=0, demand=d, preferred=True, dailyBase=0, weeklyBase=0, costThousands=100)
                        for d in (0, 1)]
        p['workers'][0].update(dailyHours=800, maximumHours=4000, restSeconds=11*3600)
        return p

    def test_same_day_split_does_not_relax_hour_or_overlap_constraints(self):
        p = self.split_fixture('2030-01-07T08:00:00Z', '2030-01-07T12:00:00Z',
                               '2030-01-07T15:00:00Z', '2030-01-07T19:00:00Z')
        self.assertTrue(evaluate(p, [0, 1])['valid'])
        p['workers'][0]['dailyHours'] = 700
        self.assertTrue(any('hour limit' in s for s in evaluate(p, [0, 1])['violations']))
        p['demands'][1].update(start='2030-01-07T11:00:00Z', end='2030-01-07T15:00:00Z')
        self.assertTrue(any('overlap' in s for s in evaluate(p, [0, 1])['violations']))

    def test_overnight_and_midnight_shifts_never_get_split_exemption(self):
        for start, end in [('2030-01-07T22:00:00Z', '2030-01-08T06:00:00Z'),
                           ('2030-01-07T20:00:00Z', '2030-01-08T00:00:00Z')]:
            p = self.split_fixture(start, end, '2030-01-08T07:00:00Z', '2030-01-08T11:00:00Z')
            self.assertTrue(any('rest limit' in s for s in evaluate(p, [0, 1])['violations']))

    def test_exact_cross_day_rest_boundary(self):
        p = self.split_fixture('2030-01-07T18:00:00Z', '2030-01-07T22:00:00Z',
                               '2030-01-08T09:00:00Z', '2030-01-08T13:00:00Z')
        self.assertTrue(evaluate(p, [0, 1])['valid'])
        p['demands'][1]['start'] = '2030-01-08T08:59:59Z'
        self.assertTrue(any('rest limit' in s for s in evaluate(p, [0, 1])['violations']))

    def test_metric_denominators_and_cost_units(self):
        p = self.fixture()
        p['options'] = [dict(worker=0, demand=0, preferred=True, dailyBase=0, weeklyBase=0, costThousands=188)]
        result = evaluate(p, [0])
        self.assertTrue(result['valid'])
        self.assertEqual(8, result['missing'])
        self.assertAlmostEqual(100/9, result['coveragePercent'])
        self.assertEqual(100, result['preferredPercent'])
        self.assertEqual(188000, result['costVnd'])
        self.assertEqual(24.5, result['minimumDeficitHours'])

    def test_generators_are_reproducible(self):
        self.assertEqual(synthetic(30, 14, 'shortage'), synthetic(30, 14, 'shortage'))
        self.assertNotEqual(synthetic(30, 14, 'shortage', 43)['options'], synthetic(30, 14, 'shortage')['options'])

    def test_projection_is_explicit_and_zero_cap_excludes_worker(self):
        source = '''SECTION_HORIZON
14
SECTION_SHIFTS
D,480,
SECTION_STAFF
A,D=0,4800,3200,5,1,1,2
B,D=10,4800,3200,5,1,1,2
SECTION_COVER
0,D,1,100,1
'''
        p = project(parse_nrp(source), 1, 'checksum')
        self.assertFalse(p['metadata']['comparableToOriginalBestKnown'])
        self.assertTrue(p['metadata']['syntheticClockTimes'])
        self.assertEqual('checksum', p['metadata']['rawSha256'])
        self.assertEqual([1], [o['worker'] for o in p['options']])

    def test_errors_are_reported_not_averaged_as_good_solutions(self):
        common = dict(dataset='case', timeLimitSeconds=2, algorithm='CP_SAT', hardViolations=0)
        rows = [common | dict(valid=True, status='FEASIBLE', missing=1),
                common | dict(valid=False, status='ERROR', missing=100)]
        result = summarize(rows)[0]
        self.assertEqual(1, result['failures'])
        self.assertEqual(1, result['missingMean'])

    def test_paired_comparison_prioritizes_coverage_and_excludes_failures(self):
        common = dict(dataset='case', timeLimitSeconds=2, valid=True, seed=0)
        rows = [common | dict(algorithm='GREEDY_SHARED_CONSTRAINTS', missing=2, priorityMissing=2, softPenalty=1),
                common | dict(algorithm='CP_SAT', missing=1, priorityMissing=1, softPenalty=1000000),
                common | dict(algorithm='ALNS_CP_SAT', missing=0, priorityMissing=0, softPenalty=10000000),
                common | dict(algorithm='ALNS_CP_SAT', seed=1, valid=False)]
        result = comparisons(rows)
        hybrid_greedy = next(r for r in result if r['left'] == 'ALNS_CP_SAT' and r['right'] == 'GREEDY_SHARED_CONSTRAINTS')
        self.assertEqual(1, hybrid_greedy['wins'])
        self.assertEqual(1, hybrid_greedy['failedPairs'])

    def test_before_after_comparison_excludes_failed_quality_but_keeps_timing(self):
        common = dict(dataset='case', timeLimitSeconds=2, algorithm='ALNS_CP_SAT', seed=0)
        before = common | dict(valid='False', status='ERROR', missing=100, solverWallMillis=900)
        after = common | dict(valid='True', status='FEASIBLE', missing=10, solverWallMillis=2000)
        result = compare({}, {}, [before], [after])[0]
        self.assertEqual('RECOVERED', result['outcome'])
        self.assertEqual('', result['before_missing'])
        self.assertEqual(900, result['before_solverWallMillis'])
        self.assertEqual(10, result['after_missing'])

    def test_before_after_rejects_different_inputs_or_iteration_limits(self):
        for key in ('datasets', 'hybridMaxIterations', 'restPolicy'):
            with self.assertRaisesRegex(ValueError, key):
                compare({key: 'before'}, {key: 'after'}, [], [])


if __name__ == '__main__':
    unittest.main()
