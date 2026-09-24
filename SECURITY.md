# 安全政策

## 报告漏洞

请打开本仓库的 [Security Advisories](https://github.com/whocare0012-wq/HOJ/security/advisories) 页面，点击 **Report a vulnerability** 私下提交。不要在公开 Issue、Discussion 或 PR 中披露可利用细节、真实密钥、用户数据或生产数据库内容。

报告中请说明受影响版本、复现条件、可能的影响和建议修复方式，并尽量使用合成数据。维护者会在私密报告中沟通确认与修复安排。

## 支持范围

当前主要维护 `master` 分支的本 fork 改动；尚未发布独立的稳定版本或安全支持周期。原版 HOJ 的问题可同时参阅 [上游项目](https://github.com/HimitZH/HOJ)。

仓库启用了 Dependabot 告警和安全更新。继承的部分 Java 依赖仍需逐项升级与兼容性验证；使用者在生产部署前应审查自己实际打包的依赖和部署环境。
