"""Add a HotLoad result channel to the frozen isolated v1 installer IR."""
from pathlib import Path
import argparse
import difflib
import hashlib
import io
import json
import subprocess
import sys
from elftools.elf.elffile import ELFFile

REPO = Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--root', type=Path, default=REPO)
parser.add_argument('--v1', type=Path, required=True, help='Frozen isolated v1 installer directory')
parser.add_argument('--ndk', type=Path)
args = parser.parse_args()
OUT = Path(__file__).resolve().parent
V1 = args.v1.resolve()
ROOT = args.root.resolve()
sys.path.insert(0, str(REPO / 'tools'))
from native_toolchain import ndk_home, ndk_bin, tool
TOOLS = ndk_bin(ndk_home(args.ndk))
SUFFIX = '.exe' if sys.platform == 'win32' else ''

commands = []
sha = lambda b: hashlib.sha256(b).hexdigest()

def run(name, *args):
    command = [str(tool(TOOLS, name + SUFFIX)), *map(str, args)]
    r = subprocess.run(command, capture_output=True, text=True)
    commands.append(dict(command=command, exitCode=r.returncode, stdout=r.stdout, stderr=r.stderr))
    (OUT / 'commands.json').write_text(json.dumps(commands, indent=2) + '\n')
    if r.returncode: raise RuntimeError(r.stdout + r.stderr)

ir = (V1 / 'm053.patched.ll').read_text()

def replace(old, new):
    global ir
    assert ir.count(old) == 1, old
    ir = ir.replace(old, new)

replace('sdk3:\n  %sdk4 = alloca i32, align 4', 'sdk3:\n  %launch.result.pipe = alloca [2 x i32], align 4\n  %sdk4 = alloca i32, align 4')
replace('''sdk285:
  %sdk286 = invoke i32 @fork() #35''', '''sdk285:
  %launch.pipe.error = call i64 @maplesu_installer_result_open(i32 %sdk1, ptr %launch.result.pipe)
  %launch.pipe.ok = icmp eq i64 %launch.pipe.error, 0
  br i1 %launch.pipe.ok, label %launch.pipe.opened, label %sdk414

launch.pipe.opened:
  %sdk286 = invoke i32 @fork() #35''')
replace('''launch.detach.failed:
  %detach.error = call i64 @maplesu_installer_launch_error(i32 1, ptr null)
  br label %sdk414''', '''launch.detach.failed:
  %detach.error.recorded = call i64 @maplesu_installer_launch_error(i32 1, ptr null)
  %detach.error = call i64 @maplesu_installer_result_parent(i32 %sdk286, i32 %sdk1, ptr %launch.result.pipe)
  br label %sdk414''')
replace('''  br i1 %sdk288, label %sdk289, label %sdk414''', '''  br i1 %sdk288, label %sdk289, label %launch.parent.result

launch.parent.result:
  %launch.parent.error = call i64 @maplesu_installer_result_parent(i32 %sdk286, i32 %sdk1, ptr %launch.result.pipe)
  br label %sdk414''')
replace('''sdk289:
  br i1 %sdk60''', '''sdk289:
  call void @maplesu_installer_result_worker(ptr %launch.result.pipe)
  br i1 %sdk60''')
replace('''sdk391:
  %launch.wait.status''', '''sdk391:
  %launch.wait.status''')
replace('''sdk392:
  %launch.ok = phi i1 [ false, %launch.fork.failed ], [ %launch.wait.ok, %sdk391 ]
''', '''sdk392:
  %launch.ok = phi i1 [ false, %launch.fork.failed ], [ %launch.wait.ok, %sdk391 ]
  %launch.raw.ok = zext i1 %launch.ok to i32
  %launch.completion = call i32 @maplesu_installer_result_complete(ptr %launch.result.pipe, ptr %sdk304, i32 %launch.raw.ok)
  %launch.completed = icmp ne i32 %launch.completion, 0
''')
replace('br i1 %launch.ok, label %launch.restart.checked, label %launch.stop', 'br i1 %launch.completed, label %launch.restart.checked, label %launch.stop')
replace('[ 0, %launch.detach.checked ], [ %detach.error, %launch.detach.failed ]', '[ %launch.parent.error, %launch.parent.result ], [ %detach.error, %launch.detach.failed ], [ %launch.pipe.error, %sdk285 ]')
replace('@waitpid(i32 noundef %sdk432, ptr noundef nonnull %sdk27, i32 noundef 0)',
        '@maplesu_wait_environment_child(i32 %sdk432, ptr %sdk27)')
replace('''sdk438:
  %sdk439 = load i64, ptr %sdk26, align 8, !tbaa !51''', '''sdk438:
  %launch.final.error = load i64, ptr %sdk26, align 8, !tbaa !51
  %launch.final.status = load i32, ptr %sdk27, align 4
  %launch.final.pid.ok = icmp eq i32 %sdk433, %sdk432
  %launch.final.status.ok = icmp eq i32 %launch.final.status, 0
  %launch.final.wait.ok = and i1 %launch.final.pid.ok, %launch.final.status.ok
  %sdk439 = select i1 %launch.final.wait.ok, i64 %launch.final.error, i64 -21002''')
