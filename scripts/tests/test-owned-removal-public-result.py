"""Execute the real restoreBoot wrapper and completed-observer result block on JVM."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def identity(path):
    path = Path(path)
    return dict(path=str(path), bytes=path.stat().st_size,
                sha256=hashlib.sha256(path.read_bytes()).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--runtime-receipt', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    runtime = json.loads(args.runtime_receipt.read_bytes())
    command = next(row['argv'] for row in runtime['commands'] if row['label'] == 'tests')
    java = command[0]
    old_cp = command[command.index('-cp') + 1]
    frozen = [*runtime['frozenInputs'], *runtime['classes'], runtime['jsonJar'], runtime['java']]
    for row in frozen:
        assert identity(row['path']) == row, 'Frozen input changed: ' + row['path']
    classpath_files = []
    for entry in map(Path, old_cp.split(os.pathsep)):
        classpath_files.extend(sorted(entry.rglob('*.class')) if entry.is_dir() else [entry])
    classpath_inputs = [identity(path) for path in classpath_files]
    compiler = next(row['path'] for row in runtime['frozenInputs'] if 'kotlin-compiler-embeddable' in row['path'])
    std = next(path for path in old_cp.split(os.pathsep) if 'kotlin-stdlib' in path)
    annotations = next(row['path'] for row in runtime['frozenInputs'] if 'annotations-13.0.jar' in row['path'])
    coroutines = next(row['path'] for row in runtime['frozenInputs'] if 'kotlinx-coroutines-core-jvm' in row['path'])
    main = ROOT / 'manager/src/main'
    cli = main / 'java/com/maplesu/core/ui/util/KsuCli.kt'
    vm = main / 'java/dev/maplesu/manager/installer/InstallerFlashViewModel.kt'
    resource = main / 'res/values/maplesu_strings.xml'
    strings = {row.attrib['name']: row.text or '' for row in ET.parse(resource).getroot().findall('string')}
    names = [name for name in strings if name.startswith(('restore_boot_', 'remove_owned_'))]
    stubs = {
        'Bundle.kt': '''package android.os
class Bundle {
    private val values = mutableMapOf<String, Any>()
    fun putString(key: String, value: String) { values[key] = value }
    fun getString(key: String) = values[key] as? String
    fun putBoolean(key: String, value: Boolean) { values[key] = value }
    fun getBoolean(key: String) = values[key] as? Boolean ?: false
}
''',
        'Log.kt': 'package android.util\nobject Log { fun w(tag:String, message:String, error:Throwable)=0 }\n',
        'InstallerServiceClient.kt': '''package dev.maplesu.manager.installer
import android.os.Bundle
object InstallerServiceClient {
    lateinit var reply: Bundle
    var failure: Exception? = null
    fun restoreStockImages(context: Any, taskId: String): Bundle { failure?.let { throw it }; return reply }
    fun installationStatus(context: Any, taskId: String): Bundle = reply
}
''',
        'Resources.kt': 'package com.maplesu.core\nobject R { object string {\n' +
            ''.join(f'const val {name} = {index}\n' for index, name in enumerate(names)) +
            '} }\nobject TestApplication { fun getString(id: Int): String = when(id) {\n' +
            ''.join(f'{index} -> {json.dumps(strings[name], ensure_ascii=False)}\n' for index, name in enumerate(names)) +
            'else -> error("Unexpected resource") } }\nval ksuApp = TestApplication\n',
    }
    cli_text = cli.read_text(encoding='utf-8')
    start = cli_text.index('\nfun restoreBoot(') + 1
    end = cli_text.index('\n@Parcelize', start)
    stubs['RestoreBootProductionSlice.kt'] = '''package com.maplesu.core.ui.util
import android.util.Log
import com.maplesu.core.R
import com.maplesu.core.ksuApp
import dev.maplesu.manager.installer.InstallerServiceClient
import dev.maplesu.manager.installer.ImageRestorationKind
import dev.maplesu.manager.installer.installerTaskResult
private const val TAG = "RestoreResultHostTest"
''' + cli_text[start:end]
    vm_text = vm.read_text(encoding='utf-8')
    vm_start = vm_text.index('                val stockRestore =')
    vm_end = vm_text.index('                return\n', vm_start)
    stubs['ObserveResultProductionSlice.kt'] = '''package dev.maplesu.manager.installer
import com.maplesu.core.ui.util.FlashResult
fun observeCompletedResult(status: android.os.Bundle, restoring: Boolean = false): FlashResult {
    val context = Unit
    val taskId = "host-task"
    val current = status.getString("state").orEmpty()
    var captured: FlashResult? = null
    fun publishResult(context: Unit, taskId: String, result: FlashResult) { captured = result }
''' + vm_text[vm_start:vm_end] + '\n    return requireNotNull(captured)\n}\n'
    inspected = [cli, vm, resource, main / 'res/values-zh-rCN/maplesu_strings.xml']
    sources = [main / 'java/dev/maplesu/manager/installer' / (name + '.kt') for name in
               ['InstallerTaskResult', 'InstallerTaskPresentation']]
    sources += [ROOT / 'manager/src/test/resources/owned-removal-public-result-jvm/OwnedRemovalPublicResultTest.kt',
                ROOT / 'manager/src/test/java/dev/maplesu/manager/installer/InstallerTaskPresentationTest.kt']
    source_records = [identity(path) for path in sources + inspected]
    compile_sources = []
    for source in sources + inspected:
        target = out / 'source' / source.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read_bytes())
        if source in sources:
            compile_sources.append(target)
    for name, value in stubs.items():
        target = out / name
        target.write_text(value, encoding='utf-8')
        compile_sources.append(target)
    classes = out / 'classes'
    classes.mkdir()
    cp = str(classes) + os.pathsep + old_cp
    receipt = dict(schema='maplesu-owned-removal-public-result/1', sourceInputs=source_records,
                   runtimeReceipt=identity(args.runtime_receipt), runner=identity(__file__),
                   frozenInputs=frozen, classpathInputs=classpath_inputs,
                   slices=[dict(source=identity(cli), startChar=start, endChar=end),
                           dict(source=identity(vm), startChar=vm_start, endChar=vm_end)],
                   boundaryDoubles=list(stubs)[:4], commands=[], deviceOperations=False, SDKOrAPKBuild=False)
    commands = [('compile', [java, '-Xmx512m', '-cp', os.pathsep.join([compiler, std, annotations, coroutines]),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17',
        '-Xfriend-paths=' + ','.join(old_cp.split(os.pathsep)), '-classpath', cp, '-d', classes, *compile_sources]),
        ('compile-viewmodel', [java, '-Xmx512m', '-cp', os.pathsep.join([compiler, std, annotations, coroutines]),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17',
        '-Xfriend-paths=' + ','.join(old_cp.split(os.pathsep)), '-classpath', old_cp, '-d', out / 'viewmodel-classes',
        out / 'source' / vm.relative_to(ROOT), out / 'source' / sources[0].relative_to(ROOT)]),
        ('tests', [java, '-Xmx256m', '-cp', cp, 'org.junit.runner.JUnitCore',
                   'dev.maplesu.manager.installer.OwnedRemovalPublicResultTest',
                   'dev.maplesu.manager.installer.InstallerTaskPresentationTest'])]
    code = 1
    for label, argv in commands:
        result = subprocess.run(list(map(str, argv)), cwd=ROOT / 'manager', capture_output=True)
        for name in ('stdout', 'stderr'):
            (out / f'{label}.{name}.log').write_bytes(getattr(result, name))
        print(result.stdout.decode('utf-8', 'replace') + result.stderr.decode('utf-8', 'replace'), end='')
        code = result.returncode
        receipt['commands'].append(dict(label=label, argv=list(map(str, argv)), exitCode=code))
        if code:
            break
    receipt.update(exitCode=code,
        sourcesUnchanged=all(identity(row['path']) == row for row in source_records),
        frozenInputsUnchanged=all(identity(row['path']) == row for row in frozen),
        classpathInputsUnchanged=all(identity(row['path']) == row for row in classpath_inputs),
        classes=[identity(path) for path in sorted(classes.rglob('*.class'))],
        viewmodelClasses=[identity(path) for path in sorted((out / 'viewmodel-classes').rglob('*.class'))])
    (out / 'receipt.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return code


if __name__ == '__main__':
    raise SystemExit(main())
