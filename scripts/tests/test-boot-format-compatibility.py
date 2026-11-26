"""Compile frozen image-core sources and run its dependency-free compatibility suites."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

import sys
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import jdk_tool

ROOT = Path(__file__).resolve().parents[2]


def identity(path):
    data = path.read_bytes()
    return dict(path=str(path), bytes=len(data), sha256=hashlib.sha256(data).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--java', type=Path, default=jdk_tool())
    parser.add_argument('--suite', action='append')
    parser.add_argument('--sample', type=Path, action='append', default=[])
    args = parser.parse_args()
    args.out = args.out.resolve()
    args.sample = [path.resolve() for path in args.sample]
    args.out.mkdir(parents=True, exist_ok=False)
    classes = args.out / 'classes'
    classes.mkdir()
    sources = sorted((ROOT / 'image-core/src').glob('*/java/**/*.java'))
    resources = sorted(p for p in (ROOT / 'image-core/src/test/resources').rglob('*') if p.is_file())
    records = [identity(p) for p in [*sources, *resources]]
    samples = [identity(path) for path in args.sample]
    frozen = []
    for path in [*sources, *resources]:
        target = args.out / 'source' / path.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(path.read_bytes())
        if path in sources:
            frozen.append(target)
    available = sorted(p.stem for p in sources if p.name.endswith('Test.java')
                       and re.search(r'public static void main\(', p.read_text(encoding='utf-8')))
    suites = args.suite or available
    assert all(s in available for s in suites), (suites, available)
    receipt = dict(schema='maplesu-boot-format-compatibility/1', sources=records,
                   runner=identity(Path(__file__)), java=identity(args.java), commands=[],
                   deviceOperations=False, SDKOrAPKBuild=False, skipped=[], samples=samples)

    def run(label, argv):
        done = subprocess.run(list(map(str, argv)), cwd=ROOT, stdin=subprocess.DEVNULL,
                              capture_output=True)
        for stream in ('stdout', 'stderr'):
            (args.out / (label + '.' + stream + '.log')).write_bytes(getattr(done, stream))
        result = dict(label=label, argv=list(map(str, argv)), exitCode=done.returncode)
        receipt['commands'].append(result)
        print(label + ' exit=' + str(done.returncode), flush=True)
        return done.returncode

    source_list = args.out / 'javac.args'
    source_list.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in frozen),
                           encoding='utf-8')
    status = run('javac', [args.java.with_name('javac' + ('.exe' if os.name == 'nt' else '')), '--release', '17', '-encoding',
                           'UTF-8', '-d', classes, '@' + str(source_list)])
    if status == 0:
        classpath = os.pathsep.join(map(str, [classes, args.out / 'source/image-core/src/test/resources']))
        for suite in suites:
            extra = []
            if suite == 'BootImageInvalidAvbCandidateTest':
                extra = [args.out / 'invalid-avb-scratch']
            if suite == 'RamdiskEntrySamplesTest':
                if len(args.sample) < 2:
                    receipt['skipped'].append(dict(suite=suite, reason='requires two real-image --sample inputs'))
                    continue
                extra = args.sample
            code = run(suite, [args.java, '-Xmx768m', '-Djava.io.tmpdir=' + (os.environ.get("TEMP") or tempfile.gettempdir()),
                               '-cp', classpath, 'dev.maplesu.image.' + suite, *extra])
            status = status or code
    receipt['sourcesUnchanged'] = records == [identity(Path(r['path'])) for r in records]
    receipt['samplesUnchanged'] = samples == [identity(path) for path in args.sample]
    receipt['exitCode'] = status or (0 if receipt['sourcesUnchanged'] and receipt['samplesUnchanged'] else 2)
    (args.out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(out=str(args.out), exitCode=receipt['exitCode'],
                          sourcesUnchanged=receipt['sourcesUnchanged'])))
    return receipt['exitCode']


if __name__ == '__main__':
    raise SystemExit(main())
