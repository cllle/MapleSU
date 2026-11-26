# AsmJit 1.16.0 — vendored source

`source=vendored`。本目录是冻结归档里 **59 个 asmjit 成员**的源码来源，已入库，
不依赖仓库外任何路径。收录范围是"编译所需"的裁剪副本：`LICENSE.md` + `src/asmjit/**`，
共 **152 个文件**（无 `test/`、无 `tools/`、无 CMake 文件）。

## 1. 来源

| 项 | 值 |
|---|---|
| 入库路径 | `runtime/upstream-pro/sdk/third_party/asmjit/1.16.0/` |
| 来源副本（仓内） | `patch-engine/upstream/3rdparty/asmjit2-src/` |
| 第二份副本（仓内） | `.research/skrootpro-patch-tool/3rdparty/asmjit2-src/` |
| 两副本关系 | 【实测】逐字节相同，152/152 文件 `sha256sum` 无差异 |
| 上游 | AsmJit 项目 <https://asmjit.com> |
| 许可 | Zlib（`LICENSE.md` 第一行 `Copyright (c) 2008-2025 The AsmJit Authors`） |

入库搬运**未做任何修改**，`SHA256SUMS`（152 行）与来源副本逐行一致：

```bash
cd runtime/upstream-pro/sdk/third_party/asmjit/1.16.0 && sha256sum -c SHA256SUMS   # 152/152 OK
```

## 2. 版本判据：1.16.0

三重独立证据，任一单独成立即可定版，三者互不耦合：

| # | 证据 | 位置 |
|---|---|---|
| 1 | 版本宏 `ASMJIT_LIBRARY_MAKE_VERSION(1, 16, 0)` | `src/asmjit/core/api-config.h:19` |
| 2 | 归档 39/59 成员缺 `T`、其余符号全在 `asmjit::v1_16::` 下（见 §3） | 冻结归档实测 |
| 3 | 归档成员内嵌 `source_filename` 指向 `3rdparty/asmjit2-src/...` | bitcode IR 实测 |

> 注意：该源码树里宏名是 `ASMJIT_LIBRARY_VERSION`，**不存在** `ASMJIT_VERSION`。

## 3. ABI 命名空间：实测串 = `v1_16`

这是决定符号能否对上的关键项，不能靠"宏里写着"推断，必须从归档符号反测。

**实测方法**（冻结归档取符号 + `llvm-cxxfilt` 反修饰）：

```bash
llvm-nm --defined-only --format=posix <member>.o | awk '$2=="T"{print $1}' | llvm-cxxfilt
```

**实测结果**：59 个成员的 **526 个 `T` 符号，526/526 全部**落在 `asmjit::v1_16` 之下，
没有第二个 ABI 命名空间，也没有例外：

```
_ZN6asmjit5v1_163a649Assembler5_emitEjRKNS0_8Operand_ES5_S5_PS4_
        └ 6asmjit   └ 5v1_16 (len=5)  └ 3a64 (len=3)  └ 9Assembler
  = asmjit::v1_16::a64::Assembler::_emit(unsigned, asmjit::v1_16::Operand_ const&, ...)
```

即 ABI 命名空间完整串为 **`v1_16`**（5 字符）。`a64` 是 asmjit 自身的子命名空间，
由源码 `ASMJIT_BEGIN_SUB_NAMESPACE(a64)`（`src/asmjit/arm/a64assembler.h:13`）产生，
与 ABI 版本无关，**不要**把它当成命名空间串的一部分。

与源码默认值的关系：`api-config.h:30` 写 `#define ASMJIT_ABI_NAMESPACE v1_16`，
与实测串一致 → **无需覆盖，也不要覆盖**。构建规则里仍显式写
`-DASMJIT_ABI_NAMESPACE=v1_16`，作用是把"实测到的值"钉进构建命令，
避免将来升级源码时默认值漂移而无人发现（它与默认值同值，故不改变产物）。

> 若换用任何别的串（例如把版本写成 `v1_16_0`），59 个成员的符号会全部对不上。
> 这一条是本库唯一"一错全错"的参数。

## 4. 归档形态：59/59 全是裸 LLVM bitcode

【实测】`od -An -tx1 -N4 <member>.o` → `42 43 c0 de`（LLVM raw bitcode magic，
不是 wrapper `de c0 17 0b`）。`llvm-dis` 可完整还原 IR。

