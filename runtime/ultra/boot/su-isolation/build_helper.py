"""Pinned helper append and paired-policy-loader rebind; no installation."""
from pathlib import Path
import argparse, hashlib, io, json, struct, subprocess, sys
from elftools.elf.elffile import ELFFile

REPO = Path(__file__).resolve().parents[4]
HERE = Path(__file__).resolve().parent
PIN = '02cf4ae01842dd34685c1300cc6316488a0304197faa1ff29e990cb50c611778'
sys.path.insert(0, str(REPO/'tools'))
from native_toolchain import ndk_home, ndk_bin, tool
sys.path.insert(0, str(REPO/'scripts'))
from policy_backend_identity import describe_backend, write_identity
sha=lambda b:hashlib.sha256(b).hexdigest()

def branch(source,target):
    delta=target-source
    assert delta%4==0 and -(1<<27)<=delta<(1<<27)
    return struct.pack('<I',0x14000000|((delta//4)&0x3ffffff))

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root',type=Path,default=REPO,help='Repository containing the pinned historical helper')
    p.add_argument('--ndk',type=Path)
    p.add_argument('--backend',required=True,type=Path)
    p.add_argument('--out',required=True,type=Path)
    a=p.parse_args();out=a.out.resolve()
    ROOT=a.root.resolve()
    TOOLS=ndk_bin(ndk_home(a.ndk))
    suffix='.exe' if sys.platform=='win32' else ''
    assert out.is_relative_to(HERE) and not out.exists()
    out.mkdir()
    source=ROOT/'runtime/maplesu-sdk/helper/autorun_bootstrap'
    original=source.read_bytes();assert sha(original)==PIN
    manifest=json.loads((ROOT/'runtime/maplesu-sdk/SDK-RELEASE.json').read_text())
    meta=json.loads((ROOT/'runtime/maplesu-sdk/maintenance/entry/build.json').read_text())
    old_entry=ELFFile(io.BytesIO((ROOT/'runtime/maplesu-sdk/maintenance/entry/entry.elf').read_bytes())).get_section_by_name('.extension')
    image=ELFFile(io.BytesIO(original));loads=[(i,s) for i,s in enumerate(image.iter_segments()) if s['p_type']=='PT_LOAD']
    def offset(va):
        return next(s['p_offset']+va-s['p_vaddr'] for _,s in loads if s['p_vaddr']<=va<s['p_vaddr']+s['p_filesz'])
    old_at=offset(old_entry['sh_addr'])
    assert original[old_at:old_at+old_entry['sh_size']]==old_entry.data()
    module=0x11cf50
    assert original[offset(module):offset(module)+33]==b'GEfQdKmhZSkuWJI2rs21JO39Xmp8XcZg\0'
    root_key=meta['keyAddress'];assert root_key==0x2ca66
    rxid,rx=max(loads,key=lambda row:row[1]['p_vaddr']);assert rx['p_flags']==5
    start=(rx['p_vaddr']+max(rx['p_memsz'],len(original)-rx['p_offset'])+15)&~15
    backend=describe_backend(a.backend)
    backend_image=ELFFile(io.BytesIO(a.backend.read_bytes()))
    exports={s.name:s for s in backend_image.get_section_by_name('.dynsym').iter_symbols() if s['st_shndx']!='SHN_UNDEF'}
    for name in ('maplesu_helper_su_write','maplesu_helper_su_exec','maplesu_environment_policy_start'):
        assert name in exports and exports[name]['st_other']['visibility']=='STV_DEFAULT'
    commands=[]
    def run(name,*args):
        argv=[str(tool(TOOLS,name+suffix)),*map(str,args)]
        result=subprocess.run(argv,capture_output=True,text=True,timeout=60)
        commands.append(dict(command=argv,exitCode=result.returncode,stderr=result.stderr))
        (out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
        assert result.returncode==0,result.stdout+result.stderr
    dyn=image.get_section_by_name('.dynsym');plt=image.get_section_by_name('.plt')['sh_addr']
    defs={dyn.get_symbol(r['r_info_sym']).name:plt+32+16*i for i,r in enumerate(image.get_section_by_name('.rela.plt').iter_relocations())}
    defs.update(original_selinux_initializer=meta['originalSelinuxInitializer'],original_module_loader=meta['originalModuleLoader'],original_root_key=root_key,policy_ready_slot=meta['readySlot'],original_module_id=module,original_su_init_success=0xf5f14,original_su_init_error=0xf53fc)
    common=['--target=aarch64-linux-android21','-Oz','-ffreestanding','-fno-builtin','-fPIC','-fno-stack-protector','-fno-unwind-tables','-fno-asynchronous-unwind-tables','-Wall','-Wextra','-Werror']
    def link(target,sources,va,entry,identity):
        folder=out/target;folder.mkdir();write_identity(folder/'environment_backend_identity.h',identity)
        objects=[]
        for path in sources:
            obj=folder/(path.name+'.o')
            run('clang',*common,'-I',folder,'-c',path,'-o',obj);objects.append(obj)
        script=folder/'entry.ld'
        script.write_text('SECTIONS { . = '+hex(va)+'; .extension : { *(.text*) *(.rodata*) } /DISCARD/ : { *(.comment) *(.note*) *(.eh_frame*) } }\n')
        run('ld.lld','-T',script,'--entry='+entry,*[f'--defsym={name}={hex(value)}' for name,value in defs.items()],*objects,'-o',folder/'entry.elf')
        elf=ELFFile(io.BytesIO((folder/'entry.elf').read_bytes()))
        assert not any(s['sh_type'] in ('SHT_REL','SHT_RELA') and s['sh_size'] for s in elf.iter_sections())
        assert not any(s.name and s['st_shndx']=='SHN_UNDEF' for s in elf.get_section_by_name('.symtab').iter_symbols())
        assert all(not(s['sh_flags']&2 and s['sh_size']) or s.name=='.extension' for s in elf.iter_sections())
        return elf.get_section_by_name('.extension').data(),{s.name:s['st_value'] for s in elf.get_section_by_name('.symtab').iter_symbols() if s.name}
    policy_sources=[ROOT/'runtime/ultra/policy/environment_loader.c',ROOT/'runtime/ultra/policy/environment_entry.S']
    baseline,old_symbols=link('baseline-policy',policy_sources,old_entry['sh_addr'],'maplesu_environment_policy_entry',manifest['environmentPolicyBoot']['backend'])
    assert baseline==old_entry.data(),'Baseline policy loader reproduction failed'
    paired,new_symbols=link('paired-policy',policy_sources,old_entry['sh_addr'],'maplesu_environment_policy_entry',backend)
    assert len(paired)==len(baseline)
    for name in ('maplesu_environment_policy_entry','maplesu_environment_policy_module_entry','maplesu_environment_policy_load'):assert old_symbols[name]==new_symbols[name]
    extension,symbols=link('su-entry',[HERE/'helper_su_loader.c',HERE/'helper_su_entry.S'],start,'maplesu_helper_su_writer_entry',backend)
    patches={0xf5130:branch(0xf5130,symbols['maplesu_helper_su_writer_entry']),0xfaa34:branch(0xfaa34,symbols['maplesu_helper_su_exec_entry'])}
    result=bytearray(original);result[old_at:old_at+len(paired)]=paired
    allowed=[(old_at,old_at+len(paired))]
    for address,code in patches.items():
        at=offset(address);result[at:at+len(code)]=code;allowed.append((at,at+len(code)))
    at=rx['p_offset']+start-rx['p_vaddr'];assert at>=len(original)
    result.extend(bytes(at-len(result))+extension);result.extend(bytes((-len(result))%8))
    ph=image['e_phoff']+rxid*image['e_phentsize'];length=len(result)-rx['p_offset']
    struct.pack_into('<QQ',result,ph+32,length,length);allowed.append((ph+32,ph+48))
    note=image.get_section_by_name('.note.gnu.build-id')['sh_offset']+16
    result[note:note+20]=bytes(20);normalized=bytearray(result)
    slot=manifest['helper']['keySlotOffset'];normalized[slot:slot+4096]=bytes(4096)
    build_id=hashlib.sha1(normalized).digest();result[note:note+20]=normalized[note:note+20]=build_id
    allowed.append((note,note+20))
    assert all(x==y or any(lo<=i<hi for lo,hi in allowed) for i,(x,y) in enumerate(zip(original,result)))
    assert result[slot:slot+4096]==original[slot:slot+4096]
    final=ELFFile(io.BytesIO(result))
    for name in ('.dynamic','.rela.dyn','.rela.plt','.dynsym','.eh_frame','.eh_frame_hdr','.gcc_except_table'):
        assert final.get_section_by_name(name).data()==image.get_section_by_name(name).data()
    assert source.read_bytes()==original
    (out/'baseline-helper.elf').write_bytes(original)
    (out/'candidate-helper.elf').write_bytes(result)
    meta.update(helperSha256=sha(result),helperNormalizedSha256=sha(normalized),helperBuildId=build_id.hex(),helperBytes=len(result),backend=dict(backend,path=str(a.backend)))
    (out/'policy-entry.json').write_text(json.dumps(meta,indent=2)+'\n')
    report=dict(schemaVersion=1,baselineHelperSha256=PIN,helperSha256=sha(result),helperNormalizedSha256=sha(normalized),helperBuildId=build_id.hex(),helperBytes=len(result),backend=dict(backend,path=str(a.backend)),keySlotPreserved=True,productionInputsModified=False,deviceAccess=False,baselinePolicyReproduced=True,policyEntryAddressesPreserved=True,dynamicMetadataPreserved=True,oldUnwindMetadataPreserved=True,newRuntime='paired backend DSO handles TLS and exception unwind; append contains C and tail/continuation adapters only',extension=dict(address=start,offset=at,bytes=len(extension),sha256=sha(extension)),patches=[dict(address=addr,offset=offset(addr),baselineHex=original[offset(addr):offset(addr)+len(code)].hex(),candidateHex=code.hex()) for addr,code in patches.items()],symbols={k:v for k,v in symbols.items() if k.startswith('maplesu_')},knownBoundaries=dict(packagesVectorStackOffset=0x40,controlStackOffset=0x58,writerErrorCleanup=0xf53fc,writerSuccess=0xf5f14,moduleAddress=module,keySlotAddress=root_key),sourceSha256={str(p):sha(p.read_bytes()) for p in (Path(__file__).resolve(),HERE/'helper_su_entry.S',HERE/'helper_su_loader.c',HERE/'helper_su_backend.cpp',HERE/'CMakeLists.txt')},status='candidate; callback replay, real DSO loading and device reboot acceptance pending')
    (out/'build.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:report[k] for k in ('helperSha256','helperBytes','keySlotPreserved','baselinePolicyReproduced','status')},indent=2))

if __name__=='__main__':main()
