# HOJ优化版

基于 [HimitZH/HOJ](https://github.com/HimitZH/HOJ) 的社区改进版本。原版是 Himit_ZH 等贡献者开发的 Hcode Online Judge；本仓库是 [公开 fork](https://github.com/whocare0012-wq/HOJ)。原有代码仍归原作者及贡献者所有，本 fork 的新增和修改部分由相应贡献者负责。保留原项目的 [MIT 许可证](./LICENSE) 和版权声明。

[![CI](https://github.com/whocare0012-wq/HOJ/actions/workflows/upgrade-ci.yml/badge.svg)](https://github.com/whocare0012-wq/HOJ/actions/workflows/upgrade-ci.yml)

> Blockly 是生成 Python 代码的前端编辑模式，并非新增的独立判题语言。

[English](./README-EN.md) · [部署步骤](#部署步骤) · [变更记录](./CHANGELOG.md) · [问题反馈](https://github.com/whocare0012-wq/HOJ/issues/new/choose) · [交流讨论](https://github.com/whocare0012-wq/HOJ/discussions) · [原版项目](https://github.com/HimitZH/HOJ)

## 与原版的主要区别

| 方向 | 原版基础 | 本版改进 |
| --- | --- | --- |
| 前端 | Vue 2 构建的 OJ 界面 | 迁移至 Vue 3 与 Vite，并更新相关组件及依赖 |
| 入门编程 | 文本代码编辑 | 增加 Blockly 积木编辑和独立窗口；生成 Python 代码，支持复制与按 Python 提交 |
| 题目难度与学习 | 原有难度、训练和题目功能 | 增加可配置难度等级、OJ 积分、学习资源、每日签到与运势功能 |
| 答题辅助 | 原有题目页与判题能力 | 增加受权限和次数限制的 AI 题目助手；API Key 由部署方私下配置 |
| 题目导入与数据安全 | 原有远程评测和管理功能 | 调整 AtCoder 导入为本地题目并使用题面样例；完善比赛题目移除时的数据保护与相关校验 |
| 运行维护 | 原版部署说明 | 补充本地验证脚本、数据库迁移和更谨慎的分服务更新流程 |

以上仅列出本版实现中可找到对应代码的改动。原版已有的语言、评测模式、比赛、团队、讨论和远程评测功能归功于原项目。功能仍须在实际部署环境中验证。

## 代码结构

- `hoj-vue/`：Vue 3 前端和 Blockly 编辑器。
- `hoj-springboot/`：后端、判题服务与共享 API，使用 Java 8。
- `sandbox/`：判题沙箱源码。
- `sqlAndsetting/`：建表、通用初始化数据和迁移脚本。
- `scripts/`：开发与验证脚本；其中本地栈脚本面向隔离测试环境。
- `docs/`：[项目文档](./docs/README.md)及[历史升级记录](./docs/upgrade-notes/README.md)。

## 部署步骤

1. 准备 JDK 8、Maven、Node.js 20.9+、Docker 和 Compose v2，获取源码并构建：

   ```bash
   git clone https://github.com/whocare0012-wq/HOJ.git
   cd HOJ/hoj-springboot
   mvn -B -ntp -pl DataBackup,JudgeServer -am package -DskipTests=true
   cd ../hoj-vue
   npm ci
   npm run build
   ```

2. 在独立部署目录配置 MySQL 8、Redis、Nacos、后端、JudgeServer、sandbox 和前端。将构建出的 `hoj-springboot/DataBackup/target/hoj-backend-4.6.jar`、`hoj-springboot/JudgeServer/target/hoj-judgeServer-4.6.jar` 和 `hoj-vue/dist/` 放入自己的镜像或挂载目录，并配置持久化卷、域名、HTTPS 和私有密钥。本仓库没有可直接运行的生产 Compose；可参考 [HOJ-Deploy](https://github.com/HimitZH/HOJ-Deploy) 的服务结构，但原版镜像不包含本版改动。
3. **仅首次安装且数据库为空时**，导入 `sqlAndsetting/hoj.sql`；在私有副本中替换 `sqlAndsetting/nacos.sql` 的默认密码和地址后再导入。已有站点只执行所需增量迁移，不要重新导入 `hoj.sql`。
4. 核对部署配置、挂载和网络后，在自己的部署目录启动 HOJ 服务；打开站点并实际提交一道题，确认前端、后端和判题正常。
5. 更新已有站点时，先保存旧产物；涉及后端或数据库时先做新的 MySQL 全库备份，再按变更范围更新对应的 HOJ 服务。保留现有 Nginx 配置、证书和其他项目容器，更新后重复第 4 步的检查。

## 参与和支持

发现错误请提交 [Issue](https://github.com/whocare0012-wq/HOJ/issues/new/choose)，使用问题可在 [Discussions](https://github.com/whocare0012-wq/HOJ/discussions) 交流。代码贡献见 [CONTRIBUTING.md](./CONTRIBUTING.md)，私密漏洞报告见 [SECURITY.md](./SECURITY.md)，其他支持方式见 [SUPPORT.md](./SUPPORT.md)。

## 公开仓库的数据边界

只发布源码、通用配置模板、建表与迁移脚本、公开文档。题目包与测试数据、用户账号和密码、数据库备份、上传文件、私有 `.env`、证书和浏览器测试产物不属于公开仓库内容。`.gitignore` 已排除常见本地产物；每次提交前仍应逐项检查暂存区。公开的初始化 SQL 不包含管理员账号，首个管理员在私有部署环境中创建。

## 许可证与致谢

本项目沿用 [MIT License](./LICENSE)。感谢 [HOJ 原作者与贡献者](https://github.com/HimitZH/HOJ/graphs/contributors)；使用或再分发时保留原版权和许可声明。
