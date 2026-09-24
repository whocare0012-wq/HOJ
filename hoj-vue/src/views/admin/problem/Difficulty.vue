<template>
  <div class="difficulty-admin-page">
    <el-card class="difficulty-card" shadow="never">
      <template #header>
        <div class="difficulty-header">
          <div>
            <h2>{{ $t('m.Problem_Difficulty_Admin') }}</h2>
            <p>{{ $t('m.Difficulty_Config_Tip') }}</p>
          </div>
          <div class="difficulty-actions">
            <el-button
              type="success"
              :disabled="loading || saving || difficulties.length >= 50"
              @click="addDifficulty"
            >
              + {{ $t('m.Add_Difficulty') }}
            </el-button>
            <el-button :disabled="loading || saving" @click="loadDifficulties">
              {{ $t('m.Reset') }}
            </el-button>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="loading"
              @click="saveDifficulties"
            >
              {{ $t('m.Save') }}
            </el-button>
          </div>
        </div>
      </template>

      <el-alert title="基础积分随难度统一管理" type="info" :closable="false" show-icon class="points-help">
        <p>每题积分 = 当前难度基础积分 × 历史最佳完成比例。修改基础积分或题目难度后，所有已得分学生按新规则计分，积分可能增加或减少。</p>
        <p>ACM 通过得满分，OI 按最佳完成比例计分；重复提交不累加。难度编号、名称、颜色和排序不参与计算。</p>
      </el-alert>
      <el-table
        v-loading="loading"
        :data="difficulties"
        row-key="clientKey"
        border
        class="difficulty-table"
      >
        <el-table-column label="基础积分" min-width="175" align="center">
          <template #default="{ row }"><el-input-number v-model="row.basePoints" :min="0" :max="100000" :precision="2" :step="10" controls-position="right" :aria-label="row.displayText + '基础积分'" :disabled="saving" style="width:150px" /></template>
        </el-table-column>
        <el-table-column
          prop="difficultyValue"
          :label="$t('m.Difficulty_Value')"
          width="95"
          align="center"
        >
          <template #default="{ row }">
            <span class="difficulty-value">
              {{ row.difficultyValue == null ? '—' : row.difficultyValue }}
            </span>
          </template>
        </el-table-column>

        <el-table-column
          :label="$t('m.Difficulty_Display_Text')"
          min-width="180"
        >
          <template #default="{ row }">
            <el-input
              v-model="row.displayText"
              maxlength="20"
              show-word-limit
              :aria-label="
                $t('m.Difficulty_Display_Text') + ' ' + row.clientKey
              "
            />
          </template>
        </el-table-column>

        <el-table-column
          :label="$t('m.Tag_Color')"
          min-width="215"
        >
          <template #default="{ row }">
            <div class="color-editor">
              <el-color-picker
                v-model="row.borderColor"
                :predefine="predefinedColors"
                :aria-label="
                  $t('m.Tag_Color') + ' ' + row.clientKey
                "
              />
              <el-input
                v-model="row.borderColor"
                maxlength="7"
                class="color-input"
                :aria-label="
                  $t('m.Tag_Color') + ' HEX ' + row.clientKey
                "
              />
            </div>
          </template>
        </el-table-column>

        <el-table-column
          prop="problemCount"
          :label="$t('m.Difficulty_Problem_Count')"
          width="130"
          align="center"
        />

        <el-table-column
          :label="$t('m.Difficulty_Preview')"
          min-width="120"
          align="center"
        >
          <template #default="{ row }">
            <span
              class="difficulty-preview"
              :style="previewStyle(row.borderColor)"
            >
              {{ row.displayText || '—' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column
          :label="$t('m.Difficulty_Operation')"
          width="180"
          align="center"
          fixed="right"
        >
          <template #default="{ row, $index }">
            <div class="row-actions">
              <el-button
                size="small"
                :disabled="$index === 0"
                :title="$t('m.Move_Up')"
                @click="moveDifficulty($index, -1)"
              >
                ↑
              </el-button>
              <el-button
                size="small"
                :disabled="$index === difficulties.length - 1"
                :title="$t('m.Move_Down')"
                @click="moveDifficulty($index, 1)"
              >
                ↓
              </el-button>
              <el-button
                size="small"
                type="danger"
                :disabled="Number(row.problemCount || 0) > 0"
                :title="
                  Number(row.problemCount || 0) > 0
                    ? $t('m.Difficulty_In_Use')
                    : $t('m.Delete')
                "
                @click="removeDifficulty($index)"
              >
                {{ $t('m.Delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="mobile-save">
        <el-button
          type="success"
          :disabled="loading || saving || difficulties.length >= 50"
          @click="addDifficulty"
        >
          + {{ $t('m.Add_Difficulty') }}
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="loading"
          @click="saveDifficulties"
        >
          {{ $t('m.Save') }}
        </el-button>
      </div>
    </el-card>
    <el-card shadow="never" style="margin-top:20px">
      <template #header><strong>最近积分规则变更</strong><el-button link type="primary" @click="loadHistory" style="float:right">刷新记录</el-button></template>
      <el-table :data="history" empty-text="暂无变更记录" max-height="320">
        <el-table-column prop="gmt_create" label="时间（北京时间）" min-width="180" />
        <el-table-column prop="operator_name" label="操作人" min-width="140" />
        <el-table-column prop="target_name" label="调整对象" min-width="140" />
        <el-table-column label="变更" min-width="220"><template #default="{ row }">{{ row.target_type === 'problem' ? '难度编号' : '基础积分' }}：{{ row.old_value }} → {{ row.new_value }}</template></el-table-column>
        <el-table-column prop="affected_users" label="涉及学生" width="110" />
      </el-table>
    </el-card>
    <el-dialog v-model="previewVisible" title="确认积分规则调整" width="min(620px, 92vw)" :close-on-click-modal="!saving" :show-close="!saving">
      <el-alert type="warning" :closable="false" show-icon :title="`本次涉及 ${preview.problemCount || 0} 道题目、${preview.userCount || 0} 位已得分学生（含团队，已去重）。`" />
      <el-table :data="preview.changes || []" style="margin-top:16px">
        <el-table-column prop="name" label="难度" /><el-table-column prop="oldPoints" label="原基础积分" /><el-table-column prop="newPoints" label="新基础积分" />
      </el-table>
      <p>保存后，个人主页和积分榜将使用新分值。历史提交的原始成绩保持不变。</p>
      <template #footer><el-button :disabled="saving" @click="previewVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="commitDifficulties">确认保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script>
import api from '@/common/api';
import myMessage from '@/common/message';
import { applyProblemLevelConfig } from '@/common/constants';

const HEX_COLOR = /^#[0-9A-Fa-f]{6}$/;

export default {
  name: 'ProblemDifficultyAdmin',
  data() {
    return {
      loading: false,
      saving: false,
      previewVisible: false,
      preview: {},
      history: [],
      pendingPayload: [],
      difficulties: [],
      nextClientKey: 0,
      predefinedColors: [
        '#19BE6B',
        '#2D8CF0',
        '#ED3F14',
        '#409EFF',
        '#67C23A',
        '#E6A23C',
        '#F56C6C',
        '#909399',
      ],
    };
  },
  mounted() {
    this.loadDifficulties();
    this.loadHistory();
  },
  methods: {
    loadHistory() { return api.admin_getProblemPointsHistory().then(res => { this.history = res.data.data || []; }); },
    loadDifficulties() {
      this.loading = true;
      return api
        .admin_getProblemDifficulties()
        .then((response) => {
          this.difficulties = (response.data.data || []).map((difficulty) =>
            this.normalizeDifficulty(difficulty)
          );
        })
        .finally(() => {
          this.loading = false;
        });
    },
    validateDifficulties() {
      if (this.difficulties.length === 0) {
        myMessage.error('至少需要保留一个难度级别');
        return false;
      }
      if (this.difficulties.length > 50) {
        myMessage.error('最多可设置 50 个难度级别');
        return false;
      }
      const names = new Set();
      for (const difficulty of this.difficulties) {
        difficulty.displayText = String(difficulty.displayText || '').trim();
        difficulty.borderColor = String(difficulty.borderColor || '').toUpperCase();
        if (!difficulty.displayText) {
          myMessage.error('难度显示文字不能为空');
          return false;
        }
        if (difficulty.basePoints == null || !Number.isFinite(Number(difficulty.basePoints)) || difficulty.basePoints < 0 || difficulty.basePoints > 100000) {
          myMessage.error('基础积分必须为 0 至 100000，最多保留两位小数'); return false;
        }
        const normalizedName = difficulty.displayText.toLocaleLowerCase();
        if (names.has(normalizedName)) {
          myMessage.error('难度显示文字不能重复');
          return false;
        }
        names.add(normalizedName);
        if (!HEX_COLOR.test(difficulty.borderColor)) {
          myMessage.error('标签颜色必须是 #RRGGBB 格式');
          return false;
        }
      }
      return true;
    },
    async saveDifficulties() {
      if (!this.validateDifficulties()) return;
      this.pendingPayload = this.difficulties.map(d => ({ difficultyValue: d.difficultyValue, displayText: d.displayText, borderColor: d.borderColor, basePoints: d.basePoints }));
      this.saving = true;
      try {
        const response = await api.admin_previewProblemPoints(this.pendingPayload);
        this.preview = response.data.data;
        if (this.preview.changes.length) this.previewVisible = true;
        else await this.commitDifficulties();
      } finally { this.saving = false; }
    },
    async commitDifficulties() {
      this.saving = true;
      try {
        const response = await api.admin_updateProblemDifficulties(this.pendingPayload);
        this.difficulties = (response.data.data || []).map(d => this.normalizeDifficulty(d));
        applyProblemLevelConfig(this.difficulties);
        this.previewVisible = false;
        myMessage.success('难度与积分规则已保存');
        await this.loadHistory();
      } finally { this.saving = false; }
    },
    previewStyle(color) {
      const borderColor = HEX_COLOR.test(String(color || ''))
        ? color
        : '#DCDFE6';
      return {
        color: '#FFFFFF',
        borderColor,
        backgroundColor: borderColor,
      };
    },
    normalizeDifficulty(difficulty) {
      return {
        difficultyValue:
          difficulty.difficultyValue == null
            ? null
            : Number(difficulty.difficultyValue),
        displayText: difficulty.displayText || '',
        borderColor: String(difficulty.borderColor || '').toUpperCase(),
        problemCount: Number(difficulty.problemCount || 0),
        basePoints: Number(difficulty.basePoints == null ? 10 : difficulty.basePoints),
        clientKey:
          difficulty.difficultyValue == null
            ? `new-${++this.nextClientKey}`
            : `difficulty-${difficulty.difficultyValue}`,
      };
    },
    addDifficulty() {
      if (this.difficulties.length >= 50) {
        return;
      }
      const color =
        this.predefinedColors[
          this.difficulties.length % this.predefinedColors.length
        ];
      this.difficulties.push(
        this.normalizeDifficulty({
          difficultyValue: null,
          displayText: '',
          borderColor: color,
          problemCount: 0,
        })
      );
    },
    removeDifficulty(index) {
      const difficulty = this.difficulties[index];
      if (Number(difficulty.problemCount || 0) > 0) {
        myMessage.warning(this.$t('m.Difficulty_In_Use'));
        return;
      }
      if (this.difficulties.length === 1) {
        myMessage.error('至少需要保留一个难度级别');
        return;
      }
      this.difficulties.splice(index, 1);
    },
    moveDifficulty(index, offset) {
      const targetIndex = index + offset;
      if (targetIndex < 0 || targetIndex >= this.difficulties.length) {
        return;
      }
      const [difficulty] = this.difficulties.splice(index, 1);
      this.difficulties.splice(targetIndex, 0, difficulty);
    },
  },
};
</script>

<style scoped>
.points-help { margin-bottom: 20px; }
.points-help p { margin: 5px 0; line-height: 1.7; }
.difficulty-admin-page {
  padding: 0;
}

.difficulty-card {
  border: none;
}

.difficulty-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.difficulty-header h2 {
  margin: 0;
  color: #409eff;
  font-size: 22px;
  font-weight: 600;
}

.difficulty-header p {
  margin: 8px 0 0;
  color: #909399;
  font-size: 13px;
}

.difficulty-actions {
  display: flex;
  flex: 0 0 auto;
}

.difficulty-table {
  width: 100%;
}

.difficulty-value {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 30px;
  border-radius: 4px;
  background: #f2f6fc;
  color: #606266;
  font-weight: 600;
}

.color-editor {
  display: flex;
  align-items: center;
  gap: 12px;
}

.color-input {
  width: 150px;
}

.difficulty-preview {
  display: inline-flex;
  min-width: 76px;
  min-height: 32px;
  align-items: center;
  justify-content: center;
  padding: 5px 14px;
  border: 1px solid;
  border-radius: 4px;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.mobile-save {
  display: none;
}

@media screen and (max-width: 768px) {
  .difficulty-header {
    align-items: flex-start;
  }

  .difficulty-header h2 {
    font-size: 20px;
  }

  .difficulty-actions {
    display: none;
  }

  .color-editor {
    align-items: stretch;
    flex-direction: column;
  }

  .color-input {
    width: 100%;
  }

  .mobile-save {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    padding-top: 18px;
  }
}
</style>
