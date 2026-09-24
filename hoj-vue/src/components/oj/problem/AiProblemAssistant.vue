<template>
  <teleport to="body">
    <div
      v-if="statusResolved && enabled"
      class="ai-assistant-floating"
      :class="{ 'is-dragging': dragging }"
      :style="floatingStyle"
    >
      <section
        v-if="expanded"
        class="ai-assistant-panel"
        :class="panelClasses"
        aria-label="AI 助手"
      >
        <header
          class="ai-assistant-header"
          @pointerdown="!resultExpanded && startDrag($event)"
        >
          <div class="ai-assistant-title">
            <BotMessageSquare class="ai-bot-icon" />
            <strong>AI 助手</strong>
            <el-icon class="ai-assistant-spark"><MagicStick /></el-icon>
          </div>
          <button
            type="button"
            class="ai-assistant-close"
            aria-label="关闭 AI 助手"
            @pointerdown.stop
            @click.stop="closePanel"
          >
            <el-icon><Close /></el-icon>
          </button>
        </header>

        <div class="ai-assistant-body">
          <div class="ai-usage-card">
            <span class="ai-usage-icon">
              <el-icon><Calendar /></el-icon>
            </span>
            <span class="ai-usage-label">剩余使用次数</span>
            <strong>{{ remaining }} 次</strong>
          </div>

          <div v-if="statusMessage" class="ai-assistant-notice">
            {{ statusMessage }}
          </div>

          <div class="ai-action-list">
            <button
              type="button"
              class="ai-action-button"
              :disabled="!canSubmit || submitting"
              @click="submitAssistantRequest('SOLUTION')"
            >
              <span class="ai-action-icon">
                <el-icon><MagicStick /></el-icon>
              </span>
              <span>求解题方法</span>
              <el-icon class="ai-action-arrow"><ArrowRight /></el-icon>
            </button>
            <button
              type="button"
              class="ai-action-button"
              :disabled="!canSubmit || submitting"
              @click="submitAssistantRequest('CODE_REVIEW')"
            >
              <span class="ai-action-icon">
                <el-icon><Search /></el-icon>
              </span>
              <span>查询代码问题</span>
              <el-icon class="ai-action-arrow"><ArrowRight /></el-icon>
            </button>
          </div>

          <div v-if="latestRequest" class="ai-result-card">
            <div class="ai-result-heading">
              <div class="ai-result-title">
                <el-icon><ChatLineRound /></el-icon>
                <strong>{{ requestTypeLabel(latestRequest.requestType) }}</strong>
              </div>
              <div class="ai-result-actions">
                <el-button
                  v-if="latestRequest.responseContent"
                  class="ai-result-size-button"
                  text
                  size="small"
                  @pointerdown.stop
                  @click.stop="toggleResultSize"
                >
                  <el-icon>
                    <ScaleToOriginal v-if="resultExpanded" />
                    <FullScreen v-else />
                  </el-icon>
                  {{ resultExpanded ? '缩小' : '展开' }}
                </el-button>
                <el-tag
                  size="small"
                  effect="plain"
                  :type="requestStatusType(latestRequest.status)"
                >
                  {{ requestStatusLabel(latestRequest) }}
                </el-tag>
              </div>
            </div>
            <div
              v-if="latestRequest.status === 'QUEUED'"
              class="ai-result-placeholder"
            >
              请求正在排队，当前位于第 {{ latestRequest.queuePosition || 1 }} 位。
            </div>
            <div
              v-else-if="latestRequest.status === 'PROCESSING'"
              class="ai-result-placeholder"
            >
              AI 正在分析，请稍候……
            </div>
            <div
              v-else-if="latestRequest.status === 'FAILED'"
              class="ai-result-error"
            >
              {{ latestRequest.errorMessage || '请求失败，请稍后重试。' }}
            </div>
            <div
              v-else-if="latestRequest.responseContent"
              class="ai-result-content"
            >
              <Markdown
                :content="latestRequest.responseContent"
                :is-avoid-xss="true"
                :normalize-latex="true"
              />
            </div>
          </div>
        </div>
      </section>

      <button
        v-show="!resultExpanded"
        type="button"
        class="ai-assistant-orb"
        aria-label="打开 AI 助手"
        :aria-expanded="expanded"
        @pointerdown="startDrag"
        @click="togglePanel"
      >
        <BotMessageSquare />
      </button>
    </div>
  </teleport>
</template>

