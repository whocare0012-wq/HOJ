<template>
  <el-row class="rank-page" type="flex" justify="space-around">
    <el-col :span="24">
      <el-card :padding="10">
        <template #header>
          <div>
            <span class="panel-title">{{ $t('m.OI_Ranklist') }}</span>
          </div>
        </template>
        <el-alert title="OJ 积分 = 各题当前基础积分 × 历史最佳完成比例之和" description="包含 ACM 与 OI 普通提交；同题只计一次。按积分、通过题数排序，两项均相同时并列。难度分值调整后同步更新。" type="info" :closable="false" show-icon />
        <div class="echarts">
          <ECharts
            ref="rankChart"
            :option="options"
            :loading="loadingTable"
            :loading-options="chartLoadingOptions"
            autoresize
          ></ECharts>
          <RankChartActions
            :mode="chartMode"
            @data-view="chartDataVisible = true"
            @change-mode="changeChartMode"
            @save-image="saveChartImage"
          ></RankChartActions>
        </div>
      </el-card>
      <el-card :padding="10" style="text-align: center;">
        <el-input
          class="rank-search-input"
          size="large"
          :placeholder="$t('m.Rank_Search_Placeholder')"
          v-model="searchUser"
          @keyup.enter="getRankData(1)"
        >
          <template #append>
            <el-button
              :icon="legacyElementIcons['el-icon-search']"
              class="search-btn"
              :aria-label="$t('m.Rank_Search_Placeholder')"
              @click="getRankData(1)"
            ></el-button>
          </template>
        </el-input>
      </el-card>
      <vxe-table
        class="rank-table"
        :data="dataRank"
        :loading="loadingTable"
        align="center"
        highlight-hover-row
        auto-resize
        :seq-config="{ seqMethod }"
        style="font-weight: 500;"
      >
        <vxe-table-column field="rankPosition" title="#" min-width="50"></vxe-table-column>
        <vxe-table-column
          field="username"
          :title="$t('m.User')"
          min-width="200"
          show-overflow
          align="left"
        >
          <template v-slot="{ row }">
            <avatar
              :username="row.username"
              :inline="true"
              :size="25"
              color="#FFF"
              :src="row.avatar"
              class="user-avatar"
            ></avatar>
            <router-link
              :to="{
                path: '/user-home',
                query: { uid: row.uid, username: row.username },
              }"
              class="rank-user-link"
            >
              {{ row.username }}
            </router-link>
            <span style="margin-left:2px" v-if="row.titleName">
              <el-tag
                class="rank-table-tag"
                effect="dark"
                size="small"
                :color="row.titleColor"
              >
                {{ row.titleName }}
              </el-tag>
            </span>
          </template>
        </vxe-table-column>
        <vxe-table-column
          field="nickname"
          :title="$t('m.Nickname')"
          width="160"
        >
          <template v-slot="{ row }">
            <el-tag
              class="rank-table-tag"
              effect="plain"
              size="small"
              v-if="row.nickname"
              :type="nicknameColor(row.nickname)"
            >
              {{ row.nickname }}
            </el-tag>
          </template>
        </vxe-table-column>
        <vxe-table-column :title="$t('m.UserHome_Score')" min-width="100">
          <template v-slot="{ row }">
            <span>{{ Number(row.score || 0).toFixed(2) }}</span>
          </template>
        </vxe-table-column>
        <vxe-table-column
          :title="$t('m.AC') + '/' + $t('m.Total')"
          min-width="100"
        >
          <template v-slot="{ row }">
            <span>
              <router-link
                :to="{
                  path: '/status',
                  query: { username: row.username, status: 0 },
                }"
                class="rank-ac-link"
              >
                {{ row.ac }}
              </router-link>
              <span>/{{ row.total }}</span>
            </span>
          </template>
        </vxe-table-column>
        <vxe-table-column :title="$t('m.Rating')" min-width="80">
          <template v-slot="{ row }">
            <span>{{ getACRate(row.ac, row.total) }}</span>
          </template>
        </vxe-table-column>
        <vxe-table-column
          :title="$t('m.Signature')"
          min-width="300"
          show-overflow="ellipsis"
          align="left"
        >
          <template v-slot="{ row }">
            <span v-katex class="rank-signature-body" v-if="row.signature">{{
              row.signature
            }}</span>
          </template>
        </vxe-table-column>
      </vxe-table>
      <Pagination
        :total="total"
        v-model:page-size="limit"
        v-model:current="page"
        @on-change="getRankData"
        show-sizer
        @on-page-size-change="getRankData(1)"
        :layout="'prev, pager, next, sizes'"
        legacy-size
      ></Pagination>
    </el-col>
    <el-dialog
      v-model="chartDataVisible"
      :title="$t('m.Rank_Data_View')"
      width="min(560px, calc(100vw - 32px))"
    >
      <el-table :data="chartRankData" max-height="420">
        <el-table-column prop="username" :label="$t('m.User')"></el-table-column>
        <el-table-column prop="score" :label="$t('m.Score')"></el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="chartDataVisible = false">
          {{ $t('m.Close') }}
        </el-button>
      </template>
    </el-dialog>
  </el-row>
