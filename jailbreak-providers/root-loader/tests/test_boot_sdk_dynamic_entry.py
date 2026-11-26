"""Dynamic CONFIGURE must remain available when static image facts are absent."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
SPEC = importlib.util.spec_from_file_location("boot_sdk_dynamic_entry_build", HERE.parent / "boot_sdk_build.py")
SDK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SDK)
sys.path.insert(0, str(ROOT / "jailbreak-providers/dirtyfrag/tests"))
from test_l3_arm import definition, host_compiler


class Symbols:
    def __init__(self, missing=()):
        self.missing = missing

    def address(self, name):
        return None if name in self.missing else 0 if name == "_text" else 4096


class DynamicBuildTest(unittest.TestCase):
    bridge = {"defines": {"MSU_PH_PAGE_BYTES": 4096, "MSU_PH_REGS_PC": 256}}
    parser = SimpleNamespace(Btf=lambda _image: SimpleNamespace(types=[dict(kind=0, size=0)]))

    def test_missing_notes_keeps_a_real_dynamic_build_entry(self):
        facts = SDK.derive(self.parser, bytes(8192), Symbols(("__start_notes", "__stop_notes")), 128, self.bridge)
        self.assertTrue(facts, "Missing legacy image notes must not omit the SDK TU")
        self.assertEqual(0, facts["defines"]["MSU_BS_STATIC_BOOTSTRAP"])
        self.assertEqual(4096, facts["defines"]["MSU_BS_PAGE_BYTES"])
        self.assertTrue(facts["environmentAbi"])
        self.assertNotIn("__start_notes", facts["lookupSymbols"])
        self.assertNotIn("MSU_BS_KERNEL_NOTES", SDK.header(facts))

    def test_unknown_wrapper_keeps_dynamic_configuration(self):
        with patch.object(SDK, "kernel_notes", return_value="01020304"), patch.object(SDK, "vmalloc_facts", return_value=None):
            facts = SDK.derive(self.parser, bytes(8192), Symbols(), 128, self.bridge)
        self.assertTrue(facts, "An unknown static vmalloc wrapper must not omit the SDK TU")
        self.assertEqual(0, facts["defines"]["MSU_BS_STATIC_BOOTSTRAP"])

    def test_dynamic_support_retains_required_runtime_helpers(self):
        for name in ("copy_to_kernel_nofault", "set_memory_x", "prepare_creds", "kallsyms_lookup_name"):
            with self.subTest(name=name):
                self.assertEqual({}, SDK.derive(self.parser, bytes(8192), Symbols((name,)), 128, self.bridge))

    def test_dynamic_build_needs_a_real_page_contract(self):
        for page in (None, 0, 8192, "4096"):
            with self.subTest(page=page):
                self.assertEqual({}, SDK.derive(self.parser, bytes(8192), Symbols(), 128,
                    {"defines": {"MSU_PH_PAGE_BYTES": page}}))

    def test_dynamic_lookup_receipt_covers_runtime_allocator_variants(self):
        facts = SDK.derive(self.parser, bytes(8192), Symbols(("__start_notes",)), 128, self.bridge)
        for name in ("vmalloc_noprof", "___vmalloc_node_range", "___vmalloc_node_range_noprof", "execmem_free"):
            self.assertIn(name, facts["lookupSymbols"])


class DynamicBootstrapTest(unittest.TestCase):
    def test_dynamic_init_defers_range_abi_selection_to_configure(self):
        source = (HERE.parent / "module/maplesu_boot_sdk.c").read_text(encoding="utf-8")
        body = definition(source, "msu_boot_sdk_init")
        fixture = r'''
#include <assert.h>
#include <string.h>
typedef unsigned long uptr;
typedef unsigned long long u64;
#define MSU_PH_NOCFI
#define MAPLESU_LEGACY_HOOK_METADATA_MAGIC 1u
#define MAPLESU_LEGACY_HOOK_SCHEMA 1u
static uptr (*lookup_name)(const char *);
static int (*root_subject)(void), (*root_handover)(void);
static void *copy_in,*copy_out,*read_kernel,*write_kernel,*allocate,*allocate_range,*release;
static void *random_bytes,*memory_ro,*memory_rw,*memory_x,*memory_nx,*flush_code;
static void *prepare_cred,*commit_cred,*hold_cred,*drop_cred,*legacy_provider_get,*legacy_provider_put;
static void *release_module;
static uptr native_execve,kernel_base,native_avc;
static char sdk_request_metadata;
static struct { struct { u64 magic,schema,length,provider_get_displacement,provider_put_displacement; } legacy; }
 sdk_request_descriptor;
static int missing_allocator,missing_copy,only_noprof;
static int handover(void) { return 0; }
static uptr lookup(const char *name) {
 if(!strcmp(name,"__vmalloc_node_range")||!strcmp(name,"__vmalloc_node_range_noprof")||
    !strcmp(name,"maplesu_legacy_hook_provider_get")||!strcmp(name,"maplesu_legacy_hook_provider_put"))return 0;
 if(missing_copy&&!strcmp(name,"copy_from_user_nofault"))return 0;
 if((missing_allocator||only_noprof)&&!strcmp(name,"vmalloc"))return 0;
 if(missing_allocator&&!strcmp(name,"vmalloc_noprof"))return 0;
 return 0x1000;
}
#include "init.inc"
int main(void) {
 assert(msu_boot_sdk_init(lookup,0,handover)==(MSU_BS_STATIC_BOOTSTRAP?-2:0));
 assert(!allocate_range);
 only_noprof=1;
 assert(msu_boot_sdk_init(lookup,0,handover)==(MSU_BS_STATIC_BOOTSTRAP?-2:0));
 missing_allocator=1;
 assert(msu_boot_sdk_init(lookup,0,handover)==-2);
 missing_allocator=only_noprof=0;missing_copy=1;
 assert(msu_boot_sdk_init(lookup,0,handover)==-2);
 return 0;
}
'''
        compiler = host_compiler()
        self.assertIsNotNone(compiler)
        with tempfile.TemporaryDirectory(prefix="sdk-init-range-abi-") as directory:
            base = Path(directory)
            (base / "init.inc").write_text(body, encoding="utf-8")
            (base / "fixture.c").write_text(fixture, encoding="utf-8")
            for native in (0, 1):
                for static in (0, 1):
                    for noprof in (0, 1):
                        for environment in (0, 1):
                            with self.subTest(native=native, static=static, noprof=noprof, environment=environment):
                                flags = ["-DMSU_BOOT_SDK_NATIVE=1"] if native else []
                                binary = base / "fixture"
                                subprocess.run([compiler, "-std=gnu89", "-Wall", "-Wextra", "-Werror",
                                    "-Wno-unused-variable", *flags,
                                    "-DMSU_BS_STATIC_BOOTSTRAP=" + str(static),
                                    "-DMSU_BS_ALLOCATOR_NOPROF=" + str(noprof),
                                    "-DMSU_BS_ENVIRONMENT=" + str(environment), "-DMSU_BS_EXECMEM_FREE=0",
                                    "-I" + str(base), base / "fixture.c", "-o", binary], check=True)
                                subprocess.run([binary], check=True, timeout=5)

    def test_actual_configure_then_bootstrap_reaches_the_qualified_allocator(self):
        source = (HERE.parent / "module/maplesu_boot_sdk.c").read_text(encoding="utf-8")
        fixture = (HERE / "boot_sdk_configuration_test.c").read_text(encoding="utf-8")
        fixture = fixture[:fixture.index("struct test_case")]
        fixture = fixture.replace('static int bootstrap(void){++sdk_allocations;state=MSU_BOOT_SDK_FAILED;return bootstrap_error=-12;}',
            'static int bootstrap(void);')
        fixture = fixture.replace('static uptr fixture_range_kind(unsigned kind){return kernel_base+6144+(kind&1)*256+(kind&2)*2048;}', '''
static void *fixture_sdk_allocate(unsigned long bytes,unsigned long align,uptr start,uptr end,unsigned int flags,
 struct sdk_pgprot prot,unsigned long shift,int node,const void *caller) {
 (void)align;(void)flags;(void)prot;(void)shift;(void)node;(void)caller;
 if(state!=MSU_BOOT_SDK_INITIALIZING||bytes!=2*MSU_BS_PAGE_BYTES||start>=end||probe_active)fixture_fault=1;
 ++sdk_allocations;return 0;
}
static uptr fixture_range_kind(unsigned kind){(void)kind;return (uptr)fixture_sdk_allocate;}
''')
        fixture += '''
#include "request_protocol.h"
#define MSU_BS_STATIC_BOOTSTRAP 0
#define MSU_BS_ARENA_PAGES 2u
#define MSU_BS_KP_ADDR sdk_layout.kp_addr
#define MSU_BS_KP_NAME sdk_layout.kp_name
#define MSU_BS_KP_PRE sdk_layout.kp_pre
static unsigned char probe[MSU_BOOT_SDK_PROBE_BYTES];
static unsigned char msu_sdk_call_template[4];
#define MSU_SDK_CODE_START msu_sdk_call_template
#define MSU_SDK_PGPROT struct sdk_pgprot
static unsigned char *msu_sdk_call_template_end=msu_sdk_call_template+4,*msu_sdk_call_blr=msu_sdk_call_template;
static uptr arena,native_execve,avc_result;
static char original_root_key[49];
static int probe_installed;
static maplesu_metadata_v2 sdk_request_metadata;
static void random_bytes(void *p,unsigned long n){memset(p,0,n);}
static void flush_code(uptr a,uptr b){(void)a;(void)b;}
static int memory_nx(uptr a,int b){(void)a;(void)b;return 0;}
static int memory_rw(uptr a,int b){(void)a;(void)b;return 0;}
static int memory_ro(uptr a,int b){(void)a;(void)b;return 0;}
static int memory_x(uptr a,int b){(void)a;(void)b;return 0;}
static int msu_boot_sdk_pre(void *p,void *r){(void)p;(void)r;return 0;}
#include "bootstrap.inc"
int main(void) {
 task=kernel_base+0x4000;credential=kernel_base+0x8000;native_return=kernel_base+0xa000;
 uptr initial_args[]={MSU_BS_PAGE_BYTES,1,kernel_base+0x10000000,kernel_base+0x70000000,0xcc0,0x0068000000000703,0,0xffffffffu};
 memcpy(native_args,initial_args,sizeof native_args);release=fixture_release;
 msu_sdk_store(header+16,0x10000000,8);msu_sdk_store(header+56,0x644d5241,4);
 struct msu_boot_sdk_configuration request={0};
 request.reply.version=MSU_BOOT_SDK_VERSION;request.reply.command=MSU_BOOT_SDK_BOOTSTRAP;
 if(msu_boot_sdk_control((uptr)&request)||request.reply.status!=-11||state!=MSU_BOOT_SDK_EMPTY||
  registrations||native_allocations||sdk_allocations||sdk_request_metadata.magic)return 1;
 request.reply=(struct msu_boot_sdk_request){.version=MSU_BOOT_SDK_VERSION,.command=MSU_BOOT_SDK_CONFIGURE};
 request.layout=(struct msu_boot_sdk_layout){.version=1,.bytes=sizeof request.layout,.page_bytes=MSU_BS_PAGE_BYTES,
  .task_bytes=4096,.cred_bytes=256,.task_cred=2048,.cred_suid=16,.cred_securebits=40,.cred_usage=0,
  .cred_usage_size=8,.kprobe_bytes=128,.kp_addr=40,.kp_name=48,.kp_pre=64,.regs_pc=256};
 probe_layout=request.layout;
 if(msu_boot_sdk_control((uptr)&request)||request.reply.status||!sdk_dynamic_ready||state!=MSU_BOOT_SDK_EMPTY||
  registrations!=1||unregistrations!=1||native_allocations!=1||releases!=1||fixture_fault||wrapper_reads)return 2;
 request.reply=(struct msu_boot_sdk_request){.version=MSU_BOOT_SDK_VERSION,.command=MSU_BOOT_SDK_BOOTSTRAP};
 if(msu_boot_sdk_control((uptr)&request)||request.reply.status!=-12||state!=MSU_BOOT_SDK_FAILED||sdk_allocations!=1||
  registrations!=1||unregistrations!=1||native_allocations!=1||releases!=1||fixture_fault||wrapper_reads||
  sdk_request_metadata.magic||probe_installed||arena||root_key[0])return 3;
 puts("CONFIGURE=0 BOOTSTRAP=-12 sdk_allocations=1 sample_released=1 metadata=0 hooks=0");
 return 0;
}
'''
        configure = "\n".join(definition(source, name) for name in
            ("sdk_observation_entry", "msu_boot_sdk_allocator_pre", "sample_allocator", "configure", "msu_boot_sdk_control"))
        bootstrap = "\n".join(definition(source, name) for name in
            ("request_v2_init", "sdk_image_qualified", "bootstrap"))
        compiler = host_compiler()
        self.assertIsNotNone(compiler)
        with tempfile.TemporaryDirectory(prefix="sdk-dynamic-control-") as directory:
            base = Path(directory)
            (base / "configuration.inc").write_text(configure, encoding="utf-8")
            (base / "bootstrap.inc").write_text(bootstrap, encoding="utf-8")
            (base / "fixture.c").write_text(fixture, encoding="utf-8")
            SDK.stage_protocol(base)
            exe = base / "fixture"
            compiled = subprocess.run([compiler, "-std=c11", "-Wall", "-Wextra", "-Werror", "-Wno-unused-function",
                "-fsanitize=address,undefined", "-fno-omit-frame-pointer", "-I" + str(base),
                "-I" + str(HERE.parent / "module"), base / "fixture.c", "-o", exe], capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            result = subprocess.run([exe], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("CONFIGURE=0 BOOTSTRAP=-12 sdk_allocations=1", result.stdout)

    def test_unconfigured_bootstrap_has_no_side_effects_and_can_retry(self):
        source = (HERE.parent / "module/maplesu_boot_sdk.c").read_text(encoding="utf-8")
        body = "\n".join(definition(source, name) for name in ("sdk_image_qualified", "bootstrap"))
        fixture = (HERE / "boot_sdk_image_test.c").read_text(encoding="utf-8")
        fixture = fixture.replace('#define MSU_BS_STATIC_BOOTSTRAP 1', '#define MSU_BS_STATIC_BOOTSTRAP 0')
        fixture = fixture.replace('static int bootstrap_error,', 'static int lookups, reads;\nstatic int bootstrap_error,')
        fixture = fixture.replace('static uptr lookup_name(const char *name) {', 'static uptr lookup_name(const char *name) {\n ++lookups;')
        fixture = fixture.replace('static long read_kernel(void *out, const void *in, unsigned long n) {',
            'static long read_kernel(void *out, const void *in, unsigned long n) {\n ++reads;')
        fixture = fixture[:fixture.index('int main(')] + '''int main(void) {
 int rc=bootstrap(), again=bootstrap();
 printf("rc=%d again=%d allocations=%d hooks=%d reads=%d lookups=%d state=%u key=%d\\n",
  rc,again,allocations,hooks,reads,lookups,state,root_key[0]);
 sdk_dynamic_ready=1;
 int resumed=bootstrap();
 printf("resumed=%d final_allocations=%d final_state=%u\\n",resumed,allocations,state);
 return 0;
}
'''
        compiler = host_compiler()
        self.assertIsNotNone(compiler)
        with tempfile.TemporaryDirectory(prefix="sdk-dynamic-entry-") as directory:
            base = Path(directory)
            (base / "bootstrap.inc").write_text(body, encoding="utf-8")
            (base / "fixture.c").write_text(fixture, encoding="utf-8")
            exe = base / "fixture.exe"
            compiled = subprocess.run([compiler, "-std=c11", "-Wall", "-Wextra", "-Werror", "-Wno-unused-function",
                "-I" + str(base), "-I" + str(HERE.parent / "module"), base / "fixture.c", "-o", exe],
                capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            result = subprocess.run([exe], check=True, capture_output=True, text=True)
            fields = dict(pair.split("=") for pair in result.stdout.split())
            self.assertEqual("-11", fields["rc"], result.stdout)
            self.assertEqual("-11", fields["again"])
            for name in ("allocations", "hooks", "reads", "lookups", "state", "key"):
                self.assertEqual("0", fields[name], name)
            self.assertEqual("-12", fields["resumed"])
            self.assertEqual("1", fields["final_allocations"])
            self.assertEqual("3", fields["final_state"])


if __name__ == "__main__":
    unittest.main()
