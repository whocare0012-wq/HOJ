# 参与贡献

感谢为 HOJ优化版提交改进。本项目是 [HimitZH/HOJ](https://github.com/HimitZH/HOJ) 的 fork；请保留原有版权与 MIT 许可声明，并说明改动解决的问题。

## 反馈与提案

- 程序错误和可复现的功能问题：提交 [Issue](https://github.com/whocare0012-wq/HOJ/issues/new/choose)。
- 使用问题与开放式提案：使用 [Discussions](https://github.com/whocare0012-wq/HOJ/discussions)。
- 安全漏洞：按 [安全政策](./SECURITY.md)私下报告，不要公开漏洞细节。

请先搜索是否已有相同问题。描述预期与实际行为、最小复现步骤、系统和浏览器版本；必要时附经过匿名化处理的截图或日志。

## 提交代码

1. 从最新 `master` 建立分支，让一次 PR 只解决一个明确问题。
2. 保留现有代码风格；行为变更应补充能验证风险的测试或可复现的手工步骤。
3. 前端在 `hoj-vue/` 运行 `npm ci`、`npm audit --audit-level=high`、`npm run build`；后端在 `hoj-springboot/` 运行 `mvn -B -ntp test -DskipTests=false`。
4. 数据库变更应提供增量迁移、回滚或兼容说明，并更新 `sqlAndsetting/PRODUCTION_UPGRADE.md`。不要把 `hoj.sql` 用于现有数据库。
5. 提交前检查 `git status --short`、`git diff --cached --name-only` 和 `git diff --cached --check`，再填写 PR 模板。

只提交源码、通用脚本与公开文档。不要提交题目或测试数据、账号、密码、Token、API Key、数据库备份、上传文件、证书、含个人信息的截图或实际生产配置。示例须使用虚构数据和占位符。

本仓库的本地验证脚本可能需要隔离环境；向线上 HOJ 或其他项目部署不属于 PR 的自动步骤。
