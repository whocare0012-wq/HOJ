#!/usr/bin/env node
// HOJ AI 数据接口的 MCP（Model Context Protocol）服务器，stdio 传输，零依赖，需 Node.js 18+。
// 提交数据工具需要 read 权限的令牌，题目与训练管理工具需要 manage 权限的令牌。
// 环境变量：
//   HOJ_BASE_URL      站点地址，如 https://oj.example.com（必填）
//   HOJ_AGENT_TOKEN   root 在后台「AI 数据接口」页面生成的令牌 hoj_at_...（必填）
//   HOJ_EXPORT_DIR    导出文件的默认目录，也是测试数据相对路径的基准目录（可选，默认当前目录）
// stdout 只用于 MCP 消息，日志一律写 stderr。

import { createInterface } from 'node:readline';
import { createWriteStream } from 'node:fs';
import { mkdir, readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { Readable, Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { deflateRawSync } from 'node:zlib';

const SERVER_INFO = { name: 'hoj-mcp', version: '1.1.0' };
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

async function request(url, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const response = await fetch(url, {
      method: options.method || 'GET',
      body: options.body,
      headers: {
        Authorization: `Bearer ${TOKEN}`,
        Accept: 'application/json, application/x-ndjson',
        ...(options.headers || {}),
      },
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
      return callManageTool(name, args);
  }
}

// ---------------------------------------------------------------- 题目与训练管理工具（需要 manage 权限的令牌）

const DRY_RUN = {
  type: 'boolean',
  description: '为 true 时只校验并返回 changes（逐字段 before/after）与 warnings，不写入。修改前务必先演练，把变更给用户确认后再正式执行。',
};

const EXAMPLES = {
  type: 'array',
  description: '题面展示的样例，整体替换',
  items: { type: 'object', properties: { input: { type: 'string' }, output: { type: 'string' } }, additionalProperties: false },
};

const TEST_CASES = {
  type: 'array',
  description: '手动测试数据（适合少量、小数据）：每项 {input, output, score?, group_num?}。OI 题目全部不给 score 时平均分配 100 分',
  items: {
    type: 'object',
    properties: {
      input: { type: 'string' },
      output: { type: 'string' },
      score: { type: 'integer', minimum: 0 },
      group_num: { type: 'integer', minimum: 1 },
    },
    additionalProperties: false,
  },
};

const CASE_SCORES = {
  type: 'array',
  description: '配合 testcase_zip_path / testcase_dir 使用：按输入文件名指定分数或子任务分组，如 [{"input":"1.in","score":10,"group_num":1}]',
  items: {
    type: 'object',
    properties: { input: { type: 'string' }, score: { type: 'integer', minimum: 0 }, group_num: { type: 'integer', minimum: 1 } },
    required: ['input'],
    additionalProperties: false,
  },
};

const TESTCASE_FILES = {
  testcase_zip_path: { type: 'string', description: '本地测试数据 zip 路径（内含 1.in/1.out 或 .ans 等），与 test_cases、testcase_dir 三选一' },
  testcase_dir: { type: 'string', description: '本地测试数据目录：自动把其中的 *.in / *.out / *.ans / input*.txt / output*.txt 打包上传' },
  case_scores: CASE_SCORES,
};

const PROBLEM_FIELDS = {
  title: { type: 'string' },
  description: { type: 'string', description: '题目描述，Markdown，支持 $LaTeX$' },
  input: { type: 'string', description: '输入格式' },
  output: { type: 'string', description: '输出格式' },
  examples: EXAMPLES,
  hint: { type: 'string', description: '提示 / 说明' },
  source: { type: 'string', description: '来源' },
  difficulty: { type: 'integer', description: '难度值，取值见 hoj_manage_meta 的 difficulties' },
  auth: { type: 'string', enum: ['public', 'private'], description: 'public 公开；private 隐藏（下线题目用它，接口不提供删除）' },
  time_limit: { type: 'integer', description: '时间限制，毫秒' },
  memory_limit: { type: 'integer', description: '内存限制，MB' },
  stack_limit: { type: 'integer', description: '栈限制，MB' },
  judge_mode: { type: 'string', enum: ['default', 'spj', 'interactive'] },
  judge_case_mode: { type: 'string', enum: ['default', 'ergodic_without_error', 'subtask_lowest', 'subtask_average'] },
  spj_language: { type: 'string', description: '特判/交互程序语言' },
  spj_code: { type: 'string', description: '特判/交互程序代码，提交前会先交给判题机编译' },
  code_share: { type: 'boolean' },
  remove_end_blank: { type: 'boolean' },
  open_case_result: { type: 'boolean', description: '是否向学生展示各测试点结果' },
  file_io: { type: 'boolean' },
  io_read_file_name: { type: 'string' },
  io_write_file_name: { type: 'string' },
  tags: { type: 'array', items: { type: 'string' }, description: '标签名称（整体替换；不存在的会新建）' },
  languages: { type: 'array', items: { type: 'string' }, description: '允许的语言（整体替换），取值见 hoj_manage_meta' },
  code_templates: {
    type: 'array',
    description: '代码模板（整体替换），语言必须在 languages 中',
    items: { type: 'object', properties: { language: { type: 'string' }, code: { type: 'string' } }, required: ['language', 'code'], additionalProperties: false },
  },
};

const TRAINING_FIELDS = {
  title: { type: 'string' },
  description: { type: 'string', description: 'Markdown' },
  auth: { type: 'string', enum: ['Public', 'Private'] },
  private_pwd: { type: 'string', description: '私有训练的密码；修改会清空已报名用户' },
  rank: { type: 'integer', description: '训练在列表中的排序，升序' },
  status: { type: 'boolean', description: '是否启用；false 即停用（接口不提供删除）' },
  category: { type: 'string', description: '训练分类名称，取值见 hoj_manage_meta；不存在时新建' },
};

const TRAINING_PROBLEMS = {
  type: 'array',
  description: '要加入的题目，默认依次追加到末尾，如 [{"problem_id":"P1001"},{"problem_id":"P1002","rank":5}]',
  items: {
    type: 'object',
    properties: { problem_id: { type: 'string' }, rank: { type: 'integer', minimum: 0 } },
    required: ['problem_id'],
    additionalProperties: false,
  },
};

const MANAGE_TOOLS = [
  {
    name: 'hoj_manage_meta',
    description: '新建/修改题目或训练前先调用：返回可用的语言、已有标签、训练分类、难度档位与各枚举字段的含义。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
  },
  {
    name: 'hoj_list_problems',
    description: '列出/搜索题库中的题目（按 ID 倒序，分页）。返回题号、标题、类型、可见性、难度、标签与 version。',
    inputSchema: {
      type: 'object',
      properties: {
        keyword: { type: 'string', description: '按标题、题号、来源、作者模糊搜索' },
        auth: { type: 'string', enum: ['public', 'private', 'contest'] },
        page: { type: 'integer', minimum: 1, description: '页码，默认 1' },
        limit: { type: 'integer', minimum: 1, maximum: 200, description: '每页条数，默认 50' },
      },
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_get_problem',
    description: '获取题目完整信息：题面、限制、样例、标签、语言、代码模板、特判程序、测试数据概况与 version（修改时作为 expected_version 传回）。',
    inputSchema: {
      type: 'object',
      properties: { problem_id: { type: 'string', description: '题号，如 P1001' } },
      required: ['problem_id'],
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_get_test_cases',
    description: '列出题目的测试点（文件名、分数、分组），可选返回输入输出内容（按 max_chars 截断）。',
    inputSchema: {
      type: 'object',
      properties: {
        problem_id: { type: 'string' },
        include_content: { type: 'boolean', description: '是否返回内容，默认 false' },
        max_chars: { type: 'integer', minimum: 1, maximum: 1000000, description: '每个输入/输出最多返回的字符数，默认 2000' },
      },
      required: ['problem_id'],
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_create_problem',
    description:
      '新建题目。必须提供测试数据：test_cases（少量手写）或 testcase_zip_path / testcase_dir（本地文件）。' +
      '未给的字段取后台默认值：ACM、公开、1000ms/256MB/128MB、全部语言、难度第一档。' +
      '建议先 dry_run=true 演练，把预览给用户确认后再正式创建。',
    inputSchema: {
      type: 'object',
      properties: {
        problem_id: { type: 'string', description: '题号（字母、数字、- 和 _），如 P1001' },
        type: { type: 'string', enum: ['ACM', 'OI'], description: '默认 ACM' },
        ...PROBLEM_FIELDS,
        test_cases: TEST_CASES,
        ...TESTCASE_FILES,
        dry_run: DRY_RUN,
      },
      required: ['problem_id', 'title'],
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_update_problem',
    description:
      '修改题目，只传要改的字段（tags、languages、code_templates、examples 传入即整体替换）。不能改 type；' +
      '测试数据用 hoj_replace_test_cases。先 hoj_get_problem 取得 version 作为 expected_version，' +
      '再 dry_run=true 演练并把 changes 给用户确认。',
    inputSchema: {
      type: 'object',
      properties: {
        problem_id: { type: 'string', description: '要修改的题目的题号' },
        new_problem_id: { type: 'string', description: '如需修改题号，在此给出新题号' },
        ...PROBLEM_FIELDS,
        expected_version: { type: 'integer', description: 'hoj_get_problem 返回的 version；题目期间被他人修改会返回冲突' },
        dry_run: DRY_RUN,
      },
      required: ['problem_id'],
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_replace_test_cases',
    description:
      '整体替换题目的测试数据（原测试点全部被替换）。三选一：test_cases（手写）、testcase_zip_path（zip）、testcase_dir（目录，自动打包）。' +
      '建议先 dry_run=true 对比替换前后的测试点概况。',
    inputSchema: {
      type: 'object',
      properties: {
        problem_id: { type: 'string' },
        test_cases: TEST_CASES,
        ...TESTCASE_FILES,
        expected_version: { type: 'integer' },
        dry_run: DRY_RUN,
      },
      required: ['problem_id'],
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_list_trainings',
    description: '列出/搜索训练（题单），按 rank 升序，含分类、题目数、启用状态与 version。',
    inputSchema: {
      type: 'object',
      properties: {
        keyword: { type: 'string', description: '按标题、作者模糊搜索' },
        page: { type: 'integer', minimum: 1 },
        limit: { type: 'integer', minimum: 1, maximum: 200 },
      },
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_get_training',
    description: '获取训练详情：描述、权限、分类、启用状态、按顺序排列的题目列表与 version。',
    inputSchema: {
      type: 'object',
      properties: { training_id: { type: 'integer' } },
      required: ['training_id'],
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_create_training',
    description: '新建训练（题单），可同时用 problems 加入题目。title 与 category 必填。建议先 dry_run=true 演练。',
    inputSchema: {
      type: 'object',
      properties: { ...TRAINING_FIELDS, problems: TRAINING_PROBLEMS, dry_run: DRY_RUN },
      required: ['title', 'category'],
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_update_training',
    description: '修改训练，只传要改的字段；status=false 即停用训练。先 hoj_get_training 取得 version，再 dry_run=true 演练。',
    inputSchema: {
      type: 'object',
      properties: {
        training_id: { type: 'integer' },
        ...TRAINING_FIELDS,
        expected_version: { type: 'integer' },
        dry_run: DRY_RUN,
      },
      required: ['training_id'],
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_add_training_problems',
    description: '向训练添加题目（默认追加到末尾）。隐藏题目也能加入，但会返回 warning：学生可能看不到。',
    inputSchema: {
      type: 'object',
      properties: { training_id: { type: 'integer' }, problems: TRAINING_PROBLEMS, dry_run: DRY_RUN },
      required: ['training_id', 'problems'],
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_remove_training_problem',
    description: '把题目移出训练（题目本身与提交记录都保留）。',
    inputSchema: {
      type: 'object',
      properties: { training_id: { type: 'integer' }, problem_id: { type: 'string' }, dry_run: DRY_RUN },
      required: ['training_id', 'problem_id'],
      additionalProperties: false,
    },
  },
  {
    name: 'hoj_reorder_training_problems',
    description: '按给定顺序重排训练中的全部题目：problem_ids 必须恰好包含训练中的所有题号。',
    inputSchema: {
      type: 'object',
      properties: {
        training_id: { type: 'integer' },
        problem_ids: { type: 'array', items: { type: 'string' }, description: '按新顺序排列的全部题号' },
        dry_run: DRY_RUN,
      },
      required: ['training_id', 'problem_ids'],
      additionalProperties: false,
    },
  },
];

TOOLS.push(...MANAGE_TOOLS);

const PROBLEM_FIELD_KEYS = Object.keys(PROBLEM_FIELDS);
const TRAINING_FIELD_KEYS = Object.keys(TRAINING_FIELDS);

// ---------------------------------------------------------------- 测试数据打包（零依赖 zip，deflate 压缩）

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n += 1) {
    let c = n;
    for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function buildZip(entries) {
  const locals = [];
  const centrals = [];
  let offset = 0;
  for (const { name, data } of entries) {
    const nameBytes = Buffer.from(name, 'utf8');
    const compressed = deflateRawSync(data);
    const crc = crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6); // UTF-8 文件名
    local.writeUInt16LE(8, 8); // deflate
    local.writeUInt32LE(0, 10);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(compressed.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBytes.length, 26);
    local.writeUInt16LE(0, 28);
    locals.push(local, nameBytes, compressed);

    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0x0800, 8);
    central.writeUInt16LE(8, 10);
    central.writeUInt32LE(0, 12);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(compressed.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(nameBytes.length, 28);
    central.writeUInt32LE(offset, 42);
    centrals.push(central, nameBytes);
    offset += local.length + nameBytes.length + compressed.length;
  }
  const centralSize = centrals.reduce((sum, part) => sum + part.length, 0);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(centralSize, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, ...centrals, end]);
}

const TESTCASE_FILE = /^(.+\.(in|out|ans)|.*(input|output).*\.txt)$/i;

async function zipFromDirectory(dir) {
  const root = path.resolve(EXPORT_DIR, dir);
  const names = (await readdir(root, { withFileTypes: true }))
    .filter((entry) => entry.isFile() && TESTCASE_FILE.test(entry.name))
    .map((entry) => entry.name)
    .sort((a, b) => a.localeCompare(b, 'en', { numeric: true }));
  if (!names.some((name) => /\.in$/i.test(name) || /input.*\.txt$/i.test(name))) {
    throw new HojError(`目录 ${root} 中没有测试输入文件（*.in 或 input*.txt）`);
  }
  const entries = [];
  for (const name of names) entries.push({ name, data: await readFile(path.join(root, name)) });
  return { buffer: buildZip(entries), filename: `${path.basename(root) || 'testcases'}.zip`, files: names.length };
}

async function testcaseZip(args) {
  const sources = ['test_cases', 'testcase_zip_path', 'testcase_dir'].filter((key) => args[key] !== undefined);
  if (sources.length > 1) throw new HojError(`${sources.join('、')} 只能选一个`);
  if (args.case_scores !== undefined && !args.testcase_zip_path && !args.testcase_dir) {
    throw new HojError('case_scores 只能与 testcase_zip_path 或 testcase_dir 一起使用；手写测试点请在 test_cases 中写 score');
  }
  if (args.testcase_zip_path) {
    const target = path.resolve(EXPORT_DIR, args.testcase_zip_path);
    return { buffer: await readFile(target), filename: path.basename(target) };
  }
  if (args.testcase_dir) return zipFromDirectory(args.testcase_dir);
  return null;
}

// ---------------------------------------------------------------- 管理接口调用

async function sendJson(method, pathname, body, params) {
  const { response, clear } = await request(buildUrl(pathname, params), {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  try {
    return await response.json();
  } finally {
    clear();
  }
}

async function sendForm(method, pathname, data, zip) {
  const form = new FormData();
  if (data && Object.keys(data).length > 0) form.set('data', JSON.stringify(data));
  form.set('file', new Blob([zip.buffer], { type: 'application/zip' }), zip.filename);
  const { response, clear } = await request(buildUrl(pathname), { method, body: form });
  try {
    return await response.json();
  } finally {
    clear();
  }
}

const enc = encodeURIComponent;

async function callManageTool(name, args) {
  switch (name) {
    case 'hoj_manage_meta':
      return getJson('/api/agent/v1/admin/meta');
    case 'hoj_list_problems':
      return getJson('/api/agent/v1/admin/problems', pick(args, ['keyword', 'auth', 'page', 'limit']));
    case 'hoj_get_problem':
      return getJson(`/api/agent/v1/admin/problems/${enc(args.problem_id)}`);
    case 'hoj_get_test_cases':
      return getJson(`/api/agent/v1/admin/problems/${enc(args.problem_id)}/testcases`,
        pick(args, ['include_content', 'max_chars']));
    case 'hoj_create_problem': {
      const body = pick(args, ['problem_id', 'type', ...PROBLEM_FIELD_KEYS, 'test_cases', 'case_scores', 'dry_run']);
      const zip = await testcaseZip(args);
      if (zip) {
        const result = await sendForm('POST', '/api/agent/v1/admin/problems', body, zip);
        return zip.files ? { packed_files: zip.files, ...result } : result;
      }
      return sendJson('POST', '/api/agent/v1/admin/problems', body);
    }
    case 'hoj_update_problem': {
      const body = pick(args, [...PROBLEM_FIELD_KEYS, 'expected_version', 'dry_run']);
      if (args.new_problem_id !== undefined) body.problem_id = args.new_problem_id;
      return sendJson('PATCH', `/api/agent/v1/admin/problems/${enc(args.problem_id)}`, body);
    }
    case 'hoj_replace_test_cases': {
      const pathname = `/api/agent/v1/admin/problems/${enc(args.problem_id)}/testcases`;
      const zip = await testcaseZip(args);
      if (zip) {
        const result = await sendForm('PUT', pathname, pick(args, ['case_scores', 'expected_version', 'dry_run']), zip);
        return zip.files ? { packed_files: zip.files, ...result } : result;
      }
      if (args.test_cases === undefined) throw new HojError('请提供 test_cases、testcase_zip_path 或 testcase_dir 之一');
      return sendJson('PUT', pathname, pick(args, ['test_cases', 'expected_version', 'dry_run']));
    }
    case 'hoj_list_trainings':
      return getJson('/api/agent/v1/admin/trainings', pick(args, ['keyword', 'page', 'limit']));
    case 'hoj_get_training':
      return getJson(`/api/agent/v1/admin/trainings/${enc(args.training_id)}`);
    case 'hoj_create_training':
      return sendJson('POST', '/api/agent/v1/admin/trainings', pick(args, [...TRAINING_FIELD_KEYS, 'problems', 'dry_run']));
    case 'hoj_update_training':
      return sendJson('PATCH', `/api/agent/v1/admin/trainings/${enc(args.training_id)}`,
        pick(args, [...TRAINING_FIELD_KEYS, 'expected_version', 'dry_run']));
    case 'hoj_add_training_problems':
      return sendJson('POST', `/api/agent/v1/admin/trainings/${enc(args.training_id)}/problems`,
        pick(args, ['problems', 'dry_run']));
    case 'hoj_remove_training_problem':
      return sendJson('DELETE',
        `/api/agent/v1/admin/trainings/${enc(args.training_id)}/problems/${enc(args.problem_id)}`, undefined,
        { dry_run: args.dry_run });
    case 'hoj_reorder_training_problems':
      return sendJson('PUT', `/api/agent/v1/admin/trainings/${enc(args.training_id)}/problems/order`,
        pick(args, ['problem_ids', 'dry_run']));
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
          '分析 HOJ 学生提交记录：hoj_find_students 找到学生 → hoj_submission_stats 看整体 → ' +
          'hoj_list_submissions 按需拉代码；全量数据用 hoj_export_submissions 导出到文件。时间均为站点时区（通常为北京时间）。\n' +
          '管理题目与训练（令牌需有 manage 权限）：先 hoj_manage_meta 了解可选值，用 hoj_get_problem / hoj_get_training 读取当前内容与 version；' +
          '任何写操作都先以 dry_run=true 调用，把返回的 changes 与 warnings 告诉用户并得到确认后，再带上 expected_version 去掉 dry_run 正式执行。' +
          '接口不能删除题目或训练：下线题目把 auth 设为 private，停用训练把 status 设为 false。',
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
