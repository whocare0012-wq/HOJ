# HOJ AI 数据接口与 MCP 服务器

让各种 AI agent 直接拉取学生的提交记录（含代码）用于分析，或按你的要求新增、修改题目与训练。由两部分组成：

- **REST 接口** `/api/agent/v1`：提交数据接口只读；`/api/agent/v1/admin/**` 为题目与训练管理接口。任何能发 HTTP 请求的工具都能用；附 OpenAPI 文档，可直接导入 Dify、Coze、GPTs Actions、LangChain 等。
- **MCP 服务器** `hoj-mcp.mjs`：把接口包装成工具，Claude Code、Claude Desktop、Cursor、Codex 等支持 MCP 的客户端可直接调用。零依赖，只需 Node.js 18+。

## 1. 生成令牌

用 **root** 账号登录后台 →「AI 数据接口」→「生成令牌」。令牌形如 `hoj_at_...`，只在生成时显示一次，请立即保存。

- 只有 root 能生成令牌；令牌创建者不再是启用的 root 时，其令牌立即失效。
- 生成时选择**权限范围**：`read`（读取提交数据）、`manage`（题目与训练管理），可同时勾选。权限不足的请求返回 403 `insufficient_scope`。升级前生成的令牌只有 `read`。
- 可设置有效期，可随时吊销；每次调用都会记录审计日志（页面下方可查看）。
- 每个令牌每分钟最多 120 次请求，批量数据请用 NDJSON 导出。
- **尚未结束的比赛中的提交不会返回。**
- 令牌可读取全站学生代码和实名信息，或以你的 root 身份修改题库，请像密码一样保管，不要提交到代码仓库。
- 管理权限需要先执行数据库升级脚本 `sqlAndsetting/hoj-agent-manage-update.sql`。

## 2. REST 接口

所有请求携带请求头 `Authorization: Bearer hoj_at_...`。下面以 `$HOJ=https://oj.example.com`、`$TOKEN=hoj_at_...` 为例。

| 接口 | 说明 |
|---|---|
| `GET /api/agent/v1/ping` | 检查令牌与连通性 |
| `GET /api/agent/v1/submissions` | 提交记录（默认含代码），支持分页与 NDJSON 导出 |
| `GET /api/agent/v1/submissions/{id}` | 单条提交 |
| `GET /api/agent/v1/students` | 按姓名/学号/用户名查找学生，或列出团队成员 |
| `GET /api/agent/v1/stats` | 统计：`group_by=user\|problem\|day\|status\|language` |
| `GET /api/agent/v1/openapi.json` | OpenAPI 3 文档（无需令牌） |

**筛选参数**（submissions 与 stats 通用，均可省略；多个值用英文逗号分隔或重复传参）：

| 参数 | 含义 |
|---|---|
| `username` / `uid` / `number` | 学生用户名 / uid / 学号 |
| `group_id` | 团队（班级）成员的全部提交 |
| `problem` | 题库题号，如 `P1001` |
| `contest_id` | 某场已结束比赛中的提交 |
| `status` | `AC, WA, CE, TLE, MLE, RE, PE, PA, SE, CA` 或数字状态码 |
| `language` | 如 `C++`、`Python3` |
| `date` | 某一天，如 `2026-11-01` |
| `from` / `to` | 时间范围；`to` 为纯日期时包含当天全天 |

submissions 额外参数：`include_code`（默认 true）、`limit`（默认 100，含代码最大 200，不含代码最大 500）、`cursor`、`order=asc|desc`、`format=json|ndjson`。

```bash
# 某个学生某天的全部提交（含代码）
curl -H "Authorization: Bearer $TOKEN" "$HOJ/api/agent/v1/submissions?username=zhangsan&date=2026-11-01"

# 一周内某班级每个学生的统计
curl -H "Authorization: Bearer $TOKEN" "$HOJ/api/agent/v1/stats?group_by=user&group_id=3&from=2026-11-01&to=2026-11-07"

# 按姓名或学号找学生
curl -H "Authorization: Bearer $TOKEN" "$HOJ/api/agent/v1/students?keyword=张三"

# 导出全部日期、全部学生的提交（每行一条 JSON）
curl -H "Authorization: Bearer $TOKEN" "$HOJ/api/agent/v1/submissions?format=ndjson" -o all-submissions.ndjson
```

