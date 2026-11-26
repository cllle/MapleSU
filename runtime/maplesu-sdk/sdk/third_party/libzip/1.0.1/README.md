# libzip 1.0.1 — vendored source

`source=vendored`。本目录是冻结归档里 **100 个 libzip 成员**
（99 个 `zip_*.o` + `mkstemp.o`，全部 native ELF）的源码来源。

> 入库前仓库内**没有任何 libzip 源码**（`zip_open.c`/`zipint.h`/`zip.h` 全仓 0 命中）。
> 源码原在仓库外 `/d/sdk-recon-work/vendor/libzip-1.0.1/`；本工作包已把它搬进仓库，
> **入库后不再回指任何仓库外路径**。

## 1. 获取方式与校验锚点（本次已重做一遍）

| 项 | 值 |
|---|---|
| 官方下载 | `https://libzip.org/download/libzip-1.0.1.tar.gz` |
| tarball sha256（**本次重新下载实测**） | `0cef914d8f5105ccac44484f61413daa08d7204758e77240a4d20ebf967f3d49` |
| tarball 字节数（实测） | `659,452` |
| 上游仓库 | <https://github.com/nih-at/libzip> |
| tag | **`rel-1-0-1`**（注意：**不叫** `v1.0.1`；`git ls-remote` 里不存在 `v1.0.1`） |
| tag commit（`git ls-remote` 实测） | `6d7dce82ae16ca5a4c51b685477f3043e83d5f89` |
| 许可 | zlib-style（`LICENSE`，1452 B，首行 `Copyright (C) 1999-2014 Dieter Baron and Thomas Klausner`） |

> 说明：tarball 的 sha256 是**重新下载后**与既有记录逐字节比对得到的，不是转抄。
> 解包后再把 `lib/**` 与 `LICENSE` 与入库副本逐文件 `sha256sum` 比对：
> **112/112 文件完全一致**（见 §6 复现命令）。

## 2. 目录内容

| 路径 | 内容 | 来源 |
|---|---|---|
| `lib/**` | **112 个文件**（104 个 `.c` + `zip.h` + `zipint.h` + `zipwin32.h` + 其它头） | 上游 tarball 原样 |
| `LICENSE` | 上游许可 | 上游 tarball 原样 |
| `config/config.h` | 构建生成头（`#define VERSION "1.0.1"`、一套 `HAVE_*`） | **非上游文件**，见 §3 |
| `config/zipconf.h` | 构建生成头（`SIZEOF_OFF_T`、`ZIP_INT64_*` 等） | **非上游文件**，见 §3 |

刻意不收录：`regress/`、`man/`、`docs/`、`examples/`、`src/`（`zipcmp`/`zipmerge`/`ziptool` 命令行工具）、
`xcode/`、autotools/CMake 脚手架、以及 4 个 Windows 专用 `lib/zip_source_win32*.c`。

## 3. `config/` 是构建生成物，不是上游文件

`config.h` / `zipconf.h` 由原构建过程生成，必须随源码一起入库（否则编不出与归档同形的对象），
但**不能**被当成上游内容去 diff。两者原样搬运自
`/d/sdk-recon-work/sdk-rebuild/elf/build-headers/`，入库后不再依赖该路径。

| 文件 | sha256 |
|---|---|
| `config/config.h` | `8fca88658f85e865d9fb99c0c8a0a32f3bc2fa64e856739fefeda9eb43977f5e` |
| `config/zipconf.h` | `772518cac8e39f2c59686c74067d49207de85726170245732e2e1d73eb6f8153` |

`zipconf.h` 选用的是 **autoconf / `make_zipconf.sh` 风味**，不是 CMake 风味。
该选择的理由写在 `config/zipconf.h` 文件头注释里（原文保留）：CMake 风味把
`ZIP_INT64_MIN` 定义成 `-0x8000000000000000LL`（该常量类型是 `unsigned long long`），
会让 `zip_source_filep.c` 里的 `offset < ZIP_INT64_MIN` 退化成恒真的无符号比较；
只有 `INT64_MIN`（来自 `<stdint.h>`，有符号）才能让该范围检查被正确折叠。
这一条与本工作包的实测一致：用该套 `config/` 编译时，`zip_source_filep.o` 等成员的符号面与归档相同。

## 4. 版本判据：为什么是 1.0.1（文件集合双向差集，最强判据）

【实测】归档 100 个成员 basename 与 `lib/*.c` 的**双射**关系：

```
归档成员（去掉 .o，补 .c） = 100
libzip 1.0.1 的 lib/*.c    = 104
归档有 · 上游无  = 0
上游有 · 归档无  = zip_source_win32a.c  zip_source_win32handle.c  zip_source_win32utf8.c  zip_source_win32w.c
```

