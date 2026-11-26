"""Publish an exact existing complete claim generation without compiling again."""
from pathlib import Path
import json
import shutil


def publish_existing(builder, out, assets, inputs):
    def read(path):
        return json.loads(path.read_text(encoding="utf-8"))

    def require(value, message):
        if not value:
            raise ValueError(message)

    identity, sha = builder["identity"], builder["sha"]
    expected_tools = [identity(path) for path in builder["BUILD_TOOLS"]]
    report = read(out / "results.json")
    require(report.get("inputsUnchanged") and report.get("inputs") == inputs,
            "Existing generation inputs differ from current selected generation")
    support = report["support"]
    require(set(support) == {"bootstrap", "materializer", "permissions"}, "Existing support set differs")
    source_receipt = out / "support/source-inputs.json"
    source_identity = identity(source_receipt)
    for row in read(source_receipt)["files"]:
        for key in ("selectedSource", "compiledCopy"):
            require(identity(Path(row[key]["path"])) == row[key], "Existing support source changed")
    for name, row in support.items():
        binary = out / "support" / (name + ".bin")
        require(identity(binary)["bytes"] == row["bytes"] and sha(binary) == row["sha256"] and
                row["sourceInputsIdentity"] == source_identity, "Existing support bytes/source differ: " + name)
    results, evidence = [], []
    kernels = sorted(builder["RAW_FAMILIES"]) if builder.get("MODULE_ROUTE_BINDING") else builder["KERNELS"]
    require({row["kernel"] for row in report["results"]} == set(kernels), "Existing raw family set differs")
    for kernel in kernels:
        target = out / kernel
        result_path = target / "build-result.json"
        row = read(result_path)
        payload = row.get("payload", {})
        require(row.get("kernel") == kernel and row.get("exitCode") == 0 and
                not row.get("buildError") and not row.get("metadataError") and not row.get("timedOut") and
                payload.get("referenceAbi", {}).get("complete") and
                payload.get("callbackCfi", {}).get("supported"), "Existing family has an open build/metadata gate: " + kernel)
        compiled = read(target / "build-inputs.json")
        require(compiled["buildTools"] == expected_tools and compiled["compiledKernelFamily"] == kernel and
                compiled["runtimeLayoutSourceSelection"] == builder["RUNTIME_LAYOUT_SELECTION"],
                "Existing family builder/tools/selection differ: " + kernel)
        for selected in compiled["inputs"]:
            actual = selected["sourceIdentity"]
            require(identity(Path(actual["path"])) == actual and
                    sha(target / selected["copy"]) == actual["sha256"], "Existing copied source changed: " + kernel)
        inspected = builder["inspect"](target / "claim.so")
        require(inspected == {key: value for key, value in payload.items() if key != "referenceAbi"},
                "Existing family ELF/metadata differs: " + kernel)
        require(read(target / "reference-abi.json") == payload["referenceAbi"],
                "Existing reference ABI receipt differs: " + kernel)
        require(payload["referenceAbi"]["generatorSha256"] == {path.name: sha(path) for path in
                (Path(builder["__file__"]), builder["ROOT"] / "scripts/policy_btf_abi.py",
                 builder["ROOT"] / "scripts/policy_reference_abi.py", builder["ROOT"] / "scripts/policy_reference_image.py",
                 builder["ROOT"] / "scripts/policy_payload_image_target.py")}, "Existing ABI extractor generation differs: " + kernel)
        require(builder["verify_runtime_claim"](target / "claim.full.so", target / "claim.so") == row["runtimePackaging"],
                "Existing runtime packaging differs: " + kernel)
        for receipt in sorted((target / "build-dependencies").glob("*.json")):
            builder["object_provenance"](target, receipt.stem)
        if "retirementSupport" in row:
            support_report = read(target / "retirement-support.json")
            require(support_report == row["retirementSupport"], "Existing retirement support report differs")
            bootstrap = support_report["bootstrap"]
            binary = target / "support/bootstrap.bin"
            require(identity(binary)["bytes"] == bootstrap["bytes"] and sha(binary) == bootstrap["sha256"],
                    "Existing family retirement support bytes differ: " + kernel)
            require(identity(target / "support/source-inputs.json") == bootstrap["sourceInputsIdentity"] and
                    identity(target / ".manager_host_runtime_types.o.cmd") == bootstrap["nativeCommandIdentity"] and
                    builder["core_cfi_policy"](target / ".manager_host_runtime_types.o.cmd") == bootstrap["nativeCfiPolicy"],
                    "Existing family retirement support policy/source differs: " + kernel)
        results.append(row)
        evidence.append(dict(kernel=kernel, result=identity(result_path), inputs=identity(target / "build-inputs.json"),
                             reference=identity(target / "reference-abi.json"), runtime=identity(target / "claim.so")))
    require(all(sha(builder["ROOT"] / path) == digest for path, digest in inputs.items()),
            "Selected input changed during existing-generation verification")
    if builder.get("MODULE_ROUTE_BINDING") is not None:
        require(builder["inspect_selected_catalog"](*builder["MODULE_ROUTE_INPUTS"],
                builder["RUNTIME_LAYOUT_SELECTION"]) == builder["MODULE_ROUTE_BINDING"],
                "Existing generation module route inputs changed")
    assets.mkdir(parents=True, exist_ok=False)
    for name in support:
        shutil.copyfile(out / "support" / (name + ".bin"), assets / (name + ".bin"))
    for row in results:
        family = assets / row["kernel"]
        family.mkdir()
        shutil.copyfile(out / row["kernel"] / "claim.so", family / "claim.so")
        if "retirementSupport" in row:
            shutil.copyfile(out / row["kernel"] / "support/bootstrap.bin", family / "bootstrap.bin")
    builder["write_runtime_manifest"](assets, out, inputs, support, results, builder.get("MODULE_ROUTE_BINDING"))
    require(all(sha(builder["ROOT"] / path) == digest for path, digest in inputs.items()),
            "Selected input changed during existing-generation packaging")
    receipt = dict(schema="maplesu-existing-claim-publication/1", status="PACKAGED_UNQUALIFIED_NOT_DEVICE_ACCEPTED",
        inputs=inputs, support=support, families=evidence, manifest=identity(assets / "manifest.json"),
        buildExecuted=False, deviceExecuted=False, deviceQualified=False, formalRelease=False)
    if builder.get("MODULE_ROUTE_BINDING") is not None:
        receipt["moduleRoutes"] = builder["MODULE_ROUTE_BINDING"]
        receipt["moduleRouteCatalog"], receipt["moduleRoutePlan"] = [identity(Path(path))
            for path in builder["MODULE_ROUTE_INPUTS"]]
    (out / "publish-existing.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    return 0
