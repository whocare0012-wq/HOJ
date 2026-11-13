# HOJ 优化版：构建、部署与更新

本教程适用于本仓库源码。操作命令以 Linux 服务器、Docker Compose v2 为例。`hoj-vue/`、`hoj-springboot/` 和 `sqlAndsetting/` 是代码与通用数据库脚本；本仓库**没有完整的生产 Compose 文件、镜像构建文件或私有配置**。原版 [HOJ-Deploy](https://github.com/HimitZH/HOJ-Deploy) 可作为服务拓扑参考，但其镜像是原版，直接拉取不会包含本 fork 的功能。上线前须按实际镜像、容器挂载、Nginx 配置完成适配并在隔离环境验证。

## 1. 准备与构建

- 准备 JDK 8、Maven、Node.js 20.9+、Docker 与 Compose v2，以及适合 HOJ 的 MySQL 8、Redis、Nacos、后端、JudgeServer、sandbox、前端环境。
- 在独立目录检出本仓库。私有密码、Token、API Key、证书和数据库备份放在仓库外；本地 `.env` 也不要提交。
- 保留 `LICENSE` 和原作者声明。本项目的显示名称可写为“HOJ 优化版”，但仓库和包名仍兼容原版。

在源码根目录构建：

```bash
cd hoj-springboot
mvn -B -ntp -pl DataBackup,JudgeServer -am package -DskipTests=true
cd ../hoj-vue
npm ci
npm run build
```

产物应为 `hoj-springboot/DataBackup/target/hoj-backend-4.6.jar`、`hoj-springboot/JudgeServer/target/hoj-judgeServer-4.6.jar` 和 `hoj-vue/dist/`。执行构建不会自动发布。验证构建产物及本次 Git 提交对应版本，保留一份可回滚的旧产物。

## 2. 全新隔离安装

1. 在**新建、专用**的部署目录获取 [原版部署模板](https://github.com/HimitZH/HOJ-Deploy)，逐项阅读其 Compose、容器入口、镜像、网络、卷、端口和 Nginx 配置。按本仓库构建产物制作或挂载自己的后端 JAR、JudgeServer JAR 和前端 `dist/`；确认容器内启动路径与 Nginx 静态根目录。不要保留指向原版镜像的服务却误认为已经部署了本 fork。
2. 在部署目录创建**私有**环境配置，分别生成 MySQL、Redis、Nacos、JWT、判题通信等随机密钥。生产环境不得沿用示例密码。`AI_ASSISTANT_KEY_ENCRYPTION_SECRET` 至少 32 个字符，并在后续升级中保持不变，否则已有加密 API Key 可能无法解密。不要把 `docker compose config` 的完整输出贴到公开日志或工单，因为它可能展开密钥。
3. 为 MySQL、上传文件和测试数据设置持久化卷，并核对备份与恢复路径。只为必要端口配置访问权限。部署独立站点前检查端口冲突和其他容器；不要覆盖别的应用的 Nginx 站点、证书或网络。
4. 对**空数据库**执行 `sqlAndsetting/hoj.sql` 一次。该脚本包含 `DROP TABLE`，绝不能用于已有数据的数据库。`sqlAndsetting/nacos.sql` 是原版示例配置，含已公开的默认值；先在私有副本中替换全部密码、Token、主机地址，再导入，且不要提交替换后的副本。
5. 检查 Compose 解析后的服务清单、镜像来源、入口命令、挂载、网络和健康检查。在独立环境中启动所需 HOJ 服务，观察后端、JudgeServer、MySQL、Nacos 和 Nginx 日志，确认页面及实际提交评测可用。原版部署模板可能含自动数据库初始化和默认账号，须先核实并关闭或移除这些逻辑，避免覆盖本仓库的无账号种子。
6. 公开种子中没有管理员账户。先在私有配置中启用邮件发送和注册，确保邮箱验证码能正常送达，再在自己的站点注册首个账号。使用数据库交互式客户端查出并人工核对该用户的 `user_info.uuid`，为这个 UUID 在 `user_role` 增加 `role_id=1000`（`root`）记录。具体用户名、UUID 和授权操作记录只保存在私有运维位置；不要把账号、密码、密码散列或生产数据库导出放进仓库。核验管理员登录后关闭临时开放的注册入口。

本仓库目前没有一键冷启动的生产编排。首次安装必须先完成上述镜像与配置适配，并在隔离环境走通；不能把原版教程的一条 `up -d` 当作本 fork 的完整部署方法。

## 3. 已有站点更新前的只读检查

先记录当前版本和可回滚产物，再检查：

- 主站和同机由该 Nginx 代理的其他域名的 HTTP 状态。
- HOJ 及同机其他项目各容器的 ID、状态和 `RestartCount`；不要为了检查而重启容器。
- 前端容器的两个实际网络、证书挂载、静态资源挂载、Nginx 生效配置及 SHA-256。
- 后端与 MySQL 最近错误日志、磁盘空间、比赛状态、文件和测试数据卷。

可以用 `docker ps --no-trunc`、`docker inspect`、`docker exec <frontend> nginx -T`、`sha256sum`、`curl -I` 等只读命令收集这些信息。按现场服务名和域名替换占位符；不要把包含密钥的 `docker inspect` 全量输出公开。

## 4. 按变更范围发布

### 仅前端

1. 本地运行 `npm ci && npm run build`，确认 `dist/` 与当前代码对应，计算产物校验值，备份现有 `index.html`、`assets/` 和完整 Nginx 配置。
2. 从容器或挂载中确认实际静态根目录。先复制新 `assets/` 并校验，最后在**同一文件系统**以临时文件加重命名原子替换 `index.html`。仅前端更新通常不需要重建容器。
3. 保留既有 Nginx 虚拟主机、HTTPS 证书和所有网络连接。若必须重建前端，只针对该服务操作，并在执行前逐项对比挂载与网络。
4. 检查主站、同机其他域名和前端功能；出错时恢复原 `index.html` 与旧静态资源。不要清理仍被旧页面引用的资源。

### 启用静态资源压缩（推荐）

前端构建会为 10 KB 以上的 JS/CSS 预生成 `.gz` 文件。若 Nginx 未开启压缩，首次访问需下载约 5 MB 的原始脚本；开启后约降到 1/3。在站点的 `server` 块中加入（示例见 `scripts/local-stack.nginx.conf`）：

```nginx
gzip on;
gzip_static on;
gzip_vary on;
gzip_proxied any;
gzip_comp_level 5;
gzip_min_length 1024;
gzip_types text/plain text/css application/javascript application/json application/x-ndjson image/svg+xml;
```

先 `nginx -t` 检查语法再 `nginx -s reload`。验证：`curl -sI -H "Accept-Encoding: gzip" https://<域名>/assets/<任一 index-*.js>` 应返回 `Content-Encoding: gzip`。

### 后端、JudgeServer 或数据库

1. **先创建新的 MySQL 全库备份**，使用唯一文件名，不覆盖旧备份；校验哈希并在隔离环境验证可恢复。比赛进行中不要把题目移出比赛。
2. 在数据库副本上按需执行 `sqlAndsetting/` 中的**增量迁移**，核对前置版本和效果。`hoj.sql` 只用于全新空库。`PRODUCTION_UPGRADE.md` 是迁移提示，实际 SQL 需逐个审核；不要盲目运行全部脚本。
3. 使用本次源码构建 JAR，在隔离环境完成 API、判题、登录与数据兼容性验证。再按现场容器入口和卷制作版本化镜像或替换版本化挂载产物。
4. 只更新涉及的 HOJ 服务。执行 Compose 操作前先确认目标项目、现有容器、挂载、网络、Nginx 配置和其他项目状态；不要在共享主机直接全栈 `pull/up -d`，也不要停止、重建无关项目容器。
5. 发布后重复第 3 节的检查，另核对新增功能的 API、数据库错误日志和提交评测。失败时按预先保存的镜像/产物和备份执行回滚。

### 后端就绪、功能与榜单性能校验

后端启动后、原子切换前端 `index.html` 前，执行只读校验：

```bash
python scripts/hoj_release_checks.py --base-url https://<域名> --output output/release-check-<发布号>.json
```

脚本只发送 GET 请求、生成本地诊断文件，不会发布、重启、改配置或回滚。保留原发布流程中的数据库备份、容器/挂载/网络核验、其他站点连续探测以及回滚步骤。

- 就绪阶段仅检查轻量网站配置接口，默认总期限 210 秒、每次请求超时 5 秒，需要连续两次成功。
- 功能阶段检查难度与积分榜响应结构，榜单分别记录首次和重复请求的耗时；断线等暂时故障最多额外尝试一次。
- 网络读取超时默认 30 秒，榜单性能预算仍为 10 秒。超时后重试成功也会保留首次失败并阻止自动切换前端，避免掩盖性能问题。可通过 `--request-timeout`、`--ranking-budget` 显式设置，报告会保留预算与实测数据。
- 返回码 `0` 表示全部通过，`2` 表示就绪/功能失败，`3` 表示功能已恢复但性能不达标。报告包含接口路径、HTTP/应用状态、失败分类和耗时，不输出响应正文或令牌。

积分榜查询复用积分迁移中已有的 `judge.idx_oj_points` 覆盖索引，上线前只读确认该索引存在。先算全站名次并筛选、分页，再统计当前页用户的提交数；积分按当前难度分值实时计算。SQL 变化可用显式启用的 `OjRankQueryIntegrationTest` 在本地一次性 MySQL 副本验证，覆盖并列、搜索、翻页、积分变更及真实量级数据；测试会核对本地数据库的 server UUID。发布校验自身的测试可运行：

```bash
python -m unittest discover -s scripts/tests -p 'test_hoj_release_checks*.py' -v
```

## 5. 公开仓库提交前检查

```bash
git status --short
git ls-files --others --exclude-standard
git diff --cached --name-only
git diff --cached --check
```

逐项审查**暂存文件内容**，确认没有题目包、测试点、账号、密码、Token、API Key、备份、私有域名配置、上传文件或证书。`.gitignore` 只保护未跟踪文件；已经跟踪、已经提交或历史提交中的内容不会因新增忽略规则而消失。如果发现真实密钥曾公开，需要在服务端轮换并评估历史清理。