即 **100 = 104 − 4 个 Windows 专用源**，双向差集完全可解释、无剩余。
这不是"名字看着像"：是**精确的 100↔100 一一对应**（`build_rules.json` 的 `members`
就是从这层对应生成的，任何一项对不上都会在 `verify_vendor.py` 里报出来）。

反向排除：1.2.0 的 `lib/*.c` 为 113（差 13），1.3.0 为 116 且开始出现"归档有·上游无"（差 2），
所以 1.0.1 是唯一"差异全为平台排除项"的候选。

## 5. 归档形态与编译参数（100/100 逐字节可复现）

【实测】100 个成员 magic 全为 `7f 45 4c 46`（ELF64 / AArch64），
`.comment` = `Android (10552028, based on r487747d) clang version 17.0.2`，
**与本机 pin 的 clang 逐字节相同**，且全部无 DWARF。

因此本库可以做到**字节级**复现，而不只是符号级。参数是从归档对象反推并逐项验证的：

| 参数 | 判定依据（实测） |
|---|---|
| `-O2` | `zip_open.o` 在 `-Oz/-O1/-O2` 下体积为 15,192 / 13,696 / **13,400**；带 `-O2` 全套参数编译得 **13,656 = 归档字节数**，且 `cmp` 0 字节差异 |
| `-fstack-protector-strong` | 归档 `U` 集含 `__stack_chk_fail`；不加则缺该符号 |
| `-D_FORTIFY_SOURCE=2` | 归档 `U` 集与不带该宏时差 1 个 libc 符号；带宽后精确一致 |
| `-ffunction-sections -fdata-sections` | 归档对象有 `.text._zip_open` 等逐函数段 + 对应 `.rela.text.*`（`zip_open.o` 14 个 `.text.*`，与带该 flag 的重建一致；`-Oz` 下会变成 29 个） |
| `-DHAVE_CONFIG_H -I lib -I config` | 无 `-DHAVE_CONFIG_H` 时 `config.h` 不生效，符号面立即改变 |
| `mkstemp.c` 额外 `-include unistd.h` | `lib/mkstemp.c` 调用 `getpid()` 却没 `#include <unistd.h>`，直接编会报未声明函数；归档 `mkstemp.o` 确实引用 `getpid`，说明原构建让该头可见 |

> `-Oz` 已被**否证**（体积与段数都显著偏大，`zip_open.o` 会变成 15,192 B / 29 段）。
> 这也说明归档第三方 ELF 与 `build.py` 现有 `-Oz` 不是同一套 flag —— 本库因此使用
> 自己的 `vendor-c-elf` profile，见 `third_party/build_rules.json`。

## 6. 门禁结果与复现命令

| 项 | 结果 |
|---|---|
| 成员数 | 100 |
| 导出集（`T`）双向差集 | **100/100 精确为空** |
| 导入集（`U`）双向差集 | **100/100 精确为空** |
| 字节一致 | **100/100**（`sha256` 与归档成员逐字节相同） |
| 结论 | **PASS** |

复现：

```bash
# 1) tarball 锚点
curl -sSLo /tmp/libzip-1.0.1.tar.gz https://libzip.org/download/libzip-1.0.1.tar.gz
sha256sum /tmp/libzip-1.0.1.tar.gz      # 0cef914d8f5105ccac44484f61413daa08d7204758e77240a4d20ebf967f3d49

# 2) 入库树与 tarball 逐文件一致
tar xzf /tmp/libzip-1.0.1.tar.gz -C /tmp && \
  (cd /tmp/libzip-1.0.1/lib && find . -type f -print0 | sort -z | xargs -0 sha256sum) > /tmp/a
(cd runtime/upstream-pro/sdk/third_party/libzip/1.0.1/lib && \
   find . -type f -print0 | sort -z | xargs -0 sha256sum) > /tmp/b
diff /tmp/a /tmp/b && echo IDENTICAL

# 3) 全量门禁（含字节一致判定）
py runtime/upstream-pro/sdk/tools/verify_vendor.py --only libzip
```

## 7. 未定项

| # | 项 | 状态 |
|---|---|---|
| U1 | 原构建让 `unistd.h` 可见的**具体**方式（`-include`？别的 `config.h`？`-D_GNU_SOURCE`？） | 【未能确认】归档无 DWARF、无 flag 痕迹；`-include unistd.h` 是能复现出**逐字节相同** `mkstemp.o` 的最小做法 |
| U2 | 归档 `mkstemp.o` 的行号疑点（旧笔记称起始行不匹配任何已发布版本） | 【不采用该判据】归档 100/100 ELF 全无 `.debug*` 段，行号无从谈起；以文件集合 + 符号面 + 字节一致替代 |