ir += '''
declare i64 @maplesu_installer_result_open(i32, ptr)
declare i64 @maplesu_installer_result_parent(i32, i32, ptr)
declare void @maplesu_installer_result_worker(ptr)
declare i32 @maplesu_installer_result_complete(ptr, ptr, i32)
'''
(OUT / 'm053.patched.ll').write_text(ir)
run('clang', '--target=aarch64-linux-android21', '-Oz', '-fPIC', '-c', OUT / 'm053.patched.ll', '-o', OUT / 'm053.patched.o')
(OUT / 'm053.patched.o').write_bytes((OUT / 'm053.patched.o').read_bytes().replace(b'5.0.5\0', b'5.2.0\0'))
run('clang', '--target=aarch64-linux-android21', '-std=c11', '-Oz', '-fPIC', '-ffunction-sections', '-fdata-sections', '-Wall', '-Wextra', '-Werror', '-c', OUT / 'result_channel.c', '-o', OUT / 'result_channel.o')
run('ld.lld', '-r', OUT / 'm053.patched.o', V1 / 'launch_guard.o', OUT / 'result_channel.o', '-o', OUT / 'maplesu_installer.o')

source = (V1 / 'maplesu_installer.candidate.cpp').read_text(encoding='utf-8')
candidate = source
old = 'extern "C" pid_t maplesu_installer_wait_child(pid_t, int*, const char*);'
candidate = candidate.replace(old, old + '''
extern "C" long maplesu_installer_result_open(int, int[2]);
extern "C" long maplesu_installer_result_parent(pid_t, int, int[2]);
extern "C" void maplesu_installer_result_worker(int[2]);
extern "C" int maplesu_installer_result_complete(int[2], const char*, int);''')
candidate = candidate.replace('''        const pid_t detached_pid = ::fork();''', '''        int result_pipe[2] = {-1, -1};
        const int mode_value = static_cast<int>(mode);
        const auto pipe_error = maplesu_installer_result_open(mode_value, result_pipe);
        if (pipe_error != 0) child_exit(static_cast<KModErr>(pipe_error));
        const pid_t detached_pid = ::fork();''', 1)
candidate = candidate.replace('''            child_exit(static_cast<KModErr>(-21002));''', '''            child_exit(static_cast<KModErr>(maplesu_installer_result_parent(detached_pid, mode_value, result_pipe)));''', 1)
candidate = candidate.replace('''        if (detached_pid == 0) {
            if (mode == InstallMode::Boot)''', '''        if (detached_pid == 0) {
            maplesu_installer_result_worker(result_pipe);
            if (mode == InstallMode::Boot)''', 1)
candidate = candidate.replace('''                    (void)maplesu_installer_launch_error(2, bootstrap_path.c_str());
                    ::_exit(127);''', '''                    (void)maplesu_installer_launch_error(2, bootstrap_path.c_str());
                    (void)maplesu_installer_result_complete(result_pipe, bootstrap_path.c_str(), 0);
                    ::_exit(127);''', 1)
candidate = candidate.replace('''                if (maplesu_installer_wait_child(exec_pid, &exec_status, bootstrap_path.c_str()) != exec_pid ||
                    exec_status != 0) {
                    ::_exit(127);
                }''', '''                const bool launched = maplesu_installer_wait_child(exec_pid, &exec_status, bootstrap_path.c_str()) == exec_pid &&
                    exec_status == 0;
                if (!maplesu_installer_result_complete(result_pipe, bootstrap_path.c_str(), launched)) {
                    ::_exit(127);
                }''', 1)
old = '''        // 原 fork 子进程：安装已成功，回传 OK。
        child_exit(KModErr::OK);'''
assert candidate.count(old) == 1
candidate = candidate.replace(old, '''        // HotLoad only reports the worker's validated result; Boot retains asynchronous reboot.
        child_exit(static_cast<KModErr>(maplesu_installer_result_parent(detached_pid, mode_value, result_pipe)));''')
old = '''    (void)::waitpid(pipe.child_pid(), &child_status, 0);
    return err;'''
new = '''    const pid_t waited = maplesu_wait_environment_child(pipe.child_pid(), &child_status);
    if (waited != pipe.child_pid() || child_status != 0) return static_cast<KModErr>(-21002);
    return err;'''
assert candidate.count(old) >= 1
candidate = candidate.replace(old, new, 1)
(OUT / 'maplesu_installer.candidate.cpp').write_text(candidate, encoding='utf-8')
(OUT / 'maplesu_installer.v1-to-v2.diff').write_text(''.join(difflib.unified_diff(source.splitlines(True), candidate.splitlines(True), fromfile='candidate-v1.cpp', tofile='candidate-v2.cpp')), encoding='utf-8')
canonical = (ROOT / 'runtime/maplesu-sdk/sdk/src/maplesu_core/maplesu_installer.cpp').read_text(encoding='utf-8-sig')
(OUT / 'maplesu_installer.source.diff').write_text(''.join(difflib.unified_diff(canonical.splitlines(True), candidate.splitlines(True), fromfile='canonical/maplesu_installer.cpp', tofile='candidate-v2.cpp')), encoding='utf-8')
def exports(path):
    elf = ELFFile(io.BytesIO(path.read_bytes()))
    return {s.name for s in elf.get_section_by_name('.symtab').iter_symbols()
            if s['st_info']['bind'] == 'STB_GLOBAL' and s['st_shndx'] != 'SHN_UNDEF'}
assert exports(V1 / 'm053.before.o') <= exports(OUT / 'maplesu_installer.o')
report = dict(canonicalModified=False, deviceAcceptance=False, candidateSha256=sha((OUT / 'maplesu_installer.o').read_bytes()),
    existingSdkStrongExportsPreserved=True,
    v1CandidateSha256=sha((V1 / 'maplesu_installer.o').read_bytes()),
    currentBootCompletedAndSafeSuccessRequired=True, hotloadResultBeforeRestart=True,
    caveats=['A validated completed receipt proves the helper bootstrap commit, not a fresh third-party SU command.',
             'Restart failure occurs after success has been sent; report covers bootstrap readiness before restart.',
             'A live worker that never responds times out after 120 seconds; parent fails without fabricating a result.'])
(OUT / 'build.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
