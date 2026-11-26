"""Replay only the explicit container policy on the three frozen old AVB failures, one at a time."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import time

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import jdk_tool

ROOT = Path(__file__).resolve().parents[2]
OLD = ROOT / 'docs/dual-mode/impl/_work/2026-11-15-p2/evidence/embedded-real-lifecycle-r2/lifecycle/LIFECYCLE.json'
WRITER = ROOT / 'docs/dual-mode/impl/_work/2026-11-16/evidence/invalid-avb-candidate-green-r2/writer-overlay.jar'
EXPECTED_WRITER = '94d44e14e31c7bb5304cd779fdd6dbb669fda24464ee574270c6f57f2318b440'
CASES = [2, 8, 9]


def identity(path):
    path = Path(path)
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return {'path': str(path), 'bytes': path.stat().st_size, 'sha256': digest.hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    args.out = args.out.resolve()
    args.out.mkdir(parents=True, exist_ok=False)
    java = jdk_tool()
    harness = ROOT / 'scripts/tests/fixtures/InvalidAvbRealContainerMain.java'
    overlay = identity(WRITER)
    assert overlay['sha256'] == EXPECTED_WRITER and overlay['bytes'] == 169133
    frozen_harness = args.out / harness.name
    frozen_harness.write_bytes(harness.read_bytes())
    frozen_runner = args.out / Path(__file__).name
    frozen_runner.write_bytes(Path(__file__).read_bytes())
    receipt = {'schema': 'maplesu-invalid-avb-real-container-receipt/1',
               'scope': 'same-component container reconstruction; not full kernel patch, SDK, Root, boot or device validation',
               'runtime': identity(java), 'writer': overlay, 'harness': identity(harness),
               'runner': identity(__file__), 'baseline': identity(OLD),
               'diskBefore': {name: shutil.disk_usage(name)._asdict() for name in dict.fromkeys((ROOT.anchor, args.out.anchor))},
               'cases': [], 'SDKOrAPKBuild': False, 'deviceOperations': False, 'partitionWrites': False}
    baseline = json.loads(OLD.read_text(encoding='utf-8-sig'))['cases']
    result = 0
    for case in CASES:
        expected = baseline[case - 1]['input']
        source = Path(expected['path'])
        before = identity(source)
        assert before == expected
        record = {'id': case, 'input': before, 'diskBefore': shutil.disk_usage(args.out)._asdict()}
        evidence = args.out / ('case-' + str(case).zfill(2))
        scratch = args.out / ('scratch-' + str(case).zfill(2))
        assert not scratch.exists()
        argv = [str(java), '-Xmx128m', '--source', '17', '-cp', str(WRITER),
                str(frozen_harness), str(source), str(scratch), str(evidence), before['sha256']]
        start = time.monotonic()
        done = subprocess.run(argv, stdin=subprocess.DEVNULL, capture_output=True)
        record.update(argv=argv, exitCode=done.returncode, elapsedSeconds=time.monotonic() - start)
        evidence.mkdir(exist_ok=True)
        (evidence / 'stdout.log').write_bytes(done.stdout)
        (evidence / 'stderr.log').write_bytes(done.stderr)
        record['inputAfter'] = identity(source)
        record['inputUnchanged'] = record['inputAfter'] == before
        record['scratchRemoved'] = not scratch.exists()
        record['diskAfter'] = shutil.disk_usage(args.out)._asdict()
        if (evidence / 'result.json').is_file():
            record['result'] = json.loads((evidence / 'result.json').read_text())
        receipt['cases'].append(record)
        result = done.returncode or (0 if record['inputUnchanged'] and record['scratchRemoved'] else 1)
        print(json.dumps({'case': case, 'exitCode': result, 'scratchRemoved': record['scratchRemoved']}), flush=True)
        if result:
            break
    receipt['exitCode'] = result
    receipt['sourcesUnchanged'] = identity(harness) == receipt['harness'] and identity(__file__) == receipt['runner']
    receipt['writerUnchanged'] = identity(WRITER) == overlay
    receipt['diskAfter'] = {name: shutil.disk_usage(name)._asdict() for name in dict.fromkeys((ROOT.anchor, args.out.anchor))}
    (args.out / 'receipt.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return result


if __name__ == '__main__':
    raise SystemExit(main())
