<template>
  <div class="agent-api-page" v-loading="loading">
    <el-card class="agent-card page-heading-card">
      <div class="page-heading">
        <div>
          <h2>AI 数据接口</h2>
          <p>为 AI agent 生成访问令牌：读取学生提交记录（含代码、真实姓名与学号）做分析，或按你的要求新增、修改题目与训练。仅超级管理员可管理。</p>
        </div>
        <div class="heading-actions">
          <el-button :icon="Refresh" @click="loadOverview">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreateDialog">生成令牌</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="agent-card">
      <template #header>
        <span class="panel-title home-title">接入信息</span>
      </template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="接口地址">
          <code>{{ apiBase }}</code>
          <el-button text type="primary" :icon="DocumentCopy" @click="copy(apiBase)">复制</el-button>
        </el-descriptions-item>
        <el-descriptions-item label="OpenAPI 文档">
          <a :href="openApiUrl" target="_blank" rel="noopener">{{ openApiUrl }}</a>
          <span class="muted">（可导入 Dify、Coze、GPTs 等平台，无需令牌即可访问）</span>
        </el-descriptions-item>
        <el-descriptions-item label="MCP 服务器">
          仓库 <code>tools/hoj-mcp/hoj-mcp.mjs</code>（Node.js 18+，零依赖），配置方法见同目录 README，生成令牌后也会给出配置示例。
        </el-descriptions-item>
      </el-descriptions>
      <el-alert
        class="scope-alert"
        type="warning"
        :closable="false"
        show-icon
        title="令牌可读取全站学生的代码与实名信息，或以你的 root 身份修改题目与训练，请像密码一样保管。尚未结束的比赛中的提交不会通过接口返回；令牌创建者失去 root 权限后令牌立即失效。"
      />
      <el-alert
        class="scope-alert"
        type="info"
        :closable="false"
        show-icon
        title="管理权限的安全边界：接口不能删除题目或训练（下线题目改为隐藏、停用训练），远程题目与团队题目/训练只读；每次修改都可以先演练（dry_run）预览，实际写入会记录修改前后的内容，见下方「最近变更」。"
      />
    </el-card>

    <el-card class="agent-card">
      <template #header>
        <span class="panel-title home-title">访问令牌</span>
      </template>
      <el-table :data="tokens" border>
        <el-table-column prop="tokenName" label="名称" min-width="140" />
        <el-table-column label="令牌" min-width="150">
          <template #default="{ row }"><code>{{ row.tokenPrefix }}…</code></template>
        </el-table-column>
        <el-table-column label="状态" width="96" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限" min-width="150">
          <template #default="{ row }">
            <el-tag
              v-for="scope in scopeList(row.scopes)"
              :key="scope"
              :type="scope === 'manage' ? 'danger' : 'primary'"
              effect="plain"
              size="small"
              class="scope-tag"
            >
              {{ scopeLabel(scope) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdByUsername" label="创建者" min-width="110" />
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.gmtCreate, '-') }}</template>
        </el-table-column>
        <el-table-column label="过期时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.expiresAt, '永不过期') }}</template>
        </el-table-column>
        <el-table-column label="最近使用" min-width="190">
          <template #default="{ row }">
            {{ formatTime(row.lastUsedAt, '尚未使用') }}
            <span v-if="row.lastUsedIp" class="muted block">{{ row.lastUsedIp }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ACTIVE'"
              type="danger"
              size="small"
              :icon="Delete"
              @click="revoke(row)"
            >
              吊销
            </el-button>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有令牌，点击右上角「生成令牌」" />
        </template>
      </el-table>
    </el-card>

    <el-card class="agent-card">
      <template #header>
        <span class="panel-title home-title">最近变更（题目与训练管理）</span>
      </template>
      <el-table :data="changes" border size="small">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div v-if="row.truncated" class="muted change-truncated">内容较长，以下只显示前 20000 个字符</div>
            <div class="change-detail">
              <div>
                <div class="muted">修改前</div>
                <pre class="snippet">{{ prettyJson(row.beforeJson) }}</pre>
              </div>
              <div>
                <div class="muted">修改后</div>
                <pre class="snippet">{{ prettyJson(row.afterJson) }}</pre>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.gmtCreate, '-') }}</template>
        </el-table-column>
        <el-table-column prop="tokenName" label="令牌" min-width="120" />
        <el-table-column label="操作" min-width="120">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="对象" min-width="110">
          <template #default="{ row }">{{ row.targetType === 'training' ? '训练 ' : '' }}{{ row.targetId }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="260" show-overflow-tooltip />
        <template #empty>
          <el-empty description="暂无通过接口进行的修改" />
        </template>
      </el-table>
    </el-card>

    <el-card class="agent-card">
      <template #header>
        <span class="panel-title home-title">最近调用记录</span>
      </template>
      <el-table :data="logs" border size="small">
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.gmtCreate, '-') }}</template>
        </el-table-column>
        <el-table-column prop="tokenName" label="令牌" min-width="120" />
        <el-table-column prop="endpoint" label="接口" min-width="200" />
        <el-table-column prop="queryString" label="参数" min-width="220" show-overflow-tooltip />
        <el-table-column prop="resultCount" label="返回条数" width="96" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.statusCode < 400 ? 'success' : 'danger'" effect="plain" size="small">
              {{ row.statusCode }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" min-width="130" />
        <template #empty>
          <el-empty description="暂无调用记录" />
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="生成访问令牌" width="440px">
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input
            v-model.trim="createForm.tokenName"
            maxlength="80"
            placeholder="用途说明，如：Claude 分析 2026 秋季班"
          />
        </el-form-item>
        <el-form-item label="权限" required>
          <el-checkbox-group v-model="createForm.scopes" class="scope-options">
            <el-checkbox value="read">读取提交数据：学生提交记录（含代码）、名单与统计</el-checkbox>
            <el-checkbox value="manage">题目与训练管理：新增、修改题目与测试数据，管理训练</el-checkbox>
          </el-checkbox-group>
          <div v-if="createForm.scopes.includes('manage')" class="muted scope-hint">
            agent 将以你的账号身份修改题库与训练，建议单独生成一个只勾选此项的令牌，并设置较短的有效期。
          </div>
        </el-form-item>
        <el-form-item label="有效期">
          <el-select v-model="createForm.expiresInDays">
            <el-option label="30 天" :value="30" />
            <el-option label="90 天" :value="90" />
            <el-option label="180 天" :value="180" />
            <el-option label="365 天" :value="365" />
            <el-option label="永不过期" value="never" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createToken">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="tokenDialogVisible"
      title="令牌已生成"
      width="720px"
      :close-on-click-modal="false"
      @closed="createdToken = null"
    >
      <template v-if="createdToken">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="请立即复制保存：关闭此窗口后将无法再次查看完整令牌。"
        />
        <div class="token-row">
          <el-input :model-value="createdToken.token" readonly />
          <el-button type="primary" :icon="DocumentCopy" @click="copy(createdToken.token)">复制令牌</el-button>
        </div>
        <el-tabs v-model="snippetTab">
          <el-tab-pane v-for="snippet in snippets" :key="snippet.name" :label="snippet.label" :name="snippet.name">
            <div class="snippet-head">
              <span class="muted">{{ snippet.hint }}</span>
              <el-button text type="primary" :icon="DocumentCopy" @click="copy(snippet.code)">复制</el-button>
            </div>
            <pre class="snippet">{{ snippet.code }}</pre>
          </el-tab-pane>
        </el-tabs>
      </template>
      <template #footer>
        <el-button type="primary" @click="tokenDialogVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { Delete, DocumentCopy, Plus, Refresh } from '@element-plus/icons-vue';
import api from '@/common/api';
import myMessage from '@/common/message';
import time from '@/common/time';

const defaultCreateForm = () => ({ tokenName: '', expiresInDays: 90, scopes: ['read'] });

const SCOPE_LABELS = { read: '读取提交数据', manage: '题目与训练管理' };
const ACTION_LABELS = {
  create_problem: '新建题目',
  update_problem: '修改题目',
  replace_test_cases: '替换测试数据',
  create_training: '新建训练',
  update_training: '修改训练',
  add_training_problems: '训练添加题目',
  update_training_problem: '调整训练题目',
  remove_training_problem: '训练移除题目',
  reorder_training_problems: '训练题目排序',
};

export default {
  name: 'AgentApiAdmin',
  data() {
    return {
      Delete,
      DocumentCopy,
      Plus,
      Refresh,
      loading: false,
      creating: false,
      tokens: [],
      logs: [],
      changes: [],
      createDialogVisible: false,
      createForm: defaultCreateForm(),
      tokenDialogVisible: false,
      createdToken: null,
      snippetTab: 'curl',
    };
  },
  computed: {
    origin() {
      return window.location.origin;
    },
    apiBase() {
      return `${this.origin}/api/agent/v1`;
    },
    openApiUrl() {
      return `${this.apiBase}/openapi.json`;
    },
    snippets() {
      const token = this.createdToken ? this.createdToken.token : 'hoj_at_...';
      const base = this.origin;
      const canRead = !this.createdToken || this.scopeList(this.createdToken.scopes).includes('read');
      const curlExample = canRead
        ? `curl -H "Authorization: Bearer ${token}" "${this.apiBase}/submissions?username=zhangsan&date=2026-11-01"`
        : `curl -H "Authorization: Bearer ${token}" "${this.apiBase}/admin/problems?keyword=A%2BB"`;
      const mcpJson = JSON.stringify(
        {
          mcpServers: {
            hoj: {
              command: 'node',
              args: ['/path/to/hoj-mcp.mjs'],
              env: { HOJ_BASE_URL: base, HOJ_AGENT_TOKEN: token },
            },
          },
        },
        null,
        2
      );
      return [
        {
          name: 'curl',
          label: 'curl',
          hint: canRead
            ? '检查令牌，再拉取某学生（把 zhangsan 换成用户名）某天的提交'
            : '检查令牌，再搜索题目',
          code: `curl -H "Authorization: Bearer ${token}" "${this.apiBase}/ping"\n` + curlExample,
        },
        {
          name: 'claude-code',
          label: 'Claude Code',
          hint: '把 /path/to/hoj-mcp.mjs 换成仓库 tools/hoj-mcp/hoj-mcp.mjs 的实际路径',
          code: `claude mcp add hoj -e HOJ_BASE_URL=${base} -e HOJ_AGENT_TOKEN=${token} -- node /path/to/hoj-mcp.mjs`,
        },
        {
          name: 'json',
          label: 'Claude Desktop / Cursor',
          hint: '写入 claude_desktop_config.json 或 .cursor/mcp.json',
          code: mcpJson,
        },
        {
          name: 'codex',
          label: 'Codex',
          hint: '写入 ~/.codex/config.toml',
          code:
            '[mcp_servers.hoj]\ncommand = "node"\nargs = ["/path/to/hoj-mcp.mjs"]\n' +
            `env = { HOJ_BASE_URL = "${base}", HOJ_AGENT_TOKEN = "${token}" }`,
        },
      ];
    },
  },
  mounted() {
    this.loadOverview();
  },
  methods: {
    loadOverview() {
      this.loading = true;
      return api
        .admin_getAgentTokens({ logLimit: 50 })
        .then((response) => {
          const data = response.data.data || {};
          this.tokens = data.tokens || [];
          this.logs = data.logs || [];
          this.changes = data.changes || [];
        })
        .finally(() => {
          this.loading = false;
        });
    },
    openCreateDialog() {
      this.createForm = defaultCreateForm();
      this.createDialogVisible = true;
    },
    createToken() {
      if (!this.createForm.tokenName) {
        myMessage.warning('请填写令牌名称');
        return;
      }
      if (!this.createForm.scopes.length) {
        myMessage.warning('请至少选择一项权限');
        return;
      }
      this.creating = true;
      api
        .admin_createAgentToken(this.createForm)
        .then((response) => {
          this.createdToken = response.data.data;
          this.snippetTab = 'curl';
          this.createDialogVisible = false;
          this.tokenDialogVisible = true;
          this.loadOverview();
        })
        .finally(() => {
          this.creating = false;
        });
    },
    revoke(row) {
      this.$confirm(`吊销后使用令牌“${row.tokenName}”的所有 agent 将立即无法访问，确定吊销吗？`, '提示', {
        type: 'warning',
      }).then(() => {
        api.admin_revokeAgentToken(row.id).then(() => {
          myMessage.success('令牌已吊销');
          this.loadOverview();
        });
      });
    },
    copy(text) {
      this.$copyText(text).then(
        () => myMessage.success('已复制'),
        () => myMessage.error('复制失败，请手动选择复制')
      );
    },
    scopeList(scopes) {
      if (Array.isArray(scopes)) return scopes;
      return String(scopes || 'read')
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean);
    },
    scopeLabel(scope) {
      return SCOPE_LABELS[scope] || scope;
    },
    actionLabel(action) {
      return ACTION_LABELS[action] || action;
    },
    prettyJson(value) {
      if (!value) return '（无）';
      try {
        return JSON.stringify(JSON.parse(value), null, 2);
      } catch (e) {
        return value;
      }
    },
    statusLabel(status) {
      return { ACTIVE: '有效', EXPIRED: '已过期', REVOKED: '已吊销' }[status] || status;
    },
    statusType(status) {
      return { ACTIVE: 'success', EXPIRED: 'warning', REVOKED: 'info' }[status] || 'info';
    },
    formatTime(value, fallback) {
      return value ? time.utcToLocal(value) : fallback;
    },
  },
};
</script>