**分页**：响应为 `{"data": [...], "count", "has_more", "next_cursor"}`，把 `next_cursor` 原样作为下次请求的 `cursor`，直到 `has_more` 为 false。按提交 ID 排序，新提交不断写入时也不会重复或遗漏；记住最后的 cursor，下次从这里继续就是增量同步。

**错误**：HTTP 状态码 + `{"error": {"code": "...", "message": "..."}}`。参数名写错会返回 400 并列出可用参数。

### 题目与训练管理（需要 `manage` 权限）

| 接口 | 说明 |
|---|---|
| `GET /api/agent/v1/admin/meta` | 可选的语言、标签、训练分类、难度与枚举值 |
| `GET /api/agent/v1/admin/problems` | 题目列表 / 搜索 |
| `GET /api/agent/v1/admin/problems/{problem_id}` | 题目完整信息（含 `version`） |
| `POST /api/agent/v1/admin/problems` | 新建题目（JSON，或 multipart 上传测试数据 zip） |
| `PATCH /api/agent/v1/admin/problems/{problem_id}` | 修改题目（只传要改的字段） |
| `GET /api/agent/v1/admin/problems/{problem_id}/testcases` | 测试点列表，`include_content=true` 返回内容 |
| `PUT /api/agent/v1/admin/problems/{problem_id}/testcases` | 整体替换测试数据 |
| `GET /api/agent/v1/admin/trainings` | 训练列表 |
| `GET /api/agent/v1/admin/trainings/{id}` | 训练详情（含题目顺序） |
| `POST /api/agent/v1/admin/trainings` | 新建训练，可同时加入题目 |
| `PATCH /api/agent/v1/admin/trainings/{id}` | 修改训练，`status=false` 即停用 |
| `POST /api/agent/v1/admin/trainings/{id}/problems` | 向训练添加题目 |
| `PATCH` / `DELETE /api/agent/v1/admin/trainings/{id}/problems/{problem_id}` | 调整排序 / 移出训练 |
| `PUT /api/agent/v1/admin/trainings/{id}/problems/order` | 按给定顺序重排全部题目 |

安全设计：

- **演练**：所有写接口支持 `dry_run=true`（查询参数或请求体字段），只做校验并返回 `changes`（逐字段 before/after）与 `warnings`，不写入。
- **并发保护**：读取时拿到的 `version` 在修改时作为 `expected_version` 传回，期间若有人在后台改过会返回 409 `version_conflict`，不会覆盖。
- **不能删除**：下线题目把 `auth` 设为 `private`，停用训练把 `status` 设为 `false`；远程 OJ 题目、团队题目与团队训练只读。
- **审计**：以令牌创建者的身份执行（题目的“最近修改人”为该账号），每次写入都在后台「AI 数据接口 → 最近变更」记录修改前后的内容。
- 校验、落库和测试数据初始化复用后台页面的同一套逻辑；修改特判/交互程序时会先交给判题机编译，失败返回 422 `spj_compile_error`。

```bash
# 演练：把 P1001 的时间限制改为 2 秒、加一个标签（不写入，返回 changes）
curl -X PATCH -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  "$HOJ/api/agent/v1/admin/problems/P1001?dry_run=true" \
  -d '{"time_limit": 2000, "tags": ["入门", "模拟"], "expected_version": 1763000000000}'

# 新建题目并上传测试数据 zip（data 为题目字段的 JSON）
curl -X POST -H "Authorization: Bearer $TOKEN" "$HOJ/api/agent/v1/admin/problems" \
  -F 'data={"problem_id":"P2001","title":"A+B","description":"输入两个整数，输出它们的和。","tags":["入门"]}' \
  -F file=@testcases.zip

# 新建训练并加入两道题
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  "$HOJ/api/agent/v1/admin/trainings" \
  -d '{"title":"第 3 周：循环","category":"入门","problems":[{"problem_id":"P1001"},{"problem_id":"P2001"}]}'
```

