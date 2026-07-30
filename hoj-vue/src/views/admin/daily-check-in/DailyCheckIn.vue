<template>
  <div class="daily-check-admin-page" v-loading="loading.overview">
    <section class="statistics-grid" aria-label="签到配置统计">
      <article
        v-for="item in statisticCards"
        :key="item.key"
        class="statistic-card"
      >
        <div class="statistic-icon" :style="{ backgroundColor: item.color }">
          <i :class="item.icon" aria-hidden="true"></i>
        </div>
        <div class="statistic-content">
          <strong :style="{ color: item.color }">{{ item.value }}</strong>
          <span>{{ item.label }}</span>
        </div>
      </article>
    </section>

    <el-card class="management-card fortune-management-card">
      <template #header>
        <div class="card-header">
          <span class="panel-title home-title">运势管理</span>
          <div class="header-actions">
            <el-button
              type="primary"
              :icon="Plus"
              @click="openFortuneDialog()"
            >
              新增运势
            </el-button>
            <el-button :icon="Refresh" @click="refreshPage">刷新</el-button>
          </div>
        </div>
      </template>

      <div class="table-scroll">
        <table class="management-table fortune-table">
          <thead>
            <tr>
              <th>#</th>
              <th>运势名称</th>
              <th>展示颜色</th>
              <th>说明</th>
              <th>排序</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(fortune, index) in overview.fortunes"
              :key="fortune.id"
              :class="{ 'is-current': currentFortune?.code === fortune.code }"
            >
              <td>{{ index + 1 }}</td>
              <td>
                <span
                  class="fortune-name-badge"
                  :style="{ backgroundColor: fortune.color }"
                >
                  {{ fortune.name }}
                </span>
              </td>
              <td>
                <span
                  class="color-swatch"
                  :style="{ backgroundColor: fortune.color }"
                ></span>
                <span class="color-code">{{ fortune.color }}</span>
              </td>
              <td class="fortune-description">{{ fortune.description || '—' }}</td>
              <td>{{ fortune.sortOrder }}</td>
              <td>
                <el-tag
                  :type="fortune.enabled ? 'success' : 'info'"
                  effect="plain"
                  size="small"
                >
                  {{ fortune.enabled ? '启用' : '停用' }}
                </el-tag>
              </td>
              <td>
                <div class="row-actions">
                  <el-button
                    type="primary"
                    size="small"
                    :icon="Edit"
                    @click="openFortuneDialog(fortune)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    type="primary"
                    size="small"
                    :icon="Collection"
                    @click="selectFortune(fortune, true)"
                  >
                    词条管理
                  </el-button>
                  <el-button
                    type="danger"
                    size="small"
                    :icon="Delete"
                    @click="deleteFortune(fortune)"
                  >
                    删除
                  </el-button>
                </div>
              </td>
            </tr>
            <tr v-if="!overview.fortunes.length">
              <td colspan="7" class="empty-cell">暂无运势配置</td>
            </tr>
          </tbody>
        </table>
      </div>
    </el-card>

    <el-card ref="adviceSection" class="management-card advice-management-card">
      <template #header>
        <div class="card-header advice-card-header">
          <div class="advice-heading">
            <span class="panel-title home-title">词条管理</span>
            <template v-if="currentFortune">
              <span class="current-label">当前运势：</span>
              <span
                class="fortune-name-badge current-fortune-badge"
                :style="{ backgroundColor: currentFortune.color }"
              >
                {{ currentFortune.name }}
              </span>
            </template>
          </div>
          <div v-if="currentFortune" class="header-actions">
            <el-button
              type="primary"
              :icon="Plus"
              @click="openAdviceDialog('recommended')"
            >
              新增宜词条
            </el-button>
            <el-button
              type="primary"
              :icon="Plus"
              @click="openAdviceDialog('avoid')"
            >
              新增忌词条
            </el-button>
          </div>
        </div>
      </template>

      <div v-if="currentFortune" v-loading="loading.advice" class="advice-grid">
        <section class="advice-panel">
          <h3 class="advice-panel-title recommended-title">宜词条</h3>
          <div class="table-scroll">
            <table class="management-table advice-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>词条内容</th>
                  <th>使用次数</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(item, index) in advicePool.recommended"
                  :key="item.id || `recommended-${index}`"
                >
                  <td>{{ index + 1 }}</td>
                  <td class="advice-title-cell" :title="item.description">
                    {{ item.title }}
                  </td>
                  <td>{{ item.usageCount || 0 }}</td>
                  <td>
                    <el-tag
                      :type="item.enabled ? 'success' : 'info'"
                      effect="plain"
                      size="small"
                    >
                      {{ item.enabled ? '启用' : '停用' }}
                    </el-tag>
                  </td>
                  <td>
                    <div class="row-actions compact-actions">
                      <el-button
                        type="primary"
                        size="small"
                        @click="openAdviceDialog('recommended', item)"
                      >
                        编辑
                      </el-button>
                      <el-button
                        type="danger"
                        size="small"
                        @click="deleteAdvice(item)"
                      >
                        删除
                      </el-button>
                    </div>
                  </td>
                </tr>
                <tr v-if="!advicePool.recommended.length">
                  <td colspan="5" class="empty-cell">暂无宜词条</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="advice-panel">
          <h3 class="advice-panel-title avoid-title">忌词条</h3>
          <div class="table-scroll">
            <table class="management-table advice-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>词条内容</th>
                  <th>使用次数</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(item, index) in advicePool.avoid"
                  :key="item.id || `avoid-${index}`"
                >
                  <td>{{ index + 1 }}</td>
                  <td class="advice-title-cell" :title="item.description">
                    {{ item.title }}
                  </td>
                  <td>{{ item.usageCount || 0 }}</td>
                  <td>
                    <el-tag
                      :type="item.enabled ? 'success' : 'info'"
                      effect="plain"
                      size="small"
                    >
                      {{ item.enabled ? '启用' : '停用' }}
                    </el-tag>
                  </td>
                  <td>
                    <div class="row-actions compact-actions">
                      <el-button
                        type="primary"
                        size="small"
                        @click="openAdviceDialog('avoid', item)"
                      >
                        编辑
                      </el-button>
                      <el-button
                        type="danger"
                        size="small"
                        @click="deleteAdvice(item)"
                      >
                        删除
                      </el-button>
                    </div>
                  </td>
                </tr>
                <tr v-if="!advicePool.avoid.length">
                  <td colspan="5" class="empty-cell">暂无忌词条</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>

      <div v-else class="advice-empty-state">
        请先新增或选择一个运势，再管理对应的宜、忌词条。
      </div>
    </el-card>

    <el-card class="management-card explanation-card">
      <h2>说明</h2>
      <p>
        签到页面会从所有启用的运势类型中抽取一个进行展示，然后从该运势对应的启用宜词条库和忌词条库中分别随机抽取一个或多个词条进行展示。
      </p>
    </el-card>

    <el-dialog
      v-model="fortuneDialog.visible"
      :title="fortuneDialog.editing ? '编辑运势' : '新增运势'"
      width="430px"
      append-to-body
      destroy-on-close
    >
      <el-form
        ref="fortuneForm"
        :model="fortuneDialog.form"
        :rules="fortuneRules"
        label-width="88px"
      >
        <el-form-item label="运势名称" prop="name">
          <el-input
            v-model="fortuneDialog.form.name"
            maxlength="32"
            show-word-limit
          ></el-input>
        </el-form-item>
        <el-form-item label="展示颜色" prop="color">
          <div class="color-form-control">
            <el-color-picker v-model="fortuneDialog.form.color"></el-color-picker>
            <el-input
              v-model="fortuneDialog.form.color"
              maxlength="7"
              placeholder="#409EFF"
            ></el-input>
          </div>
        </el-form-item>
        <el-form-item label="运势说明" prop="description">
          <el-input
            v-model="fortuneDialog.form.description"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
          ></el-input>
        </el-form-item>
        <el-form-item label="显示排序" prop="sortOrder">
          <el-input-number
            v-model="fortuneDialog.form.sortOrder"
            :min="0"
            :max="9999"
          ></el-input-number>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch
            v-model="fortuneDialog.form.enabled"
            :disabled="!fortuneDialog.editing"
          ></el-switch>
          <span v-if="!fortuneDialog.editing" class="form-help">
            新运势保存后，请先配置宜、忌词条再启用
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fortuneDialog.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="loading.savingFortune"
          @click="saveFortune"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="adviceDialog.visible"
      :title="`${adviceDialog.editing ? '编辑' : '新增'}${adviceTypeLabel}词条`"
      width="430px"
      append-to-body
      destroy-on-close
    >
      <el-form
        ref="adviceForm"
        :model="adviceDialog.form"
        :rules="adviceRules"
        label-width="88px"
      >
        <el-form-item label="所属运势">
          <el-input :model-value="currentFortune?.name" disabled></el-input>
        </el-form-item>
        <el-form-item label="词条内容" prop="title">
          <el-input
            v-model="adviceDialog.form.title"
            maxlength="100"
            show-word-limit
          ></el-input>
        </el-form-item>
        <el-form-item label="词条说明" prop="description">
          <el-input
            v-model="adviceDialog.form.description"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
          ></el-input>
        </el-form-item>
        <el-form-item label="显示排序" prop="sortOrder">
          <el-input-number
            v-model="adviceDialog.form.sortOrder"
            :min="0"
            :max="9999"
          ></el-input-number>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="adviceDialog.form.enabled"></el-switch>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adviceDialog.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="loading.savingAdvice"
          @click="saveAdvice"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  Collection,
  Delete,
  Edit,
  Plus,
  Refresh,
} from '@element-plus/icons-vue';
import api from '@/common/api';
import myMessage from '@/common/message';