<style scoped>
.agent-api-page {
  display: grid;
  gap: 18px;
}
.agent-card {
  border: 0;
}
.page-heading-card :deep(.el-card__body) {
  padding: 20px 22px;
}
.page-heading {
  align-items: center;
  display: flex;
  gap: 16px;
  justify-content: space-between;
}
.page-heading h2 {
  color: #409eff;
  font-size: 23px;
  margin: 0 0 8px;
}
.page-heading p {
  color: #8a94a6;
  margin: 0;
}
.heading-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}
.muted {
  color: #8a94a6;
}
.block {
  display: block;
  font-size: 12px;
}
code {
  background: #f4f6f8;
  border-radius: 4px;
  padding: 1px 6px;
}
.agent-card :deep(.el-descriptions__content) {
  overflow-wrap: anywhere;
  word-break: break-word;
}
.agent-card :deep(.el-descriptions__label) {
  white-space: nowrap;
}
.scope-alert {
  margin-top: 14px;
}
.scope-tag + .scope-tag {
  margin-left: 6px;
}
.scope-options {
  display: grid;
  gap: 4px;
}
.scope-options :deep(.el-checkbox) {
  height: auto;
  margin-right: 0;
  white-space: normal;
}
.scope-hint {
  font-size: 12px;
  line-height: 1.5;
  margin-top: 4px;
}
.change-truncated {
  font-size: 12px;
  padding: 0 12px 6px;
}
.change-detail {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding: 4px 12px;
}
.token-row {
  display: flex;
  gap: 8px;
  margin: 16px 0 6px;
}
.token-row :deep(.el-input__inner) {
  font-family: Consolas, Menlo, monospace;
}
.snippet-head {
  align-items: center;
  display: flex;
  justify-content: space-between;
}
.snippet {
  background: #f7f8fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  color: #303744;
  font-size: 12.5px;
  line-height: 1.6;
  margin: 6px 0 0;
  max-height: 260px;
  overflow: auto;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
@media (max-width: 700px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .token-row {
    flex-direction: column;
  }
  .change-detail {
    grid-template-columns: 1fr;
  }
}
</style>
