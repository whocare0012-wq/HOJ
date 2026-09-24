# HOJ优化版

基于 [HimitZH/HOJ](https://github.com/HimitZH/HOJ) 的社区改进版本。原版是 Himit_ZH 等贡献者开发的 Hcode Online Judge；本仓库是 [公开 fork](https://github.com/whocare0012-wq/HOJ)。原有代码仍归原作者及贡献者所有，本 fork 的新增和修改部分由相应贡献者负责。保留原项目的 [MIT 许可证](./LICENSE) 和版权声明。

[![CI](https://github.com/whocare0012-wq/HOJ/actions/workflows/upgrade-ci.yml/badge.svg)](https://github.com/whocare0012-wq/HOJ/actions/workflows/upgrade-ci.yml)

> Blockly 是生成 Python 代码的前端编辑模式，并非新增的独立判题语言。

[English](./README-EN.md) · [部署与更新教程](./docs/docs/deploy/optimized.md) · [变更记录](./CHANGELOG.md) · [问题反馈](https://github.com/whocare0012-wq/HOJ/issues/new/choose) · [交流讨论](https://github.com/whocare0012-wq/HOJ/discussions) · [原版项目](https://github.com/HimitZH/HOJ)

## 与原版的主要区别

| 方向 | 原版基础 | 本版改进 |
| --- | --- | --- |
| 前端 | Vue 2 构建的 OJ 界面 | 迁移至 Vue 3 与 Vite，并更新相关组件及依赖 |
| 入门编程 | 文本代码编辑 | 增加 Blockly 积木编辑和独立窗口；生成 Python 代码，支持复制与按 Python 提交 |
| 题目难度与学习 | 原有难度、训练和题目功能 | 增加可配置难度等级、OJ 积分、学习资源、每日签到与运势功能 |
| 答题辅助 | 原有题目页与判题能力 | 增加受权限和次数限制的 AI 题目助手；API Key 由部署方私下配置 |
| 题目导入与数据安全 | 原有远程评测和管理功能 | 调整 AtCoder 导入为本地题目并使用题面样例；完善比赛题目移除时的数据保护与相关校验 |
| 运行维护 | 原版部署说明 | 补充本地验证脚本、数据库迁移和更谨慎的分服务更新流程 |

以上仅列出本版实现中可找到对应代码的改动。原版已有的语言、评测模式、比赛、团队、讨论和远程评测功能归功于原项目。功能在具体环境中仍须按部署教程验证。

## 代码结构

- `hoj-vue/`：Vue 3 前端和 Blockly 编辑器。
- `hoj-springboot/`：后端、判题服务与共享 API，使用 Java 8。
- `sandbox/`：判题沙箱源码。
- `sqlAndsetting/`：建表、通用初始化数据和迁移脚本。
- `scripts/`：开发与验证脚本；其中本地栈脚本面向隔离测试环境。
- `docs/`：项目文档及[历史升级记录](./docs/upgrade-notes/README.md)。

## 快速验证源码

```bash
git clone https://github.com/whocare0012-wq/HOJ.git
cd HOJ/hoj-vue
npm ci
npm audit --audit-level=high
npm run build
cd ../hoj-springboot
mvn -B -ntp -pl DataBackup,JudgeServer -am test -DskipTests=false
```

这些命令验证源码和构建，不会启动完整 OJ。完整站点需要独立的 MySQL、Redis、Nacos、判题服务和私有配置，见下方教程。请勿使用生产题目或账号作为公开示例。

## 构建与部署

前端要求 Node.js 20.9+；后端使用 JDK 8 与 Maven。构建命令、首次安装、已有站点更新、迁移、验证和回滚见 [部署与更新教程](./docs/docs/deploy/optimized.md)。本仓库没有可直接替换现有生产配置的完整 Compose 文件；不能把原版镜像更新命令当成本 fork 的发布命令。

## 参与和支持

发现错误请提交 [Issue](https://github.com/whocare0012-wq/HOJ/issues/new/choose)，使用问题可在 [Discussions](https://github.com/whocare0012-wq/HOJ/discussions) 交流。代码贡献见 [CONTRIBUTING.md](./CONTRIBUTING.md)，私密漏洞报告见 [SECURITY.md](./SECURITY.md)，其他支持方式见 [SUPPORT.md](./SUPPORT.md)。

## 公开仓库的数据边界

只发布源码、通用配置模板、建表与迁移脚本、公开文档。题目包与测试数据、用户账号和密码、数据库备份、上传文件、私有 `.env`、证书和浏览器测试产物不属于公开仓库内容。`.gitignore` 已排除常见本地产物；每次提交前仍应逐项检查暂存区。公开的初始化 SQL 不包含管理员账号，首个管理员在私有部署环境中创建。

## 许可证与致谢

本项目沿用 [MIT License](./LICENSE)。感谢 [HOJ 原作者与贡献者](https://github.com/HimitZH/HOJ/graphs/contributors)；使用或再分发时保留原版权和许可声明。