<script>
import {
  ArrowRight,
  Calendar,
  ChatLineRound,
  Close,
  FullScreen,
  MagicStick,
  ScaleToOriginal,
  Search,
} from '@element-plus/icons-vue';
import { BotMessageSquare } from '@lucide/vue';
import api from '@/common/api';
import myMessage from '@/common/message';
import Markdown from '@/components/oj/common/Markdown.vue';

const POSITION_STORAGE_KEY = 'hoj-ai-assistant-position';
const TERMINAL_STATUSES = ['SUCCESS', 'FAILED', 'REJECTED'];

export default {
  name: 'AiProblemAssistant',
  components: {
    ArrowRight,
    Calendar,
    ChatLineRound,
    Close,
    FullScreen,
    BotMessageSquare,
    MagicStick,
    Markdown,
    ScaleToOriginal,
    Search,
  },
  props: {
    problem: {
      type: Object,
      required: true,
    },
    code: {
      type: String,
      default: '',
    },
    language: {
      type: String,
      default: '',
    },
    sourceType: {
      type: String,
      required: true,
    },
    trainingId: {
      type: [String, Number],
      default: null,
    },
  },
  data() {
    return {
      expanded: false,
      resultExpanded: false,
      dragging: false,
      dragMoved: false,
      suppressNextClick: false,
      dragStart: { x: 0, y: 0 },
      dragOrigin: { x: 0, y: 0 },
      position: { x: 0, y: 0 },
      remaining: 0,
      dailyLimit: 0,
      enabled: false,
      configured: false,
      statusResolved: false,
      statusLoading: false,
      submitting: false,
      latestRequest: null,
      pollTimer: null,
      statusTimer: null,
    };
  },
  computed: {
    floatingStyle() {
      return {
        left: `${this.position.x}px`,
        top: `${this.position.y}px`,
      };
    },
    panelClasses() {
      return {
        'opens-right': this.position.x < 400,
        'opens-down': this.position.y < 460,
        'is-result-expanded': this.resultExpanded,
      };
    },
    statusMessage() {
      if (this.statusLoading) {
        return '正在读取 AI 助手状态……';
      }
      if (!this.enabled) {
        return 'AI 助手当前未启用';
      }
      if (!this.configured) {
        return 'AI 助手尚未配置可用的 API Key';
      }
      if (this.remaining <= 0) {
        return '今天的使用次数已经用完';
      }
      return '';
    },
    canSubmit() {
      return this.enabled && this.configured && this.remaining > 0;
    },
  },
  watch: {
    'problem.id'() {
      this.stopPolling();
      this.latestRequest = null;
      this.expanded = false;
      this.resultExpanded = false;
      this.statusResolved = false;
      this.loadStatus();
    },
    'latestRequest.responseContent'(content) {
      if (!content) {
        this.resultExpanded = false;
      }
    },
  },
  mounted() {
    this.restorePosition();
    this.loadStatus();
    this.statusTimer = window.setInterval(() => this.loadStatus(true), 30000);
    window.addEventListener('resize', this.keepInViewport);
  },
  beforeUnmount() {
    this.stopPolling();
    if (this.statusTimer) {
      window.clearInterval(this.statusTimer);
      this.statusTimer = null;
    }
    this.removeDragListeners();
    window.removeEventListener('resize', this.keepInViewport);
  },
  methods: {
    restorePosition() {
      let saved = null;
      try {
        saved = JSON.parse(window.localStorage.getItem(POSITION_STORAGE_KEY));
      } catch (error) {
        saved = null;
      }
      this.position = saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)
        ? saved
        : {
            x: Math.max(16, window.innerWidth - 112),
            y: Math.max(16, window.innerHeight - 150),
          };
      this.$nextTick(this.keepInViewport);
    },
    keepInViewport() {
      const maxX = Math.max(16, window.innerWidth - 82);
      const maxY = Math.max(16, window.innerHeight - 82);
      this.position = {
        x: Math.min(maxX, Math.max(16, this.position.x)),
        y: Math.min(maxY, Math.max(16, this.position.y)),
      };
    },
    startDrag(event) {
      if (event.button !== undefined && event.button !== 0) {
        return;
      }
      if (
        event.target instanceof Element &&
        event.target.closest('.ai-assistant-close')
      ) {
        return;
      }
      this.dragging = true;
      this.dragMoved = false;
      this.dragStart = { x: event.clientX, y: event.clientY };
      this.dragOrigin = { ...this.position };
      window.addEventListener('pointermove', this.onDragMove);
      window.addEventListener('pointerup', this.stopDrag);
      document.body.classList.add('ai-assistant-dragging');
      event.preventDefault();
    },
    onDragMove(event) {
      if (!this.dragging) {
        return;
      }
      const deltaX = event.clientX - this.dragStart.x;
      const deltaY = event.clientY - this.dragStart.y;
      if (Math.abs(deltaX) > 4 || Math.abs(deltaY) > 4) {
        this.dragMoved = true;
      }
      this.position = {
        x: this.dragOrigin.x + deltaX,
        y: this.dragOrigin.y + deltaY,
      };
      this.keepInViewport();
    },
    stopDrag() {
      if (!this.dragging) {
        return;
      }
      this.dragging = false;
      this.suppressNextClick = this.dragMoved;
      this.removeDragListeners();
      document.body.classList.remove('ai-assistant-dragging');
      window.localStorage.setItem(
        POSITION_STORAGE_KEY,
        JSON.stringify(this.position)
      );
    },
    removeDragListeners() {
      window.removeEventListener('pointermove', this.onDragMove);
      window.removeEventListener('pointerup', this.stopDrag);
    },
    togglePanel() {
      if (this.suppressNextClick) {
        this.suppressNextClick = false;
        return;
      }
      this.expanded = !this.expanded;
      if (this.expanded) {
        this.loadStatus();
      }
    },
    closePanel() {
      this.expanded = false;
      this.resultExpanded = false;
    },
    toggleResultSize() {
      this.resultExpanded = !this.resultExpanded;
    },
    loadStatus(background = false) {
      if (!this.problem?.id) {
        return;
      }
      if (!background) {
        this.statusLoading = true;
      }
      api
        .getAiAssistantStatus({
          problemId: this.problem.id,
          sourceType: this.sourceType,
          trainingId: this.trainingId || undefined,
        })
        .then((response) => {
          const data = response.data.data || {};
          this.enabled = Boolean(data.enabled);
          this.configured = Boolean(data.configured);
          this.remaining = Number(data.remaining || 0);
          this.dailyLimit = Number(data.dailyLimit || 0);
          this.latestRequest = data.latestRequest || null;
          if (!this.enabled) {
            this.expanded = false;
            this.resultExpanded = false;
            this.stopPolling();
            return;
          }
          if (
            this.latestRequest &&
            !TERMINAL_STATUSES.includes(this.latestRequest.status)
          ) {
            this.startPolling(this.latestRequest.id);
          }
        })
        .catch(() => {
          this.enabled = false;
          this.configured = false;
        })
        .finally(() => {
          this.statusResolved = true;
          if (!background) {
            this.statusLoading = false;
          }
        });
    },
    buildProblemPayload(requestType) {
      const examples = Array.isArray(this.problem.examples)
        ? JSON.stringify(this.problem.examples)
        : this.problem.examples || '';
      return {
        problemId: this.problem.id,
        trainingId: this.trainingId || null,
        sourceType: this.sourceType,
        requestType,
        language: this.language,
        code: requestType === 'CODE_REVIEW' ? this.code : '',
        problemTitle: this.problem.title || '',
        problemDescription: this.problem.description || '',
        problemInput: this.problem.input || '',
        problemOutput: this.problem.output || '',
        problemExamples: examples,
        problemHint: this.problem.hint || '',
      };
    },
    submitAssistantRequest(requestType) {
      if (requestType === 'CODE_REVIEW' && !this.code.trim()) {
        myMessage.warning('请先在代码框中填写需要检查的代码');
        return;
      }
      this.submitting = true;
      this.stopPolling();
      api
        .createAiAssistantRequest(this.buildProblemPayload(requestType))
        .then((response) => {
          const data = response.data.data || {};
          this.latestRequest = data;
          this.remaining = Number(data.remaining ?? this.remaining);
          if (!TERMINAL_STATUSES.includes(data.status)) {
            this.startPolling(data.id);
          }
        })
        .finally(() => {
          this.submitting = false;
        });
    },
    startPolling(requestId) {
      if (!requestId) {
        return;
      }
      this.stopPolling();
      this.pollTimer = window.setInterval(() => {
        api.getAiAssistantRequest(requestId).then((response) => {
          const data = response.data.data || null;
          if (!data) {
            return;
          }
          this.latestRequest = data;
          if (TERMINAL_STATUSES.includes(data.status)) {
            this.stopPolling();
          }
        });
      }, 2000);
    },
    stopPolling() {
      if (this.pollTimer) {
        window.clearInterval(this.pollTimer);
        this.pollTimer = null;
      }
    },
    requestTypeLabel(requestType) {
      return requestType === 'CODE_REVIEW' ? '代码问题分析' : '解题方法提示';
    },
    requestStatusType(status) {
      if (status === 'SUCCESS') return 'success';
      if (status === 'FAILED') return 'danger';
      if (status === 'REJECTED') return 'warning';
      return 'primary';
    },
    requestStatusLabel(request) {
      const labels = {
        QUEUED: '排队中',
        PROCESSING: '分析中',
        SUCCESS: '已完成',
        FAILED: '失败',
        REJECTED: '已拒绝',
      };
      return labels[request.status] || request.status;
    },
  },
};
</script>