</template>

<script>
import { defineAsyncComponent } from 'vue';
import api from '@/common/api';
import { downloadDataUrl } from '@/common/download-data-url';
import utils from '@/common/utils';
import { RULE_TYPE } from '@/common/constants';
import { mapGetters } from 'vuex';
import Avatar from '@/components/common/Avatar.vue';
import RankChartActions from '@/components/oj/rank/RankChartActions.vue';
const Pagination = defineAsyncComponent(() => import('@/components/oj/common/Pagination'));
export default {
  name: 'oi-rank',
  components: {
    Pagination,
    Avatar,
    RankChartActions,
  },
  data() {
    return {
      page: 1,
      limit: 30,
      total: 0,
      searchUser: null,
      dataRank: [],
      loadingTable: false,
      chartRankData: [],
      chartDataVisible: false,
      chartMode: 'bar',
      chartLoadingOptions: {
        maskColor: 'rgba(250, 250, 250, 0.8)',
        color: '#c23531',
      },
      screenWidth: 768,
      options: {
        aria: {
          enabled: true,
          description: this.$t('m.OI_Ranklist'),
        },
        color: [
          '#c23531',
          '#2f4554',
          '#61a0a8',
          '#d48265',
          '#91c7ae',
          '#749f83',
          '#ca8622',
          '#bda29a',
          '#6e7074',
          '#546570',
          '#c4ccd3',
        ],
        tooltip: {
          trigger: 'axis',
        },
        legend: {
          data: ['Score'],
        },
        grid: {
          x: '3%',
          x2: '3%',
          left: '8%',
          right: '8%',
        },
        toolbox: {
          show: true,
          feature: {
            dataView: { show: true, readOnly: true },
            magicType: { show: true, type: ['line', 'bar'] },
            saveAsImage: { show: true },
          },
          right: '8%',
          top: '5%',
        },
        calculable: true,
        xAxis: [
          {
            type: 'category',
            data: ['root'],
            boundaryGap: true,
            axisLine: {
              show: true,
            },
            axisLabel: {
              interval: 0,
              showMinLabel: true,
              showMaxLabel: true,
              align: 'center',
              formatter: (value, index) => {
                if (this.screenWidth < 768) {
                  if (this.isAuthenticated && this.userInfo.username == value) {
                    return utils.breakLongWords(value, 14);
                  } else {
                    return '';
                  }
                } else {
                  return utils.breakLongWords(value, 14);
                }
              },
            },
            axisTick: {
              alignWithLabel: true,
            },
          },
        ],
        yAxis: [
          {
            type: 'value',
            axisLine: {
              show: true,
            },
            axisLabel: {
              rotate: 50,
              textStyle: {
                fontSize: 12,
              },
            },
          },
        ],
        series: [
          {
            name: this.$t('m.Score'),
            type: 'bar',
            data: [0],
            barMaxWidth: '80',
            markPoint: {
              data: [{ type: 'max', name: 'max' }],
            },
          },
        ],
      },
    };
  },
  created() {
    this.screenWidth = window.screen.width;
    const that = this;
    window.onresize = () => {
      return (() => {
        that.screenWidth = document.documentElement.clientWidth;
      })();
    };
  },
  mounted() {
    this.getRankData(1);
  },
  methods: {
    getRankData(page = this.page) {
      page = Math.max(1, Number(page) || 1);
      this.page = page;
      this.loadingTable = true;
      const searchKeyword =
        typeof this.searchUser === 'string'
          ? this.searchUser.trim() || null
          : this.searchUser;
      api.getUserRank(page, this.limit, RULE_TYPE.OI, searchKeyword).then(
        (res) => {
          if (page === 1) {
            this.changeCharts(res.data.data.records.slice(0, 10));
          }
          this.total = res.data.data.total;
          this.dataRank = res.data.data.records;
          this.loadingTable = false;
        },
        (err) => {
          this.loadingTable = false;
        }
      );
    },
    seqMethod({ rowIndex }) {
      return this.limit * (this.page - 1) + rowIndex + 1;
    },
    changeCharts(rankData) {
      let [usernames, scores] = [[], []];
      this.chartRankData = rankData.map((row) => ({
        username: row.username,
        score: row.score,
      }));
      rankData.forEach((ele) => {
        usernames.push(ele.username);
        scores.push(ele.score);
      });
      this.options.xAxis[0].data = usernames;
      this.options.series[0].data = scores;
    },
    changeChartMode(mode) {
      this.chartMode = mode;
      this.options.series.forEach((series) => {
        series.type = mode === 'line' ? 'line' : 'bar';
        series.stack = null;
      });
      this.options.xAxis[0].boundaryGap = mode !== 'line';
    },
    saveChartImage() {
      const chart = this.$refs.rankChart;
      if (!chart) {
        return;
      }
      downloadDataUrl(
        chart.getDataURL({
          type: 'png',
          pixelRatio: 2,
          backgroundColor: '#fff',
          excludeComponents: ['toolbox'],
        }),
        'oi-rank.png'
      );
    },
    getACRate(ac, total) {
      return utils.getACRate(ac, total);
    },
    nicknameColor(nickname) {
      let typeArr = ['', 'success', 'info', 'danger', 'warning'];
      let index = nickname.length % 5;
      return typeArr[index];
    },
  },
  computed: {
    ...mapGetters(['isAuthenticated', 'userInfo']),
  },
};
</script>

