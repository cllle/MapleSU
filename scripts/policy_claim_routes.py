"""Bind modern raw claims and legacy native modules to the actual packaged catalog."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / 'runtime/ultra/boot/init-bridge'
RAW_FAMILIES = {'android14-6.1', 'android15-6.6', 'android16-6.12', 'android17-6.18'}
LEGACY_FAMILIES = {'android12-5.10': '5.10', 'android13-5.15': '5.15'}
COMMON_SOURCES = ('manager_management.c', 'manager_resident_host.c', 'manager_owner_watch.c',
                  'manager_uid_gateway.c', 'manager_uid_snapshot.c', 'manager_ksu_compat.c')
COMMON_PATHS = {'runtime/ultra/identity/kernel/' + name for name in COMMON_SOURCES} | {
    'runtime/ultra/identity/kernel/manager_ksu_compat.h',
    *('runtime/ultra/boot/init-bridge/' + name for name in ('boot_events.c', 'boot_events.h', 'preinit.h'))}
REQUIRED_COMPONENTS = ('targetBoot', 'bridge', 'handoff', 'config', 'moduleSymvers',
                       'targetContract', 'bundleSources', 'sessionAbi', 'taskProfileAbi')


def require(value, code):
    if not value:
        raise ValueError('CLAIM_ROUTE_' + code)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def encoded(value):
    return (json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True) + '\n').encode('ascii')


def api(name):
    spec = importlib.util.spec_from_file_location('claim_route_' + name, BRIDGE / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def relative(path):
    require(isinstance(path, str) and re.fullmatch(r'[A-Za-z0-9._/-]{1,240}', path) and
            all(x not in ('', '.', '..') for x in path.split('/')), 'PATH')
    return path


def verify_native_module(module, contract, components):
    package = api('package')
    sys.path.insert(0, str(ROOT / 'tools/specgen'))
    from specgen.bootimg import _inspect, decode as decode_kernel
    from specgen.kallsyms import decode as decode_symbols
    boot = components['targetBoot']
    layout = _inspect(boot)
    require(layout['kernelSize'] > 0, 'TARGET_BOOT_KERNEL')
    image = decode_kernel(boot[layout['kernelOffset']:layout['kernelOffset'] + layout['kernelSize']])
    require(digest(image) == contract['inputs']['kernelImage']['sha256'], 'TARGET_KERNEL')
    require(api('legacy_cfi_contract').target_facts(image, decode_symbols(image)) == contract['legacyCfi'], 'TARGET_CFI')
    config = dict(line.split('=', 1) for line in components['config'].decode().splitlines()
                  if line.startswith('CONFIG_') and '=' in line)
    # Reuse normal MODULE/package qualification. This adds no raw BTF requirement.
    package.inspect_module(module, contract['kernelRelease'], config, components['moduleSymvers'], contract)
    session = json.loads(components['sessionAbi'])
    require(session.get('cfiPolicy', {}).get('mode') == 'legacy-lto-cfi', 'SESSION_CFI')
    package.verify_session_module(module, session)
    api('legacy_cfi_contract').verify_module(module, contract['legacyCfi'], session['callbacks'])
    api('task_profile_abi').verify_module(module, json.loads(components['taskProfileAbi']))


def catalog_routes(read, raw_sources=None, verify_module=verify_native_module):
    catalog_bytes = read('catalog.json')
    catalog = json.loads(catalog_bytes)
    require(catalog.get('schema') == 'maplesu-init-bridge-catalog/1', 'CATALOG_SCHEMA')
    generation = catalog.get('sourceGenerationSha256')
    require(isinstance(generation, str) and re.fullmatch('[0-9a-f]{64}', generation), 'GENERATION')
    rows = catalog.get('variants')
    require(isinstance(rows, list) and 1 <= len(rows) <= 64, 'CATALOG_VARIANTS')
    require(len({r['manifestSha256'] for r in rows}) == len(rows) and
            catalog.get('defaultManifestSha256') in {r['manifestSha256'] for r in rows}, 'CATALOG_MEMBERS')
    families = {}
    for row in rows:
        prefix = row['prefix']
        require(prefix == ('' if row['manifestSha256'] == catalog['defaultManifestSha256'] else
                'variants/' + row['manifestSha256'] + '/'), 'PREFIX')
        require(row['proof'] == 'catalog-proofs/' + row['manifestSha256'] + '.json', 'PROOF_PATH')
        manifest_bytes = read(prefix + 'manifest.json')
        require(digest(manifest_bytes) == row['manifestSha256'], 'MANIFEST_IDENTITY')
        manifest = json.loads(manifest_bytes)
        proof_bytes = read(relative(row['proof']))
        require(digest(proof_bytes) == row['proofSha256'], 'PROOF_IDENTITY')
        proof = json.loads(proof_bytes)
        require(proof.get('schema') == 'maplesu-init-bridge-variant/1' and
                proof.get('sourceGenerationSha256') == row.get('sourceGenerationSha256') == generation and
                digest(encoded(proof['generationInputs'])) == generation, 'SOURCE_GENERATION')
        for key in ('manifestSha256', 'targetBootSha256', 'kernelSha256'):
            require(proof.get(key) == row.get(key), 'PROOF_BINDING:' + key)
        require(manifest.get('schemaVersion') == 1 and all(field in manifest for field in REQUIRED_COMPONENTS),
                'MODULE_COMPONENTS')
        components = {field: read(prefix + relative(manifest[field])) for field in REQUIRED_COMPONENTS}
        for field, body in components.items():
            require(digest(body) == manifest.get(field + 'Sha256'), 'COMPONENT_IDENTITY:' + field)
        require(proof['generationInputs'].get('handoffSha256') == digest(components['handoff']), 'HANDOFF_GENERATION')
        require(manifest['targetBootSha256'] == row['targetBootSha256'] and
                manifest['kernelSha256'] == row['kernelSha256'], 'TARGET_IDENTITY')
        contract = json.loads(components['targetContract'])
        require(contract.get('schema') == 'maplesu-init-bridge-kmi-target/1', 'TARGET_CONTRACT')
        inputs = contract.get('inputs', {})
        for field, key in (('targetBoot', 'targetBoot'), ('config', 'targetConfig')):
            require(inputs.get(key, {}).get('sha256') == manifest[field + 'Sha256'], 'CONTRACT_TARGET:' + key)
        require(inputs.get('kernelImage', {}).get('sha256') == manifest['kernelSha256'], 'CONTRACT_KERNEL')
        release = contract.get('kernelRelease', '')
        family = next((f for f, series in LEGACY_FAMILIES.items() if re.match(re.escape(series) + r'(?:\.|-|$)', release)), None)
        if family is None:
            continue
        require(family not in families and contract.get('cfiMode') == 'legacy-lto-cfi' and
                isinstance(contract.get('legacyCfi'), dict), 'MODULE_CFI_OR_FAMILY')
        require(proof['bundledModule'] == {'bytes': len(components['bridge']), 'sha256': digest(components['bridge'])},
                'MODULE_IDENTITY')
        common = proof['generationInputs'].get('moduleSources', {})
        required = COMMON_PATHS
        require(required <= common.keys(), 'COMMON_SOURCE_CLOSURE')
        if raw_sources is not None:
            require(required <= raw_sources.keys() and all(common[key] == raw_sources[key] for key in
                    common.keys() & raw_sources.keys()), 'COMMON_SOURCE_GENERATION')
        verify_module(components['bridge'], contract, components)
        families[family] = {key: row[key] for key in ('manifestSha256', 'prefix', 'targetBootSha256',
                            'kernelSha256', 'sourceGenerationSha256', 'proof', 'proofSha256')}
    require(families.keys() == LEGACY_FAMILIES.keys(), 'MODULE_FAMILY_CLOSURE')
    return dict(schema='maplesu-claim-module-routes/1', catalog={'bytes': len(catalog_bytes), 'sha256': digest(catalog_bytes)},
                sourceGenerationSha256=generation, families=families)


def verify_manifest_routes(manifest, read, verify_module=verify_native_module):
    payloads = manifest.get('payloads')
    require(isinstance(payloads, dict), 'PAYLOADS')
    if 'moduleRoutes' not in manifest:
        require(set(payloads) >= RAW_FAMILIES | LEGACY_FAMILIES.keys(), 'FAMILY_CLOSURE')
        return
    require(set(payloads) == RAW_FAMILIES, 'RAW_FAMILY_CLOSURE')
    actual = catalog_routes(read, verify_module=verify_module)
    require(manifest['moduleRoutes'] == actual, 'CATALOG_BINDING')
    return actual


def inspect_selected_catalog(catalog_path, plan_path, source_selection):
    catalog_path, plan_path = Path(catalog_path).resolve(), Path(plan_path).resolve()
    catalog = json.loads(catalog_path.read_bytes())
    plan = json.loads(plan_path.read_bytes())
    require(plan.get('schema') == 'maplesu-init-bridge-catalog-plan/1', 'PLAN_SCHEMA')
    variants = [api('package_catalog').inspect_variant(row, plan_path.parent) for row in plan['variants']]
    require({v['proof']['manifestSha256'] for v in variants} == {v['manifestSha256'] for v in catalog['variants']}, 'STRICT_VARIANTS')
    for item in variants:
        row, = [r for r in catalog['variants'] if r['manifestSha256'] == item['proof']['manifestSha256']]
        require(json.loads((catalog_path.parent / relative(row['proof'])).read_bytes()) == item['proof'], 'STRICT_PROOF')
        for name, expected in item['files'].items():
            body = (catalog_path.parent / (row['prefix'] + name)).read_bytes()
            require(len(body) == expected['bytes'] and digest(body) == expected['sha256'], 'STRICT_COMPONENT')
    raw_sources = {row['logicalPath']: {k: row['after'][k] for k in ('bytes', 'sha256')}
                   for row in source_selection['files']}
    return catalog_routes(lambda name: (catalog_path.parent / relative(name)).read_bytes(), raw_sources)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--claim-manifest', type=Path, required=True)
    parser.add_argument('--catalog', type=Path, required=True)
    args = parser.parse_args()
    verify_manifest_routes(json.loads(args.claim_manifest.read_bytes()),
                           lambda name: (args.catalog.parent / relative(name)).read_bytes())

if __name__ == '__main__': main()