const emptyAdvicePool = () => ({
  recommended: [],
  avoid: [],
});

const emptyFortuneForm = () => ({
  id: null,
  name: '',
  color: '#409EFF',
  description: '',
  sortOrder: 0,
  enabled: false,
});

const emptyAdviceForm = () => ({
  id: null,
  title: '',
  description: '',
  sortOrder: 0,
  enabled: true,
});

export default {
  name: 'DailyCheckInAdmin',
  data() {
    return {
      Plus,
      Refresh,
      Edit,
      Collection,
      Delete,
      overview: {
        fortuneCount: 0,
        recommendedCount: 0,
        avoidCount: 0,
        todayCheckInCount: 0,
        fortunes: [],
      },
      currentFortune: null,
      advicePool: emptyAdvicePool(),
      loading: {
        overview: false,
        advice: false,
        savingFortune: false,
        savingAdvice: false,
      },
      fortuneDialog: {
        visible: false,
        editing: false,
        form: emptyFortuneForm(),
      },
      adviceDialog: {
        visible: false,
        editing: false,
        adviceType: 'recommended',
        form: emptyAdviceForm(),
      },
      fortuneRules: {
        name: [
          { required: true, message: '请输入运势名称', trigger: 'blur' },
          { max: 32, message: '运势名称不能超过32个字符', trigger: 'blur' },
        ],
        color: [
          { required: true, message: '请选择展示颜色', trigger: 'change' },
          {
            pattern: /^#[0-9A-Fa-f]{6}$/,
            message: '请输入六位十六进制颜色',
            trigger: ['blur', 'change'],
          },
        ],
        description: [
          { max: 255, message: '运势说明不能超过255个字符', trigger: 'blur' },
        ],
      },
      adviceRules: {
        title: [
          { required: true, message: '请输入词条内容', trigger: 'blur' },
          { max: 100, message: '词条内容不能超过100个字符', trigger: 'blur' },
        ],
        description: [
          { max: 255, message: '词条说明不能超过255个字符', trigger: 'blur' },
        ],
      },
    };
  },
  computed: {
    statisticCards() {
      return [
        {
          key: 'fortune',
          label: '运势类型数',
          value: this.overview.fortuneCount || 0,
          color: '#2D8CF0',
          icon: 'fa fa-diamond',
        },
        {
          key: 'recommended',
          label: '宜词条总数',
          value: this.overview.recommendedCount || 0,
          color: '#46B52D',
          icon: 'fa fa-book',
        },
        {
          key: 'avoid',
          label: '忌词条总数',
          value: this.overview.avoidCount || 0,
          color: '#F28C00',
          icon: 'fa fa-ban',
        },
        {
          key: 'today',
          label: '今日签到人数',
          value: this.overview.todayCheckInCount || 0,
          color: '#7B35D8',
          icon: 'fa fa-users',
        },
      ];
    },
    adviceTypeLabel() {
      return this.adviceDialog.adviceType === 'recommended' ? '宜' : '忌';
    },
  },
  mounted() {
    this.loadOverview();
  },
  methods: {
    loadOverview(preferredCode) {
      const selectedCode =
        preferredCode || (this.currentFortune && this.currentFortune.code);
      this.loading.overview = true;
      return api
        .admin_getDailyCheckInOverview()
        .then((res) => {
          this.overview = res.data.data;
          const fortunes = this.overview.fortunes || [];
          this.currentFortune =
            fortunes.find((item) => item.code === selectedCode) ||
            fortunes.find((item) => item.enabled) ||
            fortunes[0] ||
            null;
          return this.loadAdvice();
        })
        .finally(() => {
          this.loading.overview = false;
        });
    },
    refreshPage() {
      this.loadOverview().then(() => {
        myMessage.success('签到配置已刷新');
      });
    },
    selectFortune(fortune, scrollIntoView = false) {
      this.currentFortune = fortune;
      this.loadAdvice();
      if (scrollIntoView) {
        this.$nextTick(() => {
          const section = this.$refs.adviceSection?.$el || this.$refs.adviceSection;
          section?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        });
      }
    },
    loadAdvice() {
      if (!this.currentFortune) {
        this.advicePool = emptyAdvicePool();
        return Promise.resolve();
      }
      this.loading.advice = true;
      return api
        .admin_getDailyFortuneAdvice(this.currentFortune.code)
        .then((res) => {
          this.advicePool = res.data.data || emptyAdvicePool();
        })
        .finally(() => {
          this.loading.advice = false;
        });
    },
    openFortuneDialog(fortune) {
      this.fortuneDialog.editing = Boolean(fortune);
      this.fortuneDialog.form = fortune
        ? {
            id: fortune.id,
            name: fortune.name,
            color: fortune.color,
            description: fortune.description || '',
            sortOrder: fortune.sortOrder || 0,
            enabled: Boolean(fortune.enabled),
          }
        : {
            ...emptyFortuneForm(),
            sortOrder: (this.overview.fortunes?.length || 0) + 1,
          };
      this.fortuneDialog.visible = true;
      this.$nextTick(() => this.$refs.fortuneForm?.clearValidate());
    },
    saveFortune() {
      this.$refs.fortuneForm.validate((valid) => {
        if (!valid) return;
        this.loading.savingFortune = true;
        const request = this.fortuneDialog.editing
          ? api.admin_updateDailyFortune(
              this.fortuneDialog.form.id,
              this.fortuneDialog.form
            )
          : api.admin_createDailyFortune(this.fortuneDialog.form);
        request
          .then((res) => {
            const saved = res.data.data;
            this.fortuneDialog.visible = false;
            myMessage.success(
              this.fortuneDialog.editing ? '运势修改成功' : '运势新增成功'
            );
            return this.loadOverview(saved.code);
          })
          .finally(() => {
            this.loading.savingFortune = false;
          });
      });
    },
    deleteFortune(fortune) {
      this.$confirm(
        `删除运势“${fortune.name}”会同时删除其全部宜、忌词条，是否继续？`,
        '提示',
        { type: 'warning' }
      ).then(() => {
        api.admin_deleteDailyFortune(fortune.id).then(() => {
          myMessage.success('运势删除成功');
          this.loadOverview();
        });
      });
    },
    openAdviceDialog(adviceType, item) {
      this.adviceDialog.adviceType = adviceType;
      this.adviceDialog.editing = Boolean(item);
      this.adviceDialog.form = item
        ? {
            id: item.id,
            title: item.title,
            description: item.description || '',
            sortOrder: item.sortOrder || 0,
            enabled: Boolean(item.enabled),
          }
        : {
            ...emptyAdviceForm(),
            sortOrder:
              (this.advicePool[adviceType === 'recommended' ? 'recommended' : 'avoid']
                ?.length || 0) + 1,
          };
      this.adviceDialog.visible = true;
      this.$nextTick(() => this.$refs.adviceForm?.clearValidate());
    },
    saveAdvice() {
      this.$refs.adviceForm.validate((valid) => {
        if (!valid || !this.currentFortune) return;
        this.loading.savingAdvice = true;
        const request = this.adviceDialog.editing
          ? api.admin_updateDailyFortuneAdvice(
              this.adviceDialog.form.id,
              this.adviceDialog.form
            )
          : api.admin_createDailyFortuneAdvice(
              this.currentFortune.code,
              this.adviceDialog.adviceType,
              this.adviceDialog.form
            );
        request
          .then(() => {
            this.adviceDialog.visible = false;
            myMessage.success(
              this.adviceDialog.editing ? '词条修改成功' : '词条新增成功'
            );
            return this.loadOverview(this.currentFortune.code);
          })
          .finally(() => {
            this.loading.savingAdvice = false;
          });
      });
    },
    deleteAdvice(item) {
      this.$confirm(`确定删除词条“${item.title}”吗？`, '提示', {
        type: 'warning',
      }).then(() => {
        api.admin_deleteDailyFortuneAdvice(item.id).then(() => {
          myMessage.success('词条删除成功');
          this.loadOverview(this.currentFortune.code);
        });
      });
    },
  },
};
</script>

