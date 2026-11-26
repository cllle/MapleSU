# 模块仓库上传与维护

MapleSU Ultra 模块市场由贡献者共同维护，目录与安装包独立于 Manager 发布。主仓库维护应用与接口，市场仓库维护条目、版本、来源、校验数据和目录签名。

社区仓库：[maplesu-module-market](https://github.com/FengQiuFQ/maplesu-module-market)。模块开发入口：[脚本与 WebUI](MODULES.md)、[Ultra 原生模块](ULTRA_MODULES.md)。

## 提交一个模块

1. Fork 市场仓库，准备原始 ZIP、对应版本的源码、许可证和发布说明。
2. 声明真实作者、维护账号、仓库地址、模块 ID、版本和兼容模式。转载需要明确再分发依据。
3. 使用仓库工具导入。原生模块填写库内真实 ID 和成员名，脚本模块从根目录 `module.prop` 读取身份。
4. 在 `maintainers.json` 登记维护与来源信息，运行本地检查。
5. 通过 PR 提交包、索引、来源和更新记录；不包含市场签名私钥，不执行包内代码作为投稿检查。

也可使用“模块投稿”Issue 提交材料。模块由用户贡献、上传并维护；如涉及侵权，请在市场仓库提交 Issue 申请删除。

## 导入命令

以下命令在市场仓库根目录运行。

脚本模块示例：

```shell
python -m pip install -r requirements.txt
python tools/market.py add-local --package example-module.zip --release-date 2026-11-11
```

Ultra 原生模块示例；将 ID、成员名和日期替换为实际包的信息：

```shell
python tools/market.py add-local --package example-module.zip --native-id a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6 --native-member libmodule_example.so --release-date 2026-11-11
```

原生模块的市场元信息位于 `payload/module.prop`，新目录使用 `MAPLESU` 类型；Manager 继续接受已发布目录中的旧类型名。模块接口见[Ultra 原生模块开发](ULTRA_MODULES.md)，底层组件来源见[致谢](../CREDITS.md)。

导入之后运行：

```shell
python tools/market.py verify --allow-unsigned
python tools/check_maintainers.py
python tools/check_publish_tree.py
python -m unittest discover -s tools -p "test_*.py"
```

导入改变目录内容后旧签名失效，PR 阶段以 `--allow-unsigned` 检查结构和包完整性。最终签名与公钥验证由维护者在发布前完成。

## 更新与交接

同一模块复用原 ID，发布新版本和变更说明；更新当前版本时显式使用 `--select-latest`。同 ID、同版本的不同字节会被拒绝，修订应使用新版本。保留旧版本、源码索引和原作者署名。

每个条目可以登记多位维护者。变更维护人时提交 PR 并提供原作者或现任维护者的交接依据；首次认领已有收录项也通过同一流程。目录签名权不会因为条目登记而下放。

## 来源、下架与发布

来源记录至少关联实际版本的源码与许可。仓库工具自身的许可证不覆盖上传模块。来源有待确认的包保留在私有审阅材料中，确认后再进入公共分发；目录来自公开服务不自动构成再分发依据。

侵权问题通过 `COPYRIGHT.md` 和专用 Issue 模板处理。确认后撤下公开索引和受影响发布物，保留必要的内部处理记录，不把更换包名或删除原作署名作为处理方式。

合并后的目录由维护者签名，验证清单原始字节及所有包摘要，再导出同一静态树到主备源。完整命令和 Pages 配置见市场仓库 `PUBLISH.md`。个人开发者也可以维护独立 HTTPS 源，并在应用中设置对应地址与公钥。
