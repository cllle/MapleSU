# `sdk/third_party/` — vendored third-party source

本目录存放冻结归档里 **161 个第三方成员**的源码，使这些成员可以**从仓库内**重建，
不再依赖任何仓库外路径。

```
third_party/
├── build_rules.json          # build.py 消费的编译规则（profile / flags / sources / members）
├── README.md                 # 本文件：来源、版本、许可、校验锚点总表
├── asmjit/1.16.0/            # LICENSE.md + README.md + SHA256SUMS + src/asmjit/**
├── civetweb/1.16/            # LICENSE.md + README.md + SHA256SUMS + include/** + src/**
└── libzip/1.0.1/             # LICENSE  + README.md + SHA256SUMS + lib/** + config/**
```

## 1. 总表

| lib | version | 归档成员 | 成员形态 | 许可 | 上游锚点 | 门禁（导出集） |
|---|---|---:|---|---|---|---|
| asmjit | 1.16.0 | **59** | 全部 LLVM bitcode | Zlib | 源码已在仓内（两份副本逐字节相同）；上游 tag 待核 | **59/59 精确** |
| civetweb | 1.16 | **2** | `civetweb.o`=ELF，`CivetServer.o`=bitcode | MIT | tag `v1.16` = `d7ba35bb…`，tarball `f0e471c1…`（12,094,780 B） | **2/2 精确** |
| libzip | 1.0.1 | **100** | 全部 ELF | zlib-style | tag `rel-1-0-1` = `6d7dce82…`，tarball `0cef914d…`（659,452 B） | **100/100 精确** |

合计 **161 个成员**（归档侧 1,983,864 B），入库源码 **291 个文件 / 5,624,593 B（5.36 MB）**。

## 2. 三个库的判据强度不一样，不要一概而论

| 库 | 能声明的等价强度 | 为什么 |
|---|---|---|
| libzip | **字节级**（100/100 `sha256` 相同） | 101 个第三方 ELF 的 `.comment` 与本机 pin 的 clang（`10552028`）逐字节相同，参数可反推 |
| civetweb | `civetweb.o` **字节级**；`CivetServer.o` 符号级 | 前者是 ELF，后者是 bitcode（见下） |
| asmjit | **符号级**（59/59），字节不可能相同 | 归档 bitcode 的 `!llvm.ident` 是 `11349228`，本机是 `10552028`：两个不同 NDK 点版本 |

**这是本目录最重要的一条口径**：归档不是一次编译出来的，而是**两套 NDK 点版本 /
两套 flag 拼起来的**——131 个 ELF 成员用 `10552028` + `-O2 -fstack-protector-strong`，
127 个 bitcode 成员用 `11349228` + `-Oz -flto`。所以：

- 对 ELF 成员，判据可以是字节；
- 对 bitcode 成员，字节必然不同，**唯一正确的判据是符号面**（这也是 `verify_vendor.py`
  的默认判据，字节只作为参考信息记录）。

## 3. 本地补丁（必须保留，不要"清理"）

| 库 | 补丁 | 事实 |
|---|---|---|
| civetweb | `include/civetweb.h` 比上游 v1.16 多 **6 个 `#define`**（`NO_SSL`/`NO_AUTH`/`NO_CGI`/`NO_WEBSOCKET`/`NO_LUA`/`NO_DAV`） | 【实测】CRLF 归一化后与上游 diff **只剩 1 个 hunk**；其中只有 `NO_SSL`、`NO_CGI` 真实生效，另 4 个在该版本里 0 处引用（无操作）。`include/CivetServer.h` 与上游 **0 hunk** |
| libzip | `config/config.h`、`config/zipconf.h` 是**构建生成物**，不是上游文件 | `zipconf.h` 选 autoconf/`make_zipconf.sh` 风味（有符号 `INT64_MIN`），理由见该文件头注释与 `libzip/1.0.1/README.md` §3 |
| asmjit | 无补丁；但源码树是"仅编译所需"的裁剪副本 | 只有 `LICENSE.md` + `src/asmjit/**`（152 文件），无 `test/`、`tools/`、CMake |

删掉 civetweb 那 6 行宏，符号面会立刻改变（`NO_SSL`/`NO_CGI` 关掉的代码会回来）。

## 4. 校验

每个版本目录内都有 `SHA256SUMS`（逐文件、`sha256sum -c` 可用）：

| 目录 | 行数 | `SHA256SUMS` 文件自身的 sha256 |
|---|---:|---|
| `asmjit/1.16.0` | 152 | `30de07c4cf707b1de99285001dddb8b65309b71be9b20ca0906f85e879f728ae` |
| `civetweb/1.16` | 21 | `6207afe40b37c5033ce43cb352141b4b4b46720586ebb30a5d770cf409aa1608` |
| `libzip/1.0.1` | 115 | `700b1ccb27d70223cfffc4eb03f217a367bbd244eb502e0ffc476946d446c0bb` |

**覆盖范围（重要）**：`SHA256SUMS` 只列**上游派生文件**（源码、`LICENSE`、libzip 的
`config/*.h`），**不包含**本工作包新增的 `README.md`（那是说明文档，不是构建输入）。
这样 asmjit 的清单才能与仓内来源副本**逐行对照**（152 行 = 来源副本 152 文件）。
三个目录 `sha256sum -c` 实测：**152/152、21/21、115/115 全 OK，0 FAILED**。

```bash
cd runtime/upstream-pro/sdk/third_party/<lib>/<ver> && sha256sum -c SHA256SUMS
```

## 5. 门禁

```bash
py runtime/upstream-pro/sdk/tools/verify_vendor.py            # 全量 161
py runtime/upstream-pro/sdk/tools/verify_vendor.py --only libzip
```

输出 `sdk/build/report/vendor-gate.json`。当前结论：

```
members=161 OK=134 RESIDUAL=27 MISMATCH=0 BUILD-FAIL=0 byteIdentical=101
export-set exact: 161/161   import-set exact: 134/161   library-owned residuals: 0
VERDICT: PASS
```

`RESIDUAL` 表示**导入集**（未定义符号）有差异，但差异符号**没有一个是库自有符号**，
全部是 bionic/sysroot 版本差异（`__*_chk` FORTIFY 运行时、`open`↔`open64` LFS 重定向、
`snprintf`↔`vsnprintf` 等），逐条列在 `vendor-gate.json` 的 `residualSymbols` 里。

原因很硬：本机 NDK 26.1 的 `string.h` 里根本没有 `__memcpy_chk` 包装
（实测 `grep -c` = 0），所以 `__*_chk` 类符号**无法**用任何 flag 造出来。
它与源码是否正确无关。

## 6. 不许做的事

- 不要把 `THIRD-PARTY-PLAN.md` 之外的新版本源码加进来（磁盘上还有 20 个 libzip 候选
  tarball，只允许 1.0.1 入库）。
- 不要为了"瘦身"给 asmjit 加 `-DASMJIT_NO_X86` / `-DASMJIT_NO_LOGGING`：实测这两种
  宏会把对应 TU 的 `T` 集打空（`logger.o`：20 → **0**；`x86assembler.o`：9 → **0**；
  归档两者分别为 20 / 9），符号面立刻对不上。归档里**带着 x86 后端**，这是原始构建的选择。
- 不要把 `ASMJIT_ABI_NAMESPACE` 改成别的值：实测归档 526/526 个符号都在
  `asmjit::v1_16` 下，换串就全错。
