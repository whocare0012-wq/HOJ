<template>
  <el-row class="rank-page" type="flex" justify="space-around">
    <el-col :span="24">
      <el-card :padding="10">
        <template #header>
          <div>
            <span class="panel-title">{{ $t('m.ACM_Ranklist') }}</span>
          </div>
        </template>
        <div class="echarts">
          <ECharts
            ref="rankChart"
            :option="options"
            :loading="loadingTable"
            :loading-options="chartLoadingOptions"
            :autoresize="true"
          ></ECharts>
          <RankChartActions
            :mode="chartMode"
            supports-stack
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
        :seq-config="{ seqMethod }"
        auto-resize
        style="font-weight: 500;"
      >
        <vxe-table-column type="seq" title="#" min-width="50"></vxe-table-column>
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
        <vxe-table-column field="ac" :title="$t('m.AC')" min-width="80">
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
            </span>
          </template>
        </vxe-table-column>
        <vxe-table-column :title="$t('m.Total')" min-width="100" field="total">
        </vxe-table-column>
        <vxe-table-column :title="$t('m.Rating')" min-width="80">
          <template v-slot="{ row }">
            <span>{{ getACRate(row.ac, row.total) }}</span>
          </template>
        </vxe-table-column>
        <vxe-table-column
          field="signature"
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
        <el-table-column prop="ac" :label="$t('m.AC')"></el-table-column>
        <el-table-column prop="total" :label="$t('m.Total')"></el-table-column>
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
  name: 'acm-rank',
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
      loadingTable: false,
      chartLoadingOptions: {
        maskColor: 'rgba(250, 250, 250, 0.8)',
        color: '#c23531',
      },
      screenWidth: 768,
      dataRank: [],
      chartRankData: [],
      chartDataVisible: false,
      chartMode: 'bar',
      options: {
        aria: {
          enabled: true,
          description: this.$t('m.ACM_Ranklist'),
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
          data: ['AC', 'Total'],
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
            magicType: { show: true, type: ['line', 'bar', 'stack'] },
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
            name: this.$t('m.AC'),
            type: 'bar',
            data: [0],
            itemStyle: {
              color: '#91c7ae',
            },
            markPoint: {
              data: [{ type: 'max', name: 'max' }],
            },
          },
          {
            name: this.$t('m.Total'),
            type: 'bar',
            data: [0],
            itemStyle: {
              color: '#6ab0b8',
            },
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
      api
        .getUserRank(page, this.limit, RULE_TYPE.ACM, searchKeyword)
        .then((res) => {
          this.loadingTable = false;
          if (page === 1) {
            this.changeCharts(res.data.data.records.slice(0, 10));
          }
          this.total = res.data.data.total;
          this.dataRank = res.data.data.records;
        })
        .catch(() => {
          this.loadingTable = false;
        });
    },
    seqMethod({ rowIndex }) {
      return this.limit * (this.page - 1) + rowIndex + 1;
    },
    changeCharts(rankData) {
      let [usernames, acData, totalData] = [[], [], []];
      this.chartRankData = rankData.map((row) => ({
        username: row.username,
        ac: row.ac,
        total: row.total,
      }));
      rankData.forEach((ele) => {
        usernames.push(ele.username);
        acData.push(ele.ac);
        totalData.push(ele.total);
      });
      this.options.xAxis[0].data = usernames;
      this.options.series[0].data = acData;
      this.options.series[1].data = totalData;
    },
    changeChartMode(mode) {
      this.chartMode = mode;
      const stackName = mode === 'stack' ? 'rank-total' : null;
      this.options.series.forEach((series) => {
        series.type = mode === 'line' ? 'line' : 'bar';
        series.stack = stackName;
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
        'acm-rank.png'
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
<style>
.rank-signature-body img {
  height: 50px !important;
  width: 50px !important;
}
.rank-signature-body p {
  margin: 0;
  padding: 0;
}
.user-avatar {
  margin-right: 5px !important;
  vertical-align: middle;
}
.search-btn {
  color: #fff !important;
  background-color: #409eff !important;
  border-color: #409eff !important;
}
</style>
