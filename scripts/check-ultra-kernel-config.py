#!/usr/bin/env python3
"""检查最终 .config 的部分 BTF 配置依赖，并提示目标模块验证事项。

用法：python scripts/check-ultra-kernel-config.py path/to/.config [--target-kmi]
只检查 DEBUG_INFO_BTF_MODULES 的 MODULES/DEBUG_INFO_BTF 依赖及
MODULE_ALLOW_BTF_MISMATCH 的 DEBUG_INFO_BTF_MODULES 依赖。
--target-kmi 另核 target_kmi.py capture 路径既有的 MODVERSIONS=y 前置。
完整配置仍由目标内核 Kconfig 生成；本检查不证明模块可加载或设备可启动。
退出码：0=完成有限检查（可能有 WARN），1=配置关系错误，2=输入错误。
"""
from __future__ import annotations

import argparse
import sys


def parse(path: str) -> dict[str, str]:
    values: dict[str, str] = {}
    with open(path, "r", encoding="utf-8-sig") as handle:
        for line in handle:
            line = line.strip()
            if line.startswith("# CONFIG_") and line.endswith(" is not set"):
                values[line.split()[1]] = "n"
            elif line.startswith("CONFIG_") and "=" in line:
                key, value = line.split("=", 1)
                values[key] = value.strip()
    if not values:
        raise ValueError("未读到 CONFIG_ 配置项，请传入目标内核生成的最终 .config")
    return values


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("config", help="目标内核生成的最终 .config")
    parser.add_argument("--target-kmi", action="store_true", help="检查 target_kmi.py capture 路径既有前置")
    args = parser.parse_args(argv[1:])
    try:
        values = parse(args.config)
    except (OSError, UnicodeError, ValueError) as error:
        print(f"ULTRA_KERNEL_CONFIG_INPUT_ERROR: {error}", file=sys.stderr)
        return 2

    problems: list[str] = []
    if args.target_kmi and values.get("CONFIG_MODVERSIONS") != "y":
        problems.append("target_kmi.py capture 路径要求 CONFIG_MODVERSIONS=y")
    dependencies = {
        "CONFIG_DEBUG_INFO_BTF_MODULES": ("CONFIG_MODULES", "CONFIG_DEBUG_INFO_BTF"),
        "CONFIG_MODULE_ALLOW_BTF_MISMATCH": ("CONFIG_DEBUG_INFO_BTF_MODULES",),
    }
    for key, required in dependencies.items():
        if values.get(key) == "y":
            for dependency in required:
                if values.get(dependency) != "y":
                    problems.append(f"{key}=y 依赖 {dependency}=y")
    if problems:
        print("ULTRA_KERNEL_CONFIG_ERROR")
        for problem in problems:
            print(f"  ERROR: {problem}")
        return 1

    print("ULTRA_KERNEL_CONFIG_CHECKED")
    if values.get("CONFIG_DEBUG_INFO_BTF_MODULES") == "y":
        if values.get("CONFIG_MODULE_ALLOW_BTF_MISMATCH") == "y":
            print("  WARN: 已允许模块 BTF 校验失败后跳过该模块 BTF；核对依赖模块 BTF 的功能。")
        else:
            print("  WARN: 若保留厂商模块且其 split BTF 与新 vmlinux 不匹配，模块可能被拒载；按目标评估兼容性。")
    print("  NOTE: MODVERSIONS 须匹配目标模块构建合同；使用 live BTF 布局来源的路径须有可用内核 BTF。")
    print("  NOTE: 仅完成上述配置关系检查；退出码 0 不证明模块可加载或设备可启动。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
