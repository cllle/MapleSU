# CivetWeb 1.16 — vendored source

`source=vendored`。本目录是冻结归档里 **2 个 civetweb 成员**
（`civetweb.o` = native ELF，`CivetServer.o` = LLVM bitcode）的源码来源。

> 纠正一条施工口径：`SDK-FULL-SOURCE-RESTORATION-PLAN.md` 写「`.research` 有 1.16 源码」——
> **不成立**。仓库内原本只有两个头文件（`include/civetweb.h`、`include/CivetServer.h`），
> 没有任何 `.c/.cpp/.inl`。源码是在本工作包内按下面的锚点取得并入库的。

## 1. 来源（已实测可复现）

| 项 | 值 |
|---|---|
| 上游仓库 | <https://github.com/civetweb/civetweb> |
| tag | **`v1.16`** |
| tag commit（`git ls-remote` 实测） | `d7ba35bbb649209c66e582d5a0244ba988a15159` |
| tarball | `https://github.com/civetweb/civetweb/archive/refs/tags/v1.16.tar.gz` |
| tarball sha256（本次下载实测） | `f0e471c1bf4e7804a6cfb41ea9d13e7d623b2bcc7bc1e2a4dd54951a24d60285` |
| tarball 字节数（实测） | `12,094,780` |
| 许可 | MIT（`LICENSE.md`，首行 `ALL LICENSES`） |

> tarball 的 sha256 是 GitHub 自动生成源码包的哈希，会随打包行为变化，**不宜作为长期锚点**。
> 因此入库同时提供 `SHA256SUMS`（逐文件），以及上游 `include/*.h` 的 sha256 作为稳定锚点。

## 2. `include/` 是"上游 v1.16 + 6 个 `#define`"——这是本地补丁，不要删

入库的 `include/` **不是**上游原样，而是仓库自带的副本（原样搬运，含 CRLF），
它相对上游 v1.16 只多 6 行宏定义。**这是构建输入的一部分**，必须保留。

【实测】把仓库副本 CRLF 归一化后与上游 `include/civetweb.h` 逐行 diff：

```bash
diff -u <upstream>/include/civetweb.h <vendored>/include/civetweb.h   # 归一化 CRLF 后
```

结果**只有 1 个 hunk**（`@@ -20,6 +20,13 @@`）：

```diff
+#define NO_SSL
+#define NO_AUTH
+#define NO_CGI
+#define NO_WEBSOCKET
+#define NO_LUA
+#define NO_DAV
```

`include/CivetServer.h` 的 hunk 数为 **0**，即与上游 v1.16 逐字节相同（差异只有 CRLF→LF）。

| 文件 | sha256（入库原样，CRLF） |
|---|---|
| `include/civetweb.h` | `9524a602e549c14dadc07f418b78975a86ef725418dde3d8873501affbada6e8` |
| `include/CivetServer.h` | `bdf8d54c160b29edf680fa6498d96f8594c4cb7df309ff5510428ed4844d83eb` |

### 6 个宏里只有 2 个真实生效

【实测】在 `src/civetweb.c` 里的引用统计（本次用 grep 实测；计数口径写明以便复核）：

| 宏 | 引用行数 | `#if !defined(X)`（被我们关掉的代码块） | `#if defined(X)`（守卫/回退块） | 判定 |
|---|---:|---:|---:|---|
| `NO_SSL` | 31 | 15 | 17 | **真实开关**（另见 `mod_mbedtls.inl`、`openssl_dl.inl`） |
| `NO_CGI` | 16 | 11 | 11 | **真实开关** |
| `NO_AUTH` | 0 | 0 | 0 | **无操作**（该版本无此宏） |
| `NO_WEBSOCKET` | 0 | 0 | 0 | **无操作**（civetweb 的 websocket 恒开） |
| `NO_LUA` | 0 | 0 | 0 | **无操作**（civetweb 用 `USE_LUA`，不是 `NO_LUA`） |
| `NO_DAV` | 0 | 0 | 0 | **无操作** |

> 与既有笔记的差异（不掩盖）：`THIRD-PARTY-PLAN.md` 记 `NO_SSL` = 51 处。
> 本次用 `grep -c "\bNO_SSL\b" src/civetweb.c` 实测得 **31 行 / 31 次**，
> 用 `grep -o` 复核同为 31。该 51 无法用本文件复现，故此处不采用，
> 以本次可复现的 31（其中 15 个 `!defined` 生效点）为准。

