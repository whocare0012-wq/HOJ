# HOJ AI 数据接口与 MCP 服务器

让各种 AI agent 直接拉取学生的提交记录（含代码）用于分析。由两部分组成：

- **REST 接口** `/api/agent/v1`：只读，任何能发 HTTP 请求的工具都能用；附 OpenAPI 文档，可直接导入 Dify、Coze、GPTs Actions、LangChain 等。
- **MCP 服务器** `hoj-mcp.mjs`：把接口包装成 5 个工具，Claude Code、Claude Desktop、Cursor、Codex 等支持 MCP 的客户端可直接调用。零依赖，只需 Node.js 18+。

## 1. 生成令牌

用 **root** 账号登录后台 →「AI 数据接口」→「生成令牌」。令牌形如 `hoj_at_...`，只在生成时显示一次，请立即保存。

- 只有 root 能生成令牌；令牌创建者不再是启用的 root 时，其令牌立即失效。
- 可设置有效期，可随时吊销；每次调用都会记录审计日志（页面下方可查看）。
- 每个令牌每分钟最多 120 次请求，批量数据请用 NDJSON 导出。
- **尚未结束的比赛中的提交不会返回。**
- 令牌可读取全站学生代码和实名信息，请像密码一样保管，不要提交到代码仓库。

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

可选环境变量 `HOJ_EXPORT_DIR`：`hoj_export_submissions` 导出文件的默认目录。

**提供的工具**

| 工具 | 用途 |
|---|---|
| `hoj_find_students` | 按姓名/学号/用户名找学生，或列出团队成员 |
| `hoj_submission_stats` | 统计汇总，先看整体情况 |
| `hoj_list_submissions` | 按条件分页拉提交（含代码） |
| `hoj_get_submission` | 单条提交详情 |
| `hoj_export_submissions` | 全量导出为本地 NDJSON 文件，适合大批量分析 |

配好后可以直接对 AI 说，例如：“分析一下张三 11 月 1 日的提交，看看他在哪类错误上反复出问题”，或“统计 3 号团队这周每个人的通过题数，把没有提交的同学列出来”。
