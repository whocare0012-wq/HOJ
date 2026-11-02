#!/usr/bin/env node
// HOJ AI 数据接口的 MCP（Model Context Protocol）服务器，stdio 传输，零依赖，需 Node.js 18+。
// 环境变量：
//   HOJ_BASE_URL      站点地址，如 https://oj.example.com（必填）
//   HOJ_AGENT_TOKEN   root 在后台「AI 数据接口」页面生成的令牌 hoj_at_...（必填）
//   HOJ_EXPORT_DIR    hoj_export_submissions 写文件的默认目录（可选，默认当前目录）
// stdout 只用于 MCP 消息，日志一律写 stderr。

import { createInterface } from 'node:readline';
import { createWriteStream } from 'node:fs';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { Readable, Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';

const SERVER_INFO = { name: 'hoj-mcp', version: '1.0.0' };
const SUPPORTED_PROTOCOLS = ['2025-06-18', '2025-03-26', '2024-11-05'];
const BASE_URL = (process.env.HOJ_BASE_URL || '').trim().replace(/\/+$/, '');
const TOKEN = (process.env.HOJ_AGENT_TOKEN || '').trim();
const EXPORT_DIR = process.env.HOJ_EXPORT_DIR || process.cwd();
const REQUEST_TIMEOUT_MS = 120_000;

const log = (...args) => console.error('[hoj-mcp]', ...args);

// ---------------------------------------------------------------- 工具定义

const stringOrList = (description) => ({
  anyOf: [{ type: 'string' }, { type: 'array', items: { type: 'string' } }],
  description,
});

const FILTER_PROPERTIES = {
  username: stringOrList('学生用户名，可多个'),
  uid: stringOrList('学生 uid，可多个'),
  number: stringOrList('学号，可多个（精确匹配）'),
  group_id: { type: 'integer', description: '团队（班级）ID：只看该团队成员的提交' },
  problem: stringOrList('题库题号，如 P1001，可多个'),
  contest_id: { type: 'integer', description: '只看该比赛中的提交（比赛需已结束）' },
  status: stringOrList('评测结果：AC, WA, CE, TLE, MLE, RE, PE, PA, SE, CA，可多个'),
  language: stringOrList('提交语言，如 C++、Python3、Java，可多个'),
  date: { type: 'string', description: '某一天 YYYY-MM-DD（站点时区，通常为北京时间）；不能与 from/to 同用' },
  from: { type: 'string', description: '起始时间（含），YYYY-MM-DD 或 YYYY-MM-DDTHH:mm:ss' },
  to: { type: 'string', description: '结束时间；纯日期表示包含当天全天' },
};

const TOOLS = [
  {
    name: 'hoj_list_submissions',
    description:
      '按学生、日期、题目、结果等条件拉取 HOJ 提交记录（默认含代码）。不传筛选条件即为全部提交。' +
      '结果按提交 ID 分页：has_more 为 true 时，把 next_cursor 作为 cursor 再次调用。' +
      '只需整体情况时先用 hoj_submission_stats；数据量很大时用 hoj_export_submissions 导出到文件。' +
      '尚未结束的比赛中的提交不会返回。',
    inputSchema: {
      type: 'object',
      properties: {
        ...FILTER_PROPERTIES,
        include_code: { type: 'boolean', description: '是否返回代码与编译错误，默认 true' },
        limit: { type: 'integer', minimum: 1, maximum: 500, description: '每页条数，默认 50；含代码时最大 200' },
        cursor: { type: 'string', description: '上一页返回的 next_cursor' },
        order: { type: 'string', enum: ['asc', 'desc'], description: 'asc 从旧到新（默认），desc 从新到旧' },
      },
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_get_submission',
    description: '按提交 ID 获取单条提交的完整信息与代码。',
    inputSchema: {
      type: 'object',
      properties: { submit_id: { type: 'integer', description: '提交 ID' } },
      required: ['submit_id'],
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_find_students',
    description: '按姓名、学号、用户名或昵称模糊查找学生，得到 uid/username 后再拉提交；也可列出某团队（班级）的全部成员。',
    inputSchema: {
      type: 'object',
      properties: {
        keyword: { type: 'string', description: '关键字，不传则列出全部用户' },
        group_id: { type: 'integer', description: '只列出该团队成员' },
        limit: { type: 'integer', minimum: 1, maximum: 500, description: '每页条数，默认 100' },
        cursor: { type: 'string', description: '上一页返回的 next_cursor' },
      },
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_submission_stats',
    description:
      '提交统计汇总，适合先了解整体情况：group_by=user（每个学生的提交数、AC 数、通过题数）、' +
      'problem（每道题的提交与通过人数）、day（每天的提交量与活跃人数）、status（结果分布）、language（语言分布）。' +
      '支持与 hoj_list_submissions 相同的筛选条件。',
    inputSchema: {
      type: 'object',
      properties: {
        group_by: { type: 'string', enum: ['user', 'problem', 'day', 'status', 'language'], description: '默认 user' },
        limit: { type: 'integer', minimum: 1, maximum: 5000, description: '最多返回的分组行数，默认 200' },
        ...FILTER_PROPERTIES,
      },
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_export_submissions',
    description:
      '把匹配的提交记录全部导出为本地 NDJSON 文件（每行一条 JSON，含代码），返回文件路径与条数。' +
      '适合“拉取全部学生/全部日期”这类大批量分析：导出后用脚本或分批读取文件处理，避免把海量代码塞进对话。',
    inputSchema: {
      type: 'object',
      properties: {
        output_path: { type: 'string', description: '输出文件路径；相对路径基于 HOJ_EXPORT_DIR（默认当前目录）。默认自动命名' },
        ...FILTER_PROPERTIES,
        include_code: { type: 'boolean', description: '是否包含代码，默认 true' },
        max_rows: { type: 'integer', minimum: 1, description: '最多导出条数，默认不限' },
        order: { type: 'string', enum: ['asc', 'desc'], description: 'asc 从旧到新（默认）' },
      },
      additionalProperties: false,
    },
  },
];

// ---------------------------------------------------------------- HOJ 接口调用

class HojError extends Error {}

function buildUrl(pathname, params = {}) {
  if (!BASE_URL || !TOKEN) {
    throw new HojError('未配置 HOJ_BASE_URL 或 HOJ_AGENT_TOKEN 环境变量，请在 MCP 客户端配置中设置后重启。');
  }
  const url = new URL(BASE_URL + pathname);
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    url.searchParams.set(key, Array.isArray(value) ? value.join(',') : String(value));
  }
  return url;
}

async function request(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${TOKEN}`, Accept: 'application/json, application/x-ndjson' },
      signal: controller.signal,
    });
    if (!response.ok) {
      let detail = `HTTP ${response.status}`;
      try {
        const body = await response.json();
        if (body?.error) detail += ` ${body.error.code}: ${body.error.message}`;
      } catch {
        // 非 JSON 错误体，保留状态码即可
      }
      if (response.status === 429) {
        detail += `（${response.headers.get('Retry-After') || 60} 秒后可重试）`;
      }
      throw new HojError(detail);
    }
    return { response, clear: () => clearTimeout(timer) };
  } catch (error) {
    clearTimeout(timer);
    if (error instanceof HojError) throw error;
    if (error?.name === 'AbortError') throw new HojError(`请求超时（${REQUEST_TIMEOUT_MS / 1000} 秒）：${url.pathname}`);
    throw new HojError(`无法连接 HOJ（${BASE_URL}）：${error?.message || error}`);
  }
}

async function getJson(pathname, params) {
  const { response, clear } = await request(buildUrl(pathname, params));
  try {
    return await response.json();
  } finally {
    clear();
  }
}

function pick(args, keys) {
  const result = {};
  for (const key of keys) if (args[key] !== undefined) result[key] = args[key];
  return result;
}

const FILTER_KEYS = Object.keys(FILTER_PROPERTIES);

async function exportSubmissions(args) {
  const name = args.output_path || `hoj-submissions-${new Date().toISOString().replace(/[:.]/g, '-')}.ndjson`;
  const target = path.resolve(EXPORT_DIR, name);
  await mkdir(path.dirname(target), { recursive: true });

  const params = { ...pick(args, [...FILTER_KEYS, 'include_code', 'order']), format: 'ndjson' };
  if (args.max_rows !== undefined) params.limit = args.max_rows;
  const { response, clear } = await request(buildUrl('/api/agent/v1/submissions', params));

  let rows = 0;
  let bytes = 0;
  const counter = new Transform({
    transform(chunk, _encoding, callback) {
      bytes += chunk.length;
      for (const byte of chunk) if (byte === 10) rows += 1;
      callback(null, chunk);
    },
  });
  try {
    await pipeline(Readable.fromWeb(response.body), counter, createWriteStream(target));
  } finally {
    clear();
  }
  return {
    output_path: target,
    rows,
    bytes,
    format: 'NDJSON：每行一个提交对象，字段与 hoj_list_submissions 返回的 data 元素相同',
  };
}

async function callTool(name, args = {}) {
  switch (name) {
    case 'hoj_list_submissions':
      return getJson('/api/agent/v1/submissions', {
        limit: 50,
        ...pick(args, [...FILTER_KEYS, 'include_code', 'limit', 'cursor', 'order']),
      });
    case 'hoj_get_submission':
      if (!Number.isInteger(args.submit_id)) throw new HojError('submit_id 必须是整数');
      return getJson(`/api/agent/v1/submissions/${args.submit_id}`);
    case 'hoj_find_students':
      return getJson('/api/agent/v1/students', pick(args, ['keyword', 'group_id', 'limit', 'cursor']));
    case 'hoj_submission_stats':
      return getJson('/api/agent/v1/stats', pick(args, [...FILTER_KEYS, 'group_by', 'limit']));
    case 'hoj_export_submissions':
      return exportSubmissions(args);
    default:
      return undefined;
  }
}

// ---------------------------------------------------------------- MCP / JSON-RPC

function send(message) {
  process.stdout.write(JSON.stringify(message) + '\n');
}

function reply(id, result) {
  send({ jsonrpc: '2.0', id, result });
}

function fail(id, code, message) {
  send({ jsonrpc: '2.0', id, error: { code, message } });
}

async function handle(message) {
  const { id, method, params } = message;
  const isRequest = id !== undefined && id !== null;

  switch (method) {
    case 'initialize': {
      const requested = params?.protocolVersion;
      reply(id, {
        protocolVersion: SUPPORTED_PROTOCOLS.includes(requested) ? requested : SUPPORTED_PROTOCOLS[0],
        capabilities: { tools: { listChanged: false } },
        serverInfo: SERVER_INFO,
        instructions:
          '用于分析 HOJ 学生提交记录。建议流程：hoj_find_students 找到学生 → hoj_submission_stats 看整体 → ' +
          'hoj_list_submissions 按需拉代码；全量数据用 hoj_export_submissions 导出到文件。时间均为站点时区（通常为北京时间）。',
      });
      return;
    }
    case 'notifications/initialized':
    case 'notifications/cancelled':
      return;
    case 'ping':
      if (isRequest) reply(id, {});
      return;
    case 'tools/list':
      reply(id, { tools: TOOLS });
      return;
    case 'tools/call': {
      const name = params?.name;
      const tool = TOOLS.find((item) => item.name === name);
      if (!tool) {
        fail(id, -32602, `未知工具: ${name}`);
        return;
      }
      const args = params?.arguments || {};
      // 与 REST 接口一致：参数名写错时报错，而不是悄悄忽略后返回未筛选的数据
      const allowed = Object.keys(tool.inputSchema.properties);
      const unknown = Object.keys(args).filter((key) => !allowed.includes(key));
      if (unknown.length > 0) {
        reply(id, {
          content: [{ type: 'text', text: `未知参数: ${unknown.join(', ')}；可用参数: ${allowed.join(', ')}` }],
          isError: true,
        });
        return;
      }
      try {
        const result = await callTool(name, args);
        reply(id, { content: [{ type: 'text', text: JSON.stringify(result) }] });
      } catch (error) {
        const text = error instanceof HojError ? error.message : `内部错误: ${error?.stack || error}`;
        if (!(error instanceof HojError)) log(text);
        reply(id, { content: [{ type: 'text', text }], isError: true });
      }
      return;
    }
    default:
      if (isRequest) fail(id, -32601, `不支持的方法: ${method}`);
  }
}

const pending = new Set();
const rl = createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on('line', (line) => {
  if (!line.trim()) return;
  let message;
  try {
    message = JSON.parse(line);
  } catch {
    fail(null, -32700, '无法解析的 JSON');
    return;
  }
  const messages = Array.isArray(message) ? message : [message];
  for (const item of messages) {
    const task = handle(item)
      .catch((error) => {
        log('unhandled', error);
        if (item?.id !== undefined) fail(item.id, -32603, String(error?.message || error));
      })
      .finally(() => pending.delete(task));
    pending.add(task);
  }
});
// 客户端关闭 stdin 后，等待进行中的请求写完响应再退出
rl.on('close', () => {
  Promise.allSettled([...pending]).then(() => process.exit(0));
});

if (!BASE_URL || !TOKEN) {
  log('警告：未设置 HOJ_BASE_URL 或 HOJ_AGENT_TOKEN，工具调用将返回错误。');
}