`NO_SSL` 确实生效的独立佐证：归档 `civetweb.o` 的未定义符号里，
`SSL`/`deflate`/`inflate`/`BZ2`/`lzma` 家族计数为 **0**（实测）。

## 3. `src/` 收录范围

| 收录 | 说明 |
|---|---|
| `civetweb.c`（613,185 B） | 主实现，末尾 `#include` 各 `.inl` |
| `CivetServer.cpp`（19,886 B） | C++ 包装层 |
| `civetweb_private_lua.h` | 私有头 |
| `*.inl` × **15** | `civetweb.c` 以 `#include "xxx.inl"` 内联引入，**漏一个就编不过** |

【实测】入库 `civetweb.c` / `CivetServer.cpp` 的 sha256 与上游 tarball 内同名文件一致
（`e572a296…` / `04b0d391…`），即源码本身零修改。

**刻意不收录**：`src/main.c`（独立可执行入口，SDK 不用）、`src/CMakeLists.txt`、
`src/third_party/`（37 MB，内含 lua/duktape/sqlite/lfs 等内嵌依赖；当前配置
`NO_SSL` + 未开 `USE_LUA`/`USE_DUKTAPE`/`USE_SQLITE3`，编译不需要它们）。
若将来要开这些特性，需整目录补入并在 `build_rules.json` 加 `-DUSE_*`。

## 4. 两个成员的形态不同（同一库内混装）

| 成员 | 形态（实测 magic） | 来源 | 编译 profile |
|---|---|---|---|
| `civetweb.o` | `7f 45 4c 46` = **ELF** | `src/civetweb.c` | `vendor-c-elf` |
| `CivetServer.o` | `42 43 c0 de` = **bitcode** | `src/CivetServer.cpp` | `vendor-cxx-bitcode` |

`CivetServer.o` 的 IR 证据：函数属性含 `minsize`+`optsize`（→ `-Oz`）、
`!llvm.module.flags` 含 `EnableSplitLTOUnit`（→ `-flto`）、且**有** `_ZTI*`/`_ZTS*`
（→ **RTTI 未关**，与 asmjit 相反）。这些都已写进 `build_rules.json` 的 `perSource` 覆盖。

## 5. 构建规则要点

```
# civetweb.c  (ELF)
clang -target aarch64-none-linux-android21 -O2 -fstack-protector-strong -D_FORTIFY_SOURCE=2 \
      -fPIC -ffunction-sections -fdata-sections -DNDEBUG -DBUILD_DATE="Oct 16 2026" \
      -I include -I src -c src/civetweb.c

# CivetServer.cpp  (bitcode)
clang++ -target aarch64-none-linux-android21 -std=c++17 -Oz -fPIC \
        -ffunction-sections -fdata-sections -DNDEBUG -flto \
        -I include -I src -emit-llvm -c src/CivetServer.cpp
```

`-DBUILD_DATE="Oct 16 2026"`：`civetweb.c:21673` 明确写了"若未定义 `BUILD_DATE`
则用 `__DATE__`"。归档 `civetweb.o` 的 `.rodata` 里是 `Oct 16 2026`，本机编译当天是
`Nov 14 2026`，两者只差 **4 个字节**。用上游自带的 `BUILD_DATE` 开关把它钉住之后，
`civetweb.o` 变成**逐字节可复现**。该宏不影响任何符号。

## 6. 门禁结果

| 成员 | 导出集（`T`） | 导入集（`U`） | 字节一致 |
|---|---|---|---|
| `civetweb.o` | 78/78 精确 | 120/120 精确 | **是** |
| `CivetServer.o` | 53/53 精确 | 残差 3 个（非库符号） | 否（bitcode，NDK 点版本不同） |

`CivetServer.o` 的 3 个残差：`_ZNSt6__ndk1…__grow_by_and_replace`（libc++ 模板实例化）、
`__memset_chk`、`__strlen_chk`（bionic FORTIFY 运行时；本机 NDK 26.1 的 `string.h`
不含 `__memcpy_chk` 包装，实测 `grep -c` = 0，故无法用 flag 消除）。
三者都不是 civetweb 的 API/内部符号。

## 7. 未定项

| # | 项 | 状态 |
|---|---|---|
| U1 | `CivetServer.o` 的精确编译参数组合是否只有一种 | 【不可唯一确定】`-Oz -flto` + RTTI 已由 IR 属性/模块标志/`_ZTI` 存在性三项夹逼，但 `-std=` 不可判（不影响符号面） |
| U2 | 归档为何在同一库里混装 ELF 与 bitcode | 【未能确认】原构建流水线不统一；不影响本库等价结论 |
