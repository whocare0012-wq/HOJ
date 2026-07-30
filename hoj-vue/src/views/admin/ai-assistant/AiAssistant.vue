<template>
  <div class="ai-admin-page" v-loading="loading">
    <el-card class="ai-admin-card page-heading-card">
      <div class="page-heading">
        <div>
          <h2>AI 设置</h2>
          <p>管理模型连接、API Key 轮换、用户每日次数和当前请求队列。</p>
        </div>
        <el-button :icon="Refresh" @click="loadOverview">刷新</el-button>
      </div>
    </el-card>

    <section class="ai-stat-grid">
      <article class="ai-stat-card">
        <span class="ai-stat-icon is-blue"><el-icon><List /></el-icon></span>
        <div><strong>{{ counts.queued }}</strong><span>排队请求</span></div>
      </article>
      <article class="ai-stat-card">
        <span class="ai-stat-icon is-orange"><el-icon><Loading /></el-icon></span>
        <div><strong>{{ counts.processing }}</strong><span>处理中</span></div>
      </article>
      <article class="ai-stat-card">
        <span class="ai-stat-icon is-green"><el-icon><CircleCheck /></el-icon></span>
        <div><strong>{{ counts.success }}</strong><span>已完成</span></div>
      </article>
      <article class="ai-stat-card">
        <span class="ai-stat-icon is-red"><el-icon><Warning /></el-icon></span>
        <div><strong>{{ counts.failed }}</strong><span>失败请求</span></div>
      </article>
    </section>

    <el-card class="ai-admin-card">
      <template #header>
        <div class="card-header">
          <span class="panel-title home-title">模型与限额设置</span>
          <el-button type="primary" :loading="savingConfig" @click="saveConfig">
            保存设置
          </el-button>
        </div>
      </template>
      <el-form label-position="top" class="config-form">
        <el-form-item label="启用 AI 助手">
          <el-switch v-model="config.enabled" />
        </el-form-item>
        <el-form-item label="API 基础链接">
          <el-input
            v-model.trim="config.baseUrl"
            placeholder="https://api.openai.com/v1"
          />
        </el-form-item>
        <el-form-item label="模型">
          <el-select
            v-model="config.model"
            allow-create
            filterable
            default-first-option
            placeholder="选择或输入模型名称"
          >
            <el-option label="gpt-4o-mini" value="gpt-4o-mini" />
            <el-option label="gpt-4o" value="gpt-4o" />
            <el-option label="deepseek-chat" value="deepseek-chat" />
          </el-select>
        </el-form-item>
        <el-form-item label="每位用户每天使用次数">
          <el-input-number
            v-model="config.dailyLimit"
            :min="1"
            :max="100"
          />
        </el-form-item>
        <el-form-item label="每个 API Key 的请求间隔（秒）">
          <el-input-number
            v-model="config.requestIntervalSeconds"
            :min="1"
            :max="3600"
          />
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="代码排错与解题提示词固定在服务端，前端和普通用户无法修改。"
      />
    </el-card>

    <el-card class="ai-admin-card">
      <template #header>
        <div class="card-header">
          <span class="panel-title home-title">API Key 池</span>
          <el-button type="primary" :icon="Plus" @click="openKeyDialog()">
            添加 API Key
          </el-button>
        </div>
      </template>
      <el-table :data="apiKeys" border>
        <el-table-column prop="keyName" label="名称" min-width="140" />
        <el-table-column prop="apiKeyMasked" label="API Key" min-width="220" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastUsedAt" label="最近使用" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.lastUsedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" :icon="Edit" @click="openKeyDialog(row)">
              编辑
            </el-button>
            <el-button type="danger" size="small" :icon="Delete" @click="deleteKey(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无 API Key，AI 请求不会开始处理" />
        </template>
      </el-table>
    </el-card>

    <el-card class="ai-admin-card">
      <template #header>
        <div class="card-header queue-header">
          <span class="panel-title home-title">请求队列</span>
          <el-select
            v-model="queueStatus"
            clearable
            placeholder="全部状态"
            @change="changeQueueFilter"
          >
            <el-option label="排队中" value="QUEUED" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已完成" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </div>
      </template>
      <el-table :data="requests.records" border>
        <el-table-column prop="id" label="#" width="76" />
        <el-table-column prop="username" label="用户" min-width="120" />
        <el-table-column label="题目" min-width="190">
          <template #default="{ row }">
            <strong>{{ row.problemDisplayId }}</strong>
            <span class="problem-title">{{ row.problemTitle }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ requestTypeLabel(row.requestType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="requestStatusType(row.status)" effect="plain">
              {{ requestStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="apiKeyName" label="使用 Key" min-width="120">
          <template #default="{ row }">{{ row.apiKeyName || '等待分配' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.gmtCreate) }}</template>
        </el-table-column>
        <el-table-column label="详情" width="90" align="center">
          <template #default="{ row }">
            <el-button text type="primary" @click="showRequest(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination
          background
          layout="prev, pager, next, sizes, total"
          :total="Number(requests.total || 0)"
          :page-size="requests.limit"
          :current-page="requests.currentPage"
          :page-sizes="[10, 20, 50]"
          @current-change="changePage"
          @size-change="changePageSize"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="keyDialogVisible"
      :title="keyForm.id ? '编辑 API Key' : '添加 API Key'"
      width="430px"
    >
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model.trim="keyForm.keyName" maxlength="80" />
        </el-form-item>
        <el-form-item :label="keyForm.id ? '新 API Key（留空则不修改）' : 'API Key'" required>
          <el-input
            v-model.trim="keyForm.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="keyForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="keyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingKey" @click="saveKey">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="requestDialogVisible" title="AI 请求详情" width="650px">
      <template v-if="selectedRequest">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户">{{ selectedRequest.username }}</el-descriptions-item>
          <el-descriptions-item label="题目">
            {{ selectedRequest.problemDisplayId }} {{ selectedRequest.problemTitle }}
          </el-descriptions-item>
          <el-descriptions-item label="类型">
            {{ requestTypeLabel(selectedRequest.requestType) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ requestStatusLabel(selectedRequest.status) }}
          </el-descriptions-item>
        </el-descriptions>
        <h4>请求内容预览</h4>
        <pre class="request-detail-block">{{ selectedRequest.requestPreview || '无' }}</pre>
        <h4>返回结果</h4>
        <pre class="request-detail-block">{{ selectedRequest.responsePreview || selectedRequest.errorMessage || '尚无结果' }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  CircleCheck,
  Delete,
  Edit,
  List,
  Loading,
  Plus,
  Refresh,
  Warning,
} from '@element-plus/icons-vue';
import api from '@/common/api';
import myMessage from '@/common/message';
import time from '@/common/time';

const defaultConfig = () => ({
  enabled: false,
  baseUrl: 'https://api.openai.com/v1',
  model: 'gpt-4o-mini',
  dailyLimit: 12,
  requestIntervalSeconds: 10,
});

const defaultKeyForm = () => ({
  id: null,
  keyName: '',
  apiKey: '',
  enabled: true,
});

export default {
  name: 'AiAssistantAdmin',
  components: {
    CircleCheck,
    List,
    Loading,
    Warning,
  },
  data() {
    return {
      CircleCheck,
      Delete,
      Edit,
      List,
      Loading,
      Plus,
      Refresh,
      Warning,
      loading: false,
      savingConfig: false,
      savingKey: false,
      config: defaultConfig(),
      counts: { queued: 0, processing: 0, success: 0, failed: 0 },
      apiKeys: [],
      requests: { records: [], total: 0, currentPage: 1, limit: 10 },
      queueStatus: '',
      keyDialogVisible: false,
      keyForm: defaultKeyForm(),
      requestDialogVisible: false,
      selectedRequest: null,
      refreshTimer: null,
    };
  },
  mounted() {
    this.loadOverview();
    this.refreshTimer = window.setInterval(() => this.loadOverview(false), 5000);
  },
  beforeUnmount() {
    if (this.refreshTimer) window.clearInterval(this.refreshTimer);
  },
  methods: {
    loadOverview(showLoading = true) {
      if (showLoading) this.loading = true;
      return api
        .admin_getAiAssistantOverview({
          currentPage: this.requests.currentPage,
          limit: this.requests.limit,
          status: this.queueStatus || undefined,
        })
        .then((response) => {
          const data = response.data.data || {};
          this.config = { ...defaultConfig(), ...(data.config || {}) };
          this.counts = { ...this.counts, ...(data.counts || {}) };
          this.apiKeys = data.apiKeys || [];
          this.requests = { ...this.requests, ...(data.requests || {}) };
        })
        .finally(() => {
          this.loading = false;
        });
    },
    saveConfig() {
      this.savingConfig = true;
      api
        .admin_updateAiAssistantConfig(this.config)
        .then(() => {
          myMessage.success('AI 设置保存成功');
          this.loadOverview(false);
        })
        .finally(() => {
          this.savingConfig = false;
        });
    },
    openKeyDialog(row = null) {
      this.keyForm = row
        ? {
            id: row.id,
            keyName: row.keyName,
            apiKey: '',
            enabled: Boolean(row.enabled),
          }
        : defaultKeyForm();
      this.keyDialogVisible = true;
    },
    saveKey() {
      if (!this.keyForm.keyName || (!this.keyForm.id && !this.keyForm.apiKey)) {
        myMessage.warning('请填写 API Key 名称和值');
        return;
      }
      this.savingKey = true;
      const request = this.keyForm.id
        ? api.admin_updateAiAssistantApiKey(this.keyForm.id, this.keyForm)
        : api.admin_createAiAssistantApiKey(this.keyForm);
      request
        .then(() => {
          myMessage.success('API Key 保存成功');
          this.keyDialogVisible = false;
          this.loadOverview(false);
        })
        .finally(() => {
          this.savingKey = false;
        });
    },
    deleteKey(row) {
      this.$confirm(`确定删除 API Key“${row.keyName}”吗？`, '提示', {
        type: 'warning',
      }).then(() => {
        api.admin_deleteAiAssistantApiKey(row.id).then(() => {
          myMessage.success('API Key 已删除');
          this.loadOverview(false);
        });
      });
    },
    changeQueueFilter() {
      this.requests.currentPage = 1;
      this.loadOverview();
    },
    changePage(page) {
      this.requests.currentPage = page;
      this.loadOverview();
    },
    changePageSize(size) {
      this.requests.limit = size;
      this.requests.currentPage = 1;
      this.loadOverview();
    },
    showRequest(row) {
      this.selectedRequest = row;
      this.requestDialogVisible = true;
    },
    requestTypeLabel(type) {
      return type === 'CODE_REVIEW' ? '代码排错' : '解题思路';
    },
    requestStatusLabel(status) {
      return {
        QUEUED: '排队中',
        PROCESSING: '处理中',
        SUCCESS: '已完成',
        FAILED: '失败',
        REJECTED: '已拒绝',
      }[status] || status;
    },
    requestStatusType(status) {
      if (status === 'SUCCESS') return 'success';
      if (status === 'FAILED') return 'danger';
      if (status === 'REJECTED') return 'warning';
      return 'primary';
    },
    formatDateTime(value) {
      return value ? time.utcToLocal(value) : '尚未使用';
    },
  },
};
</script>

<style scoped>
.ai-admin-page {
  display: grid;
  gap: 18px;
}
.ai-admin-card {
  border: 0;
}
.page-heading-card :deep(.el-card__body) {
  padding: 20px 22px;
}
.page-heading,
.card-header {
  align-items: center;
  display: flex;
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
.ai-stat-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.ai-stat-card {
  align-items: center;
  background: #fff;
  border-radius: 7px;
  box-shadow: 0 2px 12px rgba(31, 45, 61, 0.08);
  display: flex;
  gap: 15px;
  padding: 18px;
}
.ai-stat-icon {
  align-items: center;
  border-radius: 8px;
  color: #fff;
  display: flex;
  font-size: 26px;
  height: 52px;
  justify-content: center;
  width: 52px;
}
.ai-stat-icon.is-blue { background: #409eff; }
.ai-stat-icon.is-orange { background: #e6a23c; }
.ai-stat-icon.is-green { background: #67c23a; }
.ai-stat-icon.is-red { background: #f56c6c; }
.ai-stat-card div {
  display: flex;
  flex-direction: column;
}
.ai-stat-card strong {
  color: #303744;
  font-size: 25px;
}
.ai-stat-card span {
  color: #7b8494;
}
.config-form {
  display: grid;
  gap: 0 16px;
  grid-template-columns:
    150px
    minmax(240px, 1.3fr)
    minmax(170px, 0.75fr)
    minmax(190px, 0.8fr)
    minmax(215px, 0.9fr);
}
.config-form :deep(.el-select),
.config-form :deep(.el-input-number) {
  width: 100%;
}
.queue-header {
  gap: 18px;
}
.queue-header :deep(.el-select) {
  width: 150px;
}
.problem-title {
  color: #8a94a6;
  display: block;
  margin-top: 3px;
}
.pagination-row {
  display: flex;
  justify-content: center;
  padding-top: 18px;
}
.request-detail-block {
  background: #f7f8fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  color: #4b5563;
  line-height: 1.65;
  margin: 8px 0 18px;
  max-height: 230px;
  overflow: auto;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
@media (max-width: 1200px) {
  .ai-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .config-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 700px) {
  .ai-stat-grid,
  .config-form {
    grid-template-columns: 1fr;
  }
  .page-heading {
    align-items: flex-start;
    gap: 14px;
  }
}
</style>