<style scoped>
.ai-assistant-floating {
  position: fixed;
  pointer-events: none;
  z-index: 3000;
}
.ai-assistant-floating.is-dragging {
  transition: none;
}
.ai-assistant-orb {
  align-items: center;
  background: #1688ff;
  border: 7px solid rgba(255, 255, 255, 0.72);
  border-radius: 50%;
  box-shadow: 0 10px 28px rgba(22, 136, 255, 0.38);
  color: #fff;
  cursor: grab;
  display: flex;
  height: 74px;
  justify-content: center;
  padding: 0;
  pointer-events: auto;
  touch-action: none;
  transform: scale(0.65);
  transform-origin: center;
  transition:
    transform 180ms ease,
    box-shadow 180ms ease;
  width: 74px;
}
.ai-assistant-orb:hover,
.ai-assistant-orb:focus-visible,
.ai-assistant-floating.is-dragging .ai-assistant-orb {
  box-shadow: 0 12px 32px rgba(22, 136, 255, 0.46);
  transform: scale(1);
}
.ai-assistant-orb:active,
.ai-assistant-header:active {
  cursor: grabbing;
}
.ai-assistant-orb .lucide {
  height: 39px;
  stroke-width: 2.2;
  width: 39px;
}
.ai-assistant-panel {
  background: #fff;
  border: 1px solid #d8e6f6;
  border-radius: 20px;
  bottom: 88px;
  box-shadow: 0 12px 34px rgba(39, 112, 197, 0.25);
  max-height: min(590px, calc(100vh - 116px));
  overflow: visible;
  pointer-events: auto;
  position: absolute;
  right: 0;
  width: min(370px, calc(100vw - 32px));
}
.ai-assistant-panel::after {
  background: #fff;
  border-bottom: 1px solid #d8e6f6;
  border-right: 1px solid #d8e6f6;
  bottom: -9px;
  content: '';
  height: 18px;
  position: absolute;
  right: 27px;
  transform: rotate(45deg);
  width: 18px;
}
.ai-assistant-panel.opens-right {
  left: 0;
  right: auto;
}
.ai-assistant-panel.opens-right::after {
  left: 27px;
  right: auto;
}
.ai-assistant-panel.opens-down {
  bottom: auto;
  top: 88px;
}
.ai-assistant-panel.opens-down::after {
  border-bottom: 0;
  border-left: 1px solid #d8e6f6;
  border-right: 0;
  border-top: 1px solid #d8e6f6;
  bottom: auto;
  top: -9px;
}
.ai-assistant-panel.is-result-expanded {
  bottom: 16px;
  display: flex;
  flex-direction: column;
  left: 16px;
  max-height: none;
  position: fixed;
  right: 16px;
  top: 16px;
  width: auto;
}
.ai-assistant-panel.is-result-expanded::after {
  display: none;
}
.ai-assistant-header {
  align-items: center;
  cursor: grab;
  display: flex;
  justify-content: space-between;
  padding: 22px 22px 14px;
  touch-action: none;
  user-select: none;
}
.ai-assistant-title {
  align-items: center;
  color: #1688ff;
  display: flex;
  gap: 9px;
}
.ai-assistant-title .ai-bot-icon {
  height: 29px;
  stroke-width: 2.2;
  width: 29px;
}
.ai-assistant-title strong {
  font-size: 22px;
}
.ai-assistant-title .ai-assistant-spark {
  color: #7caefa;
  font-size: 16px;
}
.ai-assistant-close {
  align-items: center;
  background: transparent;
  border: 0;
  color: #8a94a6;
  cursor: pointer;
  display: flex;
  font-size: 18px;
  padding: 5px;
}
.ai-assistant-body {
  max-height: calc(min(590px, 100vh - 116px) - 68px);
  overflow-anchor: none;
  overflow-y: auto;
  padding: 0 18px 20px;
}
.ai-usage-card {
  align-items: center;
  border: 1px solid #dfe6ef;
  border-radius: 13px;
  display: grid;
  gap: 12px;
  grid-template-columns: 38px 1fr auto;
  padding: 15px 16px;
}
.ai-usage-icon,
.ai-action-icon {
  align-items: center;
  background: #eef6ff;
  border-radius: 9px;
  color: #1688ff;
  display: flex;
  height: 36px;
  justify-content: center;
  width: 36px;
}
.ai-usage-label {
  color: #4e5969;
  font-size: 16px;
}
.ai-usage-card strong {
  background: #f2f7ff;
  border-radius: 18px;
  color: #1688ff;
  font-size: 16px;
  padding: 7px 13px;
}
.ai-assistant-notice {
  background: #f7f9fc;
  border-radius: 10px;
  color: #8a94a6;
  font-size: 13px;
  line-height: 20px;
  margin-top: 12px;
  padding: 10px 12px;
}
.ai-action-list {
  border: 1px solid #dfe6ef;
  border-radius: 13px;
  margin-top: 14px;
  overflow: hidden;
}
.ai-action-button {
  align-items: center;
  background: #fff;
  border: 0;
  color: #303744;
  cursor: pointer;
  display: grid;
  font-size: 16px;
  gap: 12px;
  grid-template-columns: 36px 1fr auto;
  padding: 14px 16px;
  text-align: left;
  width: 100%;
}
.ai-action-button + .ai-action-button {
  border-top: 1px solid #e6ebf2;
}
.ai-action-button:hover:not(:disabled) {
  background: #f7fbff;
}
.ai-action-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.ai-action-arrow {
  color: #79879a;
}
.ai-result-card {
  border: 1px solid #dfe6ef;
  border-radius: 13px;
  margin-top: 14px;
  overflow: hidden;
}
.ai-result-heading {
  align-items: center;
  background: #f7fbff;
  border-bottom: 1px solid #e6ebf2;
  display: flex;
  justify-content: space-between;
  padding: 11px 13px;
}
.ai-result-title,
.ai-result-actions {
  align-items: center;
  display: flex;
  gap: 7px;
}
.ai-result-title {
  color: #1688ff;
}
.ai-result-size-button {
  margin-right: 2px;
}
.ai-result-content,
.ai-result-placeholder,
.ai-result-error {
  color: #434b57;
  font-size: 14px;
  line-height: 1.75;
  max-height: 240px;
  overflow-y: auto;
  padding: 14px;
  word-break: break-word;
}
.ai-result-placeholder,
.ai-result-error {
  white-space: pre-wrap;
}
.ai-result-content :deep(.markdown-body) {
  color: inherit;
  font-size: inherit;
  line-height: inherit;
  overflow-wrap: anywhere;
}
.ai-result-content :deep(.markdown-body > :first-child) {
  margin-top: 0;
}
.ai-result-content :deep(.markdown-body > :last-child) {
  margin-bottom: 0;
}
.ai-result-content :deep(.katex-display) {
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 3px;
}
.ai-assistant-panel.is-result-expanded .ai-assistant-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  max-height: none;
  min-height: 0;
}
.ai-assistant-panel.is-result-expanded .ai-result-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}
.ai-assistant-panel.is-result-expanded .ai-result-content {
  flex: 1;
  max-height: none;
  min-height: 260px;
}
.ai-result-placeholder {
  color: #7a8596;
}
.ai-result-error {
  color: #f56c6c;
}
:global(body.ai-assistant-dragging) {
  cursor: grabbing !important;
  user-select: none !important;
}

@media (prefers-reduced-motion: reduce) {
  .ai-assistant-orb {
    transition: none;
  }
}

@media (max-width: 767px) {
  .ai-assistant-orb {
    height: 62px;
    width: 62px;
  }
  .ai-assistant-orb .lucide {
    height: 33px;
    width: 33px;
  }
  .ai-assistant-panel {
    bottom: 74px;
  }
  .ai-assistant-panel.opens-down {
    top: 74px;
  }
  .ai-assistant-panel.is-result-expanded {
    bottom: 8px;
    left: 8px;
    right: 8px;
    top: 8px;
  }
  .ai-result-heading {
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
