<template>
  <el-card>
    <template #header>
      <div class="statistics-header">
        <span class="home-title panel-title">
          <el-icon class="statistics-title-icon"><DataLine /></el-icon>
          {{$t('m.Statistics_Submissions_In_The_Last_Week')}}
        </span>
        <el-button
          class="statistics-refresh-button"
          type="primary"
          :icon="legacyElementIcons['el-icon-refresh']"
          size="small"
          :loading="loading"
          @click="getLastWeekSubmissionStatistics(true)"
          >{{ $t('m.Refresh') }}</el-button>
      </div>
    </template>
    <div
      class="echarts"
      v-loading="loading"
    >
      <ECharts
        :option="options"
        :autoresize="true"
      ></ECharts>
    </div>
  </el-card>
</template>
<script>
import api from "@/common/api";
import { DataLine } from "@element-plus/icons-vue";
import { mapGetters } from 'vuex';
export default {
  name: "SubmissionStatistics",
  components: {
    DataLine,
  },
  data() {
    return {
      loading: false,
      options: {
        tooltip: {
          trigger: "axis",
          axisPointer: {
            type: "cross",
            label: {
              backgroundColor: "#6a7985",
            },
          },
        },
        legend: {
          data: [this.$t("m.AC"), this.$t("m.Total")],
          left: "center",
          top: 0,
        },
        toolbox: {
          right: -10,
          top: -8,
          feature: {
            saveAsImage: { show: true, title: this.$t("m.save_as_image") },
          },
        },
        grid: {
          left: "3%",
          right: "4%",
          top: 52,
          bottom: "4%",
          containLabel: true,
        },
        xAxis: [
          {
            type: "category",
            boundaryGap: false,
            data: [],
          },
        ],
        yAxis: [
          {
            type: "value",
          },
        ],
        series: [
          {
            name: this.$t("m.AC"),
            type: "line",
            stack: "Total",
            areaStyle: {},
            emphasis: {
              focus: "series",
            },
            color: "#91cc75",
            data: [0, 0, 0, 0, 0, 0, 0],
          },
          {
            name: this.$t("m.Total"),
            type: "line",
            stack: "Total",
            label: {
              color: "#73c0de",
              show: true,
              position: "top",
            },
            areaStyle: {},
            emphasis: {
              focus: "series",
            },
            color: "#73c0de",
            data: [0, 0, 0, 0, 0, 0, 0],
          },
        ],
      },
    };
  },
  mounted() {
    this.getLastWeekSubmissionStatistics(false);
  },
  methods: {
    getLastWeekSubmissionStatistics(forceRefresh) {
      this.loading = true;
      api.getLastWeekSubmissionStatistics(forceRefresh).then(
        (res) => {
          this.options.xAxis[0].data = res.data.data.dateStrList;
          this.options.series[0].data = res.data.data.acCountList;
          this.options.series[1].data = res.data.data.totalCountList;
          this.loading = false;
        },
        (err) => {
          this.loading = false;
        }
      );
    },
  },
  computed: {
    ...mapGetters(['webLanguage'])
  },
  watch:{
    webLanguage(newVal, oldVal){
        this.options.legend.data = [this.$t("m.AC"), this.$t("m.Total")];
        if(this.options.series != null && this.options.series.length == 2){
            this.options.series[0].name = this.$t("m.AC");
            this.options.series[1].name = this.$t("m.Total");
        }
    }
  }
};
</script>
<style scoped>
.statistics-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
}
.statistics-header .panel-title {
  align-items: center;
  display: inline-flex;
  gap: 6px;
  line-height: 32px;
  padding: 0;
}
.statistics-title-icon {
  font-size: 18px;
}
:deep(.el-card__header) {
  padding: 9px 20px;
}
:deep(.statistics-refresh-button.el-button--small) {
  flex: 0 0 auto;
  height: 32px;
  padding: 9px 15px;
}
.echarts {
  height: 400px;
  width: 100%;
}
:deep(.el-card__body) {
  padding: 20px 10px !important;
}
</style>
