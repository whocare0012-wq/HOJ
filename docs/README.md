# 项目文档

本 fork 通过 GitHub Markdown 维护文档。请从 [部署与更新教程](./docs/deploy/optimized.md) 和 [项目首页](../README.md) 开始阅读。

`docs/docs/` 中保留了原版 HOJ 的历史文档与静态资源，供查阅原有功能；其中部分旧页面描述的是上游镜像与配置。当前 fork 不发布独立的 VuePress 文档站，因此移除了旧 VuePress 1.x 的包清单与锁文件。需要恢复文档站时，应重新选用受维护的构建工具并验证内容，不要直接使用历史依赖。

[历史升级记录](./upgrade-notes/README.md) 描述过去的开发阶段，不能替代当前部署步骤。所有公开文档都不得包含题目包、用户数据、密码或生产配置。
