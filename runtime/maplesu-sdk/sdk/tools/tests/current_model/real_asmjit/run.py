#!/usr/bin/env python3
"""Real AsmJit void-wrapper regression: exact r2 red, r4 green, reuse sealed r23 library."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

HERE = Path(__file__).resolve().parent
IMAGE = 'sha256:272ada1e61c525116032246df2a790d1ccec1804b7a9e0b09203c5e650dcb78e'
INLINE = 'runtime/maplesu-sdk/include/module_base_kernel_export_symbol.inl'
MODEL = 'runtime/maplesu-sdk/include/kernel_offsets/task_struct.h'
RAW_EXPORT = 'runtime/maplesu-sdk/sdk/src/patch/patch_avc_denied.cpp'

def identity(path):
    data = path.read_bytes()
    return {'path': str(path.resolve()), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}

def body(text, signature):
    start = text.index(signature)
    at = text.index('{', start) + 1
    depth = 1
    while depth:
        if text[at] == '{': depth += 1
        elif text[at] == '}': depth -= 1
        at += 1
    return text[start:at]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before-root', type=Path, required=True)
    parser.add_argument('--after-root', type=Path, required=True)
    parser.add_argument('--dependencies', type=Path, required=True)
    parser.add_argument('--selection', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--reuse-contract', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve(); out.mkdir(parents=True, exist_ok=False)
    dependencies = args.dependencies.resolve()
    reuse = json.loads(args.reuse_contract.read_bytes())
    assert reuse['rebuildPermittedByThisPlan'] is False
    reused_library = Path(reuse['library']['path'])
    prior_receipt_path = Path(reuse['receipt']['path'])
    assert identity(reused_library) == reuse['library']
    assert identity(prior_receipt_path) == reuse['receipt']
    prior_receipt = json.loads(prior_receipt_path.read_bytes())
    assert reuse['library'] in prior_receipt['artifacts']
    assert prior_receipt['image'] == reuse['image'] == IMAGE
    assert prior_receipt['inputsUnchanged']
    assert str(dependencies) == reuse['dependenciesRoot']
    library_compile = prior_receipt['commands'][0]
    assert library_compile == reuse['libraryCompilation']
    assert library_compile['name'] == 'compile-selected-library' and library_compile['exit'] == 0
    assert library_compile['sanitizer'] is False
    inputs = [identity(Path(__file__)), identity(HERE/'test_current_void.cpp'), identity(args.selection),
        identity(args.reuse_contract), identity(reused_library), identity(prior_receipt_path)]
    for key in ['r23ToolInventoryBefore', 'r23ToolInventoryAfter', 'r23Execution']:
        path = Path(reuse[key]['path']); assert identity(path) == reuse[key]; inputs.append(identity(path))
    inputs += [identity(path) for path in sorted(dependencies.rglob('*')) if path.is_file()]
    selection = json.loads(args.selection.read_bytes())
    sources = selection['sourceRelativePaths']
    assert len(sources) == 59 and len(set(sources)) == 59
    assert all((dependencies/path).is_file() for path in sources)
    assert identity(args.selection) == reuse['selection']
    assert sources == reuse['sourceRelativePaths'] == prior_receipt['selectedImplementationSources']
    actual_dependencies = [identity(path) for path in sorted(dependencies.rglob('*')) if path.is_file()]
    assert actual_dependencies == reuse['dependencyInputs']
    prior_inputs = {item['path']: item for item in prior_receipt['inputs']}
    assert all(prior_inputs[item['path']] == item for item in actual_dependencies)
    fragments = []
    raw_source = (dependencies/RAW_EXPORT).read_text(encoding='utf-8-sig')
    raw_body = body(raw_source, 'static inline __attribute__((always_inline)) std::vector<std::uint8_t> aarch64_my_asm_to_bytes(')
    for stage, source_root in [('before', args.before_root.resolve()), ('after', args.after_root.resolve())]:
        target = out/stage; target.mkdir()
        for relative in [INLINE, MODEL]: inputs.append(identity(source_root/relative))
        text = (source_root/INLINE).read_text(encoding='utf-8-sig')
        begin = text.index('inline KModErr get_current_checked(')
        end = text.index('static const std::vector<std::string> copy_from_user_symbol_names', begin)
        fragment = text[begin:end]
        (target/'actual_current.inl').write_text(fragment, encoding='utf-8')
        model_text = (source_root/MODEL).read_text(encoding='utf-8-sig')
        model_body = body(model_text, 'enum class CurrentTaskSource')+';\n'+body(model_text, 'struct CurrentTaskModel')+';\n'
        (target/'actual_model.inl').write_text(model_body, encoding='utf-8')
        (target/'actual_raw_export.inl').write_text(raw_body+'\n', encoding='utf-8')
        (target/'test_current_void.cpp').write_bytes((HERE/'test_current_void.cpp').read_bytes())
        fragments.append({'stage': stage, 'productionInline': identity(source_root/INLINE),
            'actualInlineFragment': identity(target/'actual_current.inl'), 'actualModelFragment': identity(target/'actual_model.inl'),
            'actualRawExporterFragment': identity(target/'actual_raw_export.inl')})
    generated_inputs = [identity(path) for path in sorted(out.rglob('*')) if path.is_file()]
    docker = ['docker', 'run', '--rm', '--pull', 'never', '--network', 'none', '--read-only',
        '--tmpfs', '/tmp:rw,size=512m', '--mount', f'type=bind,source={out},target=/work',
        '--mount', f'type=bind,source={dependencies},target=/deps,readonly',
        '--mount', f'type=bind,source={reused_library.parent},target=/reused,readonly', '-w', '/work', IMAGE]
    common = ['g++', '-std=c++20', '-O1', '-g', '-DASMJIT_STATIC=', '-fsanitize=address,undefined',
        '-fno-omit-frame-pointer', '-pthread']
    assert common == reuse['fixtureCompilerCommon']
    prior_tail = common+['-fPIC', '-shared']+['/deps/'+path for path in sources]+[
        '-ldl', '-lrt', '-o', '/work/libselected-asmjit.so']
    assert library_compile['argv'][library_compile['argv'].index('g++'):] == prior_tail == reuse['libraryCompilerArgvTail']
    # Passive file hashes only: no compiler query and no selected-library rebuild.
    tool_check = ('import hashlib,pathlib,shutil; expected='+repr(reuse['compilerFiles'])+'; '
        'driver=pathlib.Path(shutil.which("g++")).resolve(); '
        'assert str(driver)==expected[0]["path"]; '
        'actual=[dict(path=str(pathlib.Path(x["path"]).resolve()),bytes=len(pathlib.Path(x["path"]).read_bytes()),'
        'sha256=hashlib.sha256(pathlib.Path(x["path"]).read_bytes()).hexdigest()) for x in expected]; '
        'assert actual==expected; print("REUSED_COMPILER_IDENTITIES_MATCH")')
    commands = [('verify-reused-compiler-files', docker[:-1]+['--entrypoint', '/usr/bin/python3', IMAGE,
        '-B', '-c', tool_check], 0)]
    for stage, expected in [('before', 1), ('after', 0)]:
        commands.append((stage+'-compile', docker+common+['-no-pie', '-I/work/'+stage,
            '-I/deps/runtime/maplesu-sdk/include', '/work/'+stage+'/test_current_void.cpp',
            '-L/reused', '-lselected-asmjit', '-Wl,-rpath,/reused', '-ldl', '-lrt', '-o', '/work/'+stage+'/current-void-test'], 0))
        commands.append((stage+'-test', docker+['/work/'+stage+'/current-void-test'], expected))
    rows = []
    baseline_assertion_red = False
    for name, argv, expected in commands:
        result = subprocess.run(argv, capture_output=True, text=True, encoding='utf-8', errors='replace')
        output = result.stdout+result.stderr
        print(output, end=''); (out/(name+'.log')).write_text(output, encoding='utf-8')
        sanitizer = 'runtime error:' in output or 'ERROR: AddressSanitizer:' in output
        rows.append({'name': name, 'argv': argv, 'exit': result.returncode, 'expectedExit': expected, 'sanitizer': sanitizer})
        if name == 'before-test':
            baseline_assertion_red = result.returncode == 1 and 'VOID_COMPAT:' in output and 'SETUP:' not in output
        if result.returncode != expected or sanitizer or (name == 'before-test' and not baseline_assertion_red): break
    stable = all(identity(Path(item['path'])) == item for item in inputs+generated_inputs)
    passed = stable and baseline_assertion_red and len(rows) == len(commands) and all(
        row['exit'] == row['expectedExit'] and not row['sanitizer'] for row in rows)
    receipt = {'schema': 'current-model-real-selected-asmjit-void/1', 'inputs': inputs,
        'generatedInputs': generated_inputs, 'inputsUnchanged': stable, 'image': IMAGE,
        'selectedImplementationSources': sources, 'fragments': fragments, 'commands': rows,
        'libraryReuse': {'contract': identity(args.reuse_contract), 'artifact': identity(reused_library),
            'priorReceipt': identity(prior_receipt_path), 'rebuilt': False},
        'baselineAssertionRed': baseline_assertion_red, 'passed': passed,
        'artifacts': [identity(path) for path in [reused_library, out/'before/current-void-test', out/'after/current-void-test'] if path.is_file()],
        'scope': 'Real selected AsmJit 1.16.0 implementation, real project assembly/error-handler header and exact raw exporter. Only resolver and Huawei adjustment are injected at the wrapper seam. Both void wrappers, preexisting multi-section code/labels/relocations/unresolved fixups, second attached emitter, own/inherited collecting and throwing handlers, no handler, actual project context, raw/flatten/relocate/copy exports, unchanged checked-error controls and success byte equality. No real kernel, vendor span, VA-width, Huawei combined path, full SDK build or device acceptance.'}
    (out/'receipt.json').write_text(json.dumps(receipt, indent=2)+'\n', encoding='utf-8')
    raise SystemExit(0 if passed else 1)

if __name__ == '__main__': main()
