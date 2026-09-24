<template>
  <el-card class="points-details" shadow="never">
    <template #header>
      <div class="points-heading"><strong>OJ 积分明细</strong><el-tag effect="plain">按当前难度计分</el-tag></div>
    </template>
    <p class="points-rule">每题积分 = 当前难度基础积分 × 历史最佳完成比例。同一道题只计一次，难度或分值调整后同步变化。</p>
    <p class="points-note">ACM 通过得满分；OI 按最佳完成比例计分。仅统计公共题库的非比赛提交，团队积分单独计算。</p>
    <el-table :data="pageRows" empty-text="还没有获得积分，完成第一道题目后即可在这里查看。" stripe>
      <el-table-column label="题目" min-width="220">
        <template #default="{ row }"><router-link v-if="row.problemId" :to="{ name: 'ProblemDetails', params: { problemID: row.problemId } }">{{ row.problemId }} · {{ row.title }}</router-link><span v-else>{{ row.title }}</span></template>
      </el-table-column>
      <el-table-column prop="difficulty" label="当前难度" min-width="110" />
      <el-table-column label="基础积分" width="110" align="right"><template #default="{ row }">{{ money(row.basePoints) }}</template></el-table-column>
      <el-table-column label="最佳完成比例" min-width="175" align="right"><template #default="{ row }">{{ (Number(row.completionRatio) * 100).toFixed(2) }}% <el-tooltip v-if="row.estimated" content="旧提交未保存当时的满分，已使用迁移时的题目满分估算并固定。"><el-tag size="small" type="warning">历史估算</el-tag></el-tooltip></template></el-table-column>
      <el-table-column label="获得积分" width="110" align="right"><template #default="{ row }"><strong class="points-value">{{ money(row.points) }}</strong></template></el-table-column>
    </el-table>
    <el-pagination v-if="rows.length > 10" v-model:current-page="page" :page-size="10" :total="rows.length" layout="total, prev, pager, next" class="points-pagination" />
  </el-card>
</template>
<script>
export default {
  props: { rows: { type: Array, default: () => [] } },
  data: () => ({ page: 1 }),
  computed: { pageRows() { return this.rows.slice((this.page - 1) * 10, this.page * 10); } },
  watch: { rows() { this.page = 1; } },
  methods: { money(value) { return Number(value || 0).toFixed(2); } },
};
</script>
<style scoped>
.points-details { margin-top: 20px; text-align: left; }
.points-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.points-rule { color: #334155; line-height: 1.7; margin: 0 0 8px; }
.points-note { color: #64748b; font-size: 13px; line-height: 1.7; margin: 0 0 16px; }
.points-value { color: #2563eb; font-variant-numeric: tabular-nums; }
.points-pagination { margin-top: 18px; justify-content: flex-end; }
</style>
