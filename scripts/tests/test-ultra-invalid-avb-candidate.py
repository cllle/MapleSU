"""Small current-source AVB candidate contracts; no SDK/APK, image matrix or device operations."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from native_toolchain import jdk_tool

ROOT = Path(__file__).resolve().parents[2]


def identity(path):
    path = Path(path)
    data = path.read_bytes()
    return {'path': str(path), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--regressions', action='store_true', help='Run existing small component/metadata contracts too')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    java = jdk_tool()
    javac = java.with_name('javac' + ('.exe' if os.name == 'nt' else ''))
    names = ['AvbFooter', 'BootImageInspector', 'BootImageEquivalence', 'BootImageRebuilder', 'KernelIdentity']
    paths = [ROOT / 'image-core/src/main/java/dev/maplesu/image' / (name + '.java') for name in names]
    paths.append(ROOT / 'image-core/src/test/java/dev/maplesu/image/BootImageInvalidAvbCandidateTest.java')
    test_names = ['BootImageInvalidAvbCandidateTest']
    if args.regressions:
        paths = sorted((ROOT / 'image-core/src/main/java').rglob('*.java'))
        test_names.extend(['BootImageRebuilderTest', 'BootImageEquivalenceTest', 'RamdiskRebuilderTest',
                           'RecoveryRegionRebuilderTest'])
        paths.extend(ROOT / 'image-core/src/test/java/dev/maplesu/image' / (name + '.java') for name in test_names)
    sources, frozen = [], []
    for path in paths:
        target = args.out / 'source' / path.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(path.read_bytes())
        sources.append(identity(path))
        frozen.append(target)
    frozen_runner = args.out / 'source/scripts/tests' / Path(__file__).name
    frozen_runner.parent.mkdir(parents=True, exist_ok=True)
    frozen_runner.write_bytes(Path(__file__).read_bytes())
    classes = args.out / 'classes'
    classes.mkdir()
    receipt = {'schema': 'maplesu-invalid-avb-candidate/1', 'sources': sources,
               'runner': identity(__file__), 'runtime': [identity(java), identity(javac), identity(java.with_name('jar' + ('.exe' if os.name == 'nt' else '')))], 'commands': [],
               'SDKOrAPKBuild': False, 'deviceOperations': False, 'productionInputModified': False}

    def run(label, argv):
        result = subprocess.run(list(map(str, argv)), stdin=subprocess.DEVNULL, capture_output=True)
        for stream in ['stdout', 'stderr']:
            (args.out / (label + '.' + stream + '.log')).write_bytes(getattr(result, stream))
        receipt['commands'].append({'label': label, 'argv': list(map(str, argv)), 'exitCode': result.returncode})
        return result.returncode

    result = run('compile', [javac, '--release', '17', '-encoding', 'UTF-8', '-d', classes, *frozen])
    if result == 0:
        result = run('tests', [java, '-Xmx128m', '-cp', classes,
                              'dev.maplesu.image.BootImageInvalidAvbCandidateTest', args.out / 'fixture-work'])
    if result == 0 and args.regressions:
        resource = ROOT / 'image-core/src/test/resources/lenovo-avb-fixtures.zip'
        frozen_resource = args.out / 'source' / resource.relative_to(ROOT)
        frozen_resource.parent.mkdir(parents=True, exist_ok=True)
        frozen_resource.write_bytes(resource.read_bytes())
        shutil.copyfile(frozen_resource, classes / resource.name)
        receipt['resources'] = [identity(resource)]
        for name in test_names[1:]:
            result = run(name, [java, '-Xmx128m', '-Djava.io.tmpdir=' + str(args.out),
                               '-Dmaplesu.root=' + str(args.out), '-cp', classes, 'dev.maplesu.image.' + name])
            if result != 0:
                break
    if result == 0:
        overlay = args.out / 'writer-overlay.jar'
        result = run('jar', [java.with_name('jar' + ('.exe' if os.name == 'nt' else '')), '--create', '--file', overlay, '-C', classes, '.'])
        if result == 0:
            receipt['writerOverlay'] = identity(overlay)
    receipt['exitCode'] = result
    receipt['sourcesUnchanged'] = sources == [identity(item['path']) for item in sources]
    receipt['classes'] = [identity(path) for path in sorted(classes.rglob('*.class'))]
    (args.out / 'receipt.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'out': str(args.out), 'exitCode': result, 'sourcesUnchanged': receipt['sourcesUnchanged']}))
    return result


if __name__ == '__main__':
    raise SystemExit(main())