<style scoped>
.daily-check-admin-page {
  color: #303133;
}
.statistics-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 14px;
}
.statistic-card {
  align-items: center;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 6%);
  display: flex;
  min-height: 104px;
  padding: 13px 22px;
}
.statistic-icon {
  align-items: center;
  border-radius: 4px;
  color: #fff;
  display: flex;
  flex: 0 0 80px;
  font-size: 37px;
  height: 78px;
  justify-content: center;
}
.statistic-content {
  align-items: center;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 5px;
  justify-content: center;
  min-width: 0;
}
.statistic-content strong {
  font-size: 32px;
  line-height: 38px;
}
.statistic-content span {
  color: #303133;
  font-size: 15px;
  white-space: nowrap;
}
.management-card {
  margin-bottom: 14px;
}
.management-card :deep(.el-card__header) {
  padding: 11px 18px;
}
.management-card :deep(.el-card__body) {
  padding: 0 16px 12px;
}
.card-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  min-height: 32px;
}
.management-card .home-title {
  line-height: 32px;
  padding: 0;
}
.header-actions {
  align-items: center;
  display: flex;
  gap: 12px;
}
.header-actions :deep(.el-button) {
  height: 32px;
  margin-left: 0;
  padding: 8px 15px;
}
.table-scroll {
  overflow-x: auto;
}
.management-table {
  border-collapse: collapse;
  table-layout: fixed;
  width: 100%;
}
.management-table th,
.management-table td {
  border: 1px solid #e4e7ed;
  box-sizing: border-box;
  font-size: 13px;
  height: 36px;
  padding: 3px 8px;
  text-align: center;
  vertical-align: middle;
}
.management-table th {
  background: #f8f8f9;
  color: #202124;
  font-weight: 700;
}
.management-table tbody tr {
  transition: background-color 0.2s ease;
}
.management-table tbody tr:hover,
.management-table tbody tr.is-current {
  background: #f5f9ff;
}
.fortune-table {
  min-width: 1050px;
}
.fortune-table th:nth-child(1) {
  width: 55px;
}
.fortune-table th:nth-child(2) {
  width: 150px;
}
.fortune-table th:nth-child(3) {
  width: 170px;
}
.fortune-table th:nth-child(5) {
  width: 90px;
}
.fortune-table th:nth-child(6) {
  width: 105px;
}
.fortune-table th:nth-child(7) {
  width: 300px;
}
.fortune-name-badge {
  border-radius: 3px;
  color: #fff;
  display: inline-block;
  font-weight: 700;
  line-height: 24px;
  min-width: 48px;
  padding: 0 10px;
  text-align: center;
}
.color-swatch {
  border-radius: 2px;
  display: inline-block;
  height: 16px;
  margin-right: 9px;
  vertical-align: -3px;
  width: 16px;
}
.color-code {
  font-family: Consolas, Monaco, monospace;
}
.fortune-description {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-actions {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: center;
  white-space: nowrap;
}
.row-actions :deep(.el-button) {
  height: 29px;
  margin-left: 0;
  padding: 7px 11px;
}
.advice-heading {
  align-items: center;
  display: flex;
  gap: 14px;
}
.current-label {
  color: #606266;
  font-size: 14px;
  margin-left: 22px;
}
.current-fortune-badge {
  min-width: 58px;
}
.advice-grid {
  display: grid;
  gap: 24px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.advice-panel {
  border: 1px solid #e4e7ed;
  min-width: 0;
}
.advice-panel-title {
  border-bottom: 1px solid #e4e7ed;
  font-size: 18px;
  font-weight: 500;
  line-height: 36px;
  margin: 0;
  padding: 0 20px;
}
.recommended-title {
  color: #2d8cf0;
}
.avoid-title {
  color: #f56c6c;
}
.advice-table {
  min-width: 520px;
}
.advice-table th:nth-child(1) {
  width: 50px;
}
.advice-table th:nth-child(3) {
  width: 105px;
}
.advice-table th:nth-child(4) {
  width: 85px;
}
.advice-table th:nth-child(5) {
  width: 155px;
}
.advice-title-cell {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.compact-actions {
  gap: 8px;
}
.compact-actions :deep(.el-button) {
  padding-left: 11px;
  padding-right: 11px;
}
.empty-cell {
  color: #909399;
  height: 72px !important;
}
.advice-empty-state {
  color: #909399;
  padding: 48px 20px;
  text-align: center;
}
.explanation-card :deep(.el-card__body) {
  padding: 15px 18px 17px;
}
.explanation-card h2 {
  color: #2d8cf0;
  font-size: 20px;
  font-weight: 500;
  line-height: 28px;
  margin: 0 0 8px;
}
.explanation-card p {
  color: #303133;
  font-size: 14px;
  line-height: 24px;
  margin: 0;
}
.color-form-control {
  display: flex;
  gap: 10px;
  width: 100%;
}
.color-form-control :deep(.el-input) {
  flex: 1;
}
.form-help {
  color: #909399;
  font-size: 12px;
  margin-left: 10px;
}
:global(.el-dialog .el-input-number) {
  width: 180px;
}
@media screen and (max-width: 1200px) {
  .statistics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .advice-grid {
    grid-template-columns: 1fr;
  }
}
@media screen and (max-width: 768px) {
  .statistics-grid {
    grid-template-columns: 1fr;
  }
  .card-header,
  .advice-card-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
  .header-actions {
    flex-wrap: wrap;
  }
  .advice-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .current-label {
    margin-left: 0;
  }
}
</style>
