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

      <el-table
        v-loading="loading"
        :data="difficulties"
        row-key="clientKey"
        border
        class="difficulty-table"
      >
        <el-table-column
          prop="difficultyValue"
          :label="$t('m.Difficulty_Value')"
          width="130"
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
          min-width="260"
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
          min-width="280"
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
          min-width="220"
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
          width="210"
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
  },
  methods: {
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
    saveDifficulties() {
      if (!this.validateDifficulties()) {
        return;
      }
      this.saving = true;
      api
        .admin_updateProblemDifficulties(
          this.difficulties.map((difficulty) => ({
            difficultyValue: difficulty.difficultyValue,
            displayText: difficulty.displayText,
            borderColor: difficulty.borderColor,
          }))
        )
        .then((response) => {
          this.difficulties = (response.data.data || []).map((difficulty) =>
            this.normalizeDifficulty(difficulty)
          );
          applyProblemLevelConfig(this.difficulties);
          myMessage.success(this.$t('m.Update_Successfully'));
        })
        .finally(() => {
          this.saving = false;
        });
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