## 3. MCP 服务器

把本目录的 `hoj-mcp.mjs` 放到本机任意位置（下文以 `/path/to/hoj-mcp.mjs` 表示），在客户端中配置：

**Claude Code**

```bash
claude mcp add hoj -e HOJ_BASE_URL=https://oj.example.com -e HOJ_AGENT_TOKEN=hoj_at_... -- node /path/to/hoj-mcp.mjs
```

**Claude Desktop**（`claude_desktop_config.json`）/ **Cursor**（`.cursor/mcp.json` 或 `~/.cursor/mcp.json`）

```json
{
  "mcpServers": {
    "hoj": {
      "command": "node",
      "args": ["/path/to/hoj-mcp.mjs"],
      "env": {
        "HOJ_BASE_URL": "https://oj.example.com",
        "HOJ_AGENT_TOKEN": "hoj_at_..."
      }
    }
  }
}
```

**Codex**（`~/.codex/config.toml`）

```toml
[mcp_servers.hoj]
command = "node"
args = ["/path/to/hoj-mcp.mjs"]
env = { HOJ_BASE_URL = "https://oj.example.com", HOJ_AGENT_TOKEN = "hoj_at_..." }
```

可选环境变量 `HOJ_EXPORT_DIR`：`hoj_export_submissions` 导出文件的默认目录，也是 `testcase_zip_path` / `testcase_dir` 相对路径的基准目录。

**提供的工具**

| 工具 | 用途 |
|---|---|
| `hoj_find_students` | 按姓名/学号/用户名找学生，或列出团队成员 |
| `hoj_submission_stats` | 统计汇总，先看整体情况 |
| `hoj_list_submissions` | 按条件分页拉提交（含代码） |
| `hoj_get_submission` | 单条提交详情 |
| `hoj_export_submissions` | 全量导出为本地 NDJSON 文件，适合大批量分析 |

题目与训练管理（令牌需要 `manage` 权限）：

| 工具 | 用途 |
|---|---|
| `hoj_manage_meta` | 可选的语言、标签、训练分类、难度 |
| `hoj_list_problems` / `hoj_get_problem` | 搜索题目 / 读取题目完整信息与 version |
| `hoj_get_test_cases` | 查看测试点（可含内容） |
| `hoj_create_problem` | 新建题目；测试数据可用 `test_cases`、本地 zip（`testcase_zip_path`）或本地目录（`testcase_dir`，自动打包） |
| `hoj_update_problem` | 修改题目字段（改题号用 `new_problem_id`） |
| `hoj_replace_test_cases` | 整体替换测试数据 |
| `hoj_list_trainings` / `hoj_get_training` | 搜索训练 / 读取训练详情 |
| `hoj_create_training` / `hoj_update_training` | 新建 / 修改训练（停用：`status=false`） |
| `hoj_add_training_problems` / `hoj_remove_training_problem` / `hoj_reorder_training_problems` | 管理训练中的题目与顺序 |

MCP 服务器会提示 agent：所有写操作先 `dry_run=true` 演练、把变更给你确认后再执行。

配好后可以直接对 AI 说，例如：“分析一下张三 11 月 1 日的提交，看看他在哪类错误上反复出问题”，或“统计 3 号团队这周每个人的通过题数，把没有提交的同学列出来”。有管理权限时还可以说：“把 generated-problems/C1621 发布成新题 P2001，测试数据用里面的 testcases 目录，然后加到‘第 3 周’训练的末尾”，或“把训练 12 里所有题的时间限制统一改成 2 秒”。