<style scoped>
.echarts {
  margin: 0 auto;
  position: relative;
  width: 100%;
  height: 400px;
}
:deep(.rank-table .vxe-table--empty-content) {
  color: #000;
}
:deep(.rank-table-tag.el-tag--small) {
  height: 24px;
  line-height: 22px;
  padding: 0 8px;
}
:deep(.rank-search-input .el-input__wrapper) {
  padding: 0;
}
:deep(.rank-search-input .el-input-group__append) {
  width: 57px;
}
:deep(.rank-search-input .el-input__inner) {
  height: 40px;
  line-height: 40px;
  padding: 0 15px;
}
:deep(.rank-search-input .search-btn) {
  height: 38px;
  padding: 12px 20px;
  width: 56px;
}
.rank-user-link {
  color: #2d8cf0;
}
.rank-ac-link {
  color: rgb(87, 163, 243);
}
@media screen and (max-width: 768px) {
  :deep(.el-card__body) {
    padding: 0 !important;
  }
}
.user-avatar {
  margin-right: 5px !important;
  vertical-align: middle;
}
@media screen and (min-width: 768px) {
  .rank-search-input.el-input-group {
    width: 50%;
  }
}
@media screen and (min-width: 1050px) {
  .rank-search-input.el-input-group {
    width: 30%;
  }
}
</style>