从 IR 反推出的构建参数（这些不是猜的，都是 IR 里落笔的字段）：

| 参数 | 证据（实测） |
|---|---|
| `-flto` | `!llvm.module.flags` 含 `!{i32 1, !"EnableSplitLTOUnit", i32 0}` — 只有 LTO 编译单元才有 |
| `-fno-rtti` | vtable 的 RTTI 槽是 `ptr null`；模块内**没有** `_ZTI*`/`_ZTS*`/`_ZTVN10__cxxabiv1*` |
| `-Oz` | 函数属性 `minsize` + `optsize`（`-O1/-O2` 不会有 `minsize`） |
| 无 `-fstack-protector` | 属性里**没有** `sspstrong`/`ssp` |
| `-fPIC` | `!"PIC Level", i32 2` |
| 无 DWARF | `grep -c -E '!llvm\.dbg|!DISubprogram|!DIFile'` = 0 |

> 与同归档的 ELF 成员（libzip / civetweb）不同：那些是 `-O2 -fstack-protector-strong`。
> 同一个静态库里存在两套编译参数，是原构建流水线不统一的结果，已记入
> `third_party/build_rules.json` 的 profile 差异中。

## 5. 构建规则

见 `../../build_rules.json` 的 `libs[lib=asmjit]`，profile = `vendor-cxx-bitcode`：

```
clang++ -target aarch64-none-linux-android21 -std=c++17 -Oz -fPIC \
        -ffunction-sections -fdata-sections -DNDEBUG -flto -fno-rtti \
        -DASMJIT_STATIC -DASMJIT_ABI_NAMESPACE=v1_16 \
        -I src -emit-llvm -c src/asmjit/<sub>/<name>.cpp
```

- `-DASMJIT_STATIC`：静态库语义。**在 IR 层不可区分**（`api-config.h:339-351` 只在
  `_WIN32` 分支才有 `dllimport/dllexport` 差异），显式写上只为语义正确，不改变符号面。
- `-std=c++17`：实测 `c++17` 与 `c++20` 的 `T` 集完全相同；此处取 c++17。
- 59 个成员名 ↔ 59 个 `.cpp` **一一对应**（入库树恰好 59 个 `.cpp`，无歧义、无遗漏）。

## 6. 门禁结果（`sdk/tools/verify_vendor.py`）

| 项 | 结果 |
|---|---|
| 成员数 | 59 |
| 导出集（`T`）双向差集 | **59/59 精确为空** |
| 导入集（`U`）精确 | 33/59 |
| 库自有符号残差 | **0** |
| 字节一致 | 0/59（预期，见下） |
| 结论 | **PASS** |

字节不一致是**必然且已解释**的：归档 bitcode 的 `!llvm.ident` 是
`Android (11349228, based on r487747e) clang version 17.0.2`，而本机 pin 的
clang 是 `Android (10552028, based on r487747d)` —— 两个不同的 NDK 点版本，
producer 字符串不同，bitcode 字节不可能相同。所以本库的等价判据是**符号面**，
不是字节。

26 个成员存在**非库符号**的导入集残差，全部是 sysroot 版本差异，逐条列在
`build/report/vendor-gate.json` 的 `residualSymbols`：`__*_chk`（bionic FORTIFY
运行时，归档侧）、`strlen`/`snprintf`/`open64`（本机 bionic 侧）等。
本机 NDK 26.1 的 `string.h` 里根本没有 `__memcpy_chk` 包装
（`grep -c` = 0），因此这些残差无法用 flag 消除，与源码无关。

**9 个成员的 `T` 集为空**（`a64operand.o`、`operand.o`、`support.o`、`x86operand.o`、
`zonelist.o`、`zonetree.o` 连任何定义符号都没有；另有 `type.o`、`x86instdb.o`、
`zonetree.o` 无 `T`）—— 空集对空集判 **PASS**，不做"非空才算通过"的处理。

## 7. 未定项

| # | 项 | 状态 |
|---|---|---|
| U1 | `1.16.0` 是否为上游官方发布 tag | 【未能确认】asmjit 上游 tags API 曾返回空数组；不影响入库（源码在仓内且符号面 59/59 同一），对外声明来源前需补证 |
| U2 | 归档 bitcode 是否用 `-std=c++20` 编的 | 【不可判】IR 不记录 std 版本；实测两者符号面相同，故无影响 |
