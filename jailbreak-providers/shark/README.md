# 逐内核 plant（由保留的上游源码自行构建）

来源：公开项目 [Shark](https://github.com/Yunnijian/Shark) `v2.2.2`（commit
`2af72cb8959fae5f1c7f929a649db5e546334cef`，GPL-3.0）。

本目录**不分发上游发布的 runner 二进制**。这里只保留：

- `upstream/exploit/`：上游 Rust/C 源码（每内核的偏移表就在 `src/K*_plant.c` 里）；
- `plants/arm64-v8a/plant-*`：**本仓库用 Android NDK 从上述源码编译出的**独立 AArch64 可执行文件；
- `build_plants.py`：可复现的构建脚本（校验源码摘要 → 逐内核编译 → 回写摘要）；
- `manifest.json`：上游 commit/许可、每个源文件摘要、每个 plant 的源码与产物摘要、构建工具链。

上游 runner 依赖 Rust 运行时；我们只取 C 的 plant 部分（提权与 fake-cred 子进程），
用 NDK clang 直接编译成 47–88 KB 的可执行文件，因此不需要 Rust 工具链，也不携带任何
上游 Release 二进制。

## 它在这个项目里承担什么

只承担一件事：**拿到内核域 root 上下文（uid 0 + full caps + 可写内核）**，然后把控制权
交回 MapleSU 自己的接管批次。以下环节仍然是 `maplesu-93260` 原有的那一套，没有替换：

- SELinux 进入/恢复窗口；
- `bs --handover --target normal|ultra` 的模块 late-load；
- 共享接管回执（`maplesu.93260.source.v1`）与 `maplesu.handover.v1` 校验；
- 管理器接管、重启与后续验证。

## 注入点

plant 的 C 入口在 fake-cred 子进程里用 `system()` 执行
`"$PYB110_KSUD" late-load --kmi ... --package-name <...>`（hooks-gated 内核则是
`debug extract-binary`）。`MapleSu93260Attempt` 把 `PYB110_KSUD` 指向 wrapper：

```sh
#!/system/bin/sh
exec /system/bin/sh /data/local/tmp/maplesu-93260/batch.sh "$@"
```

于是 fake-cred 子进程执行的就是原有的 handover 批次：回执、SELinux 恢复、
`bs --handover` 全都不变。plant 自带的 late-load 不会被触发（gate 内核上 wrapper 故意不生成
`ko.bin`，`load_bound_ko` 随即跳过），root 不会被交给别家管理器。

## kallsyms 快照

我们的 ksud 补丁在 `MAPLESU_KALLSYMS_PATH` 存在时只认这个文件、不会回退到
`/proc/kallsyms`。保留引擎为 PD2546 打包了快照；plant 引擎没有逐构建快照，所以 wrapper
在 fake-cred root 上下文里先 `cat /proc/kallsyms` 到 `/data/local/tmp/kall`，**只有**确认
`_text` 是真实非零地址时才导出该变量；否则删掉文件、不导出，让 ksud 走自己的
`/proc/kallsyms` 回退，并在不可读时以既有错误码失败——不会把全零表喂给 ksud。

## 覆盖范围

| plant | 覆盖的完整 `uname -r` |
| --- | --- |
| `plant-61821` | `6.18.21-android17-5-gf957accaf563-ab15926176-4k`<br>`6.18.21-android17-5-g7fcbffcad3e1-ab16353812-4k` |
| `plant-61269` | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| `plant-61238` | `6.12.38-android16-5-g844001fb8721-ab14552068-4k` |
| `plant-61258` | `6.12.58-android16-6-g7704a1ae279b-ab15213644-4k`<br>`6.12.58-android16-6-g0a092cc0037a-abogki521987229-4k`<br>`6.12.58-android16-6-g690108013dcc-abogki576129896-4k` |
| `plant-61223` | `6.12.23-android16-5-g41f4cb1fa7c7-abogki465038634-4k` |
| `plant-66118` | `6.6.118-android15-8-g9bc34d5b0c79-abogki537459655-4k`<br>`6.6.118-android15-8-gc4127a25dcf3-ab15863337-4k` |
| `plant-66127` | `6.6.127-android15-8-gd489d4ad2a70-abogki567717946-4k` |

PD2546 的 `6.12.58-…-gdd6fd…` 仍走原来的 `louise` 引擎，行为不变。

## 重建

```bash
python jailbreak-providers/shark/build_plants.py \
    --ndk "$ANDROID_NDK_HOME"
# 只校验已记录的产物与源码摘要：
python jailbreak-providers/shark/build_plants.py --check
```

脚本会先逐字节校验 `manifest.json` 里的 `sources`，再用 NDK clang
`--target=aarch64-linux-android31 -O2 -fPIC -fno-stack-protector -D_GNU_SOURCE -pthread`
编译每个 `K*_plant.c`，最后回写 `plants` 与 `build`。`package.py` 在打包时会再次校验
产物摘要与源码摘要，不一致直接失败。
