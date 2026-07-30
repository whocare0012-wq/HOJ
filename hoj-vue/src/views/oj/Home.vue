<template>
  <div>
    <el-row :gutter="20">
      <el-col
        :md="15"
        :sm="24"
      >
        <el-card>
          <template #header>
            <div
                class="content-center"
            >
              <span class="panel-title home-title welcome-title">{{ $t('m.Welcome_to')
                }}{{ websiteConfig.shortName }}</span>
            </div>
          </template>
          <el-carousel
            :interval="interval"
            :height="srcHight"
            class="img-carousel"
            arrow="always"
            indicator-position="outside"
          >
            <el-carousel-item
              v-for="(item, index) in carouselImgList"
              :key="index"
            >
              <el-image
                :src="item.url"
                fit="fill"
              >
                <template #error>
                  <div
                      class="image-slot"
                  >
                    <i class="el-icon-picture-outline"></i>
                  </div>
                </template>
              </el-image>
            </el-carousel-item>
          </el-carousel>
        </el-card>
        <Announcements class="card-top"></Announcements>
        <SubmissionStatistic class="card-top"></SubmissionStatistic>
        <el-card class="card-top">
          <template #header>
            <div
                class="clearfix"
            >
              <span class="panel-title home-title">
                <i class="el-icon-magic-stick"></i> {{
                $t('m.Latest_Problem')
              }}</span>
            </div>
          </template>
          <div
            class="latest-problem-table-header"
            role="row"
          >
            <span role="columnheader">{{ $t('m.Problem_ID') }}</span>
            <span role="columnheader">{{ $t('m.Title') }}</span>
            <span role="columnheader">{{ $t('m.Recent_Update') }}</span>
          </div>
          <vxe-table
            class="latest-problem-table"
            border="inner"
            highlight-hover-row
            stripe
            :loading="loading.recentUpdatedProblemsLoading"
            auto-resize
            :data="recentUpdatedProblems"
            :show-header="false"
            @cell-click="goProblem"
          >
            <vxe-table-column
              field="problemId"
              :title="$t('m.Problem_ID')"
              min-width="100"
              show-overflow
              align="center"
            >
            </vxe-table-column>
            <vxe-table-column
              field="title"
              :title="$t('m.Title')"
              show-overflow
              min-width="130"
              align="center"
            >
            </vxe-table-column>
            <vxe-table-column
              field="gmtModified"
              :title="$t('m.Recent_Update')"
              show-overflow
              min-width="96"
              align="center"
            >
              <template v-slot="{ row }">
                <el-tooltip
                  :content="$filters.localtime(row.gmtModified)"
                  placement="top"
                >
                  <span>{{ $filters.fromNow(row.gmtModified) }}</span>
                </el-tooltip>
              </template>
            </vxe-table-column>

          </vxe-table>
        </el-card>
      </el-col>
      <el-col
        :md="9"
        :sm="24"
        class="phone-margin"
      >
        <el-card class="daily-check-in-card">
          <template #header>
            <div class="daily-check-in-header">
              <span class="panel-title home-title">
                <i class="fa fa-calendar-check-o daily-check-in-title-icon"></i>
                {{ $t('m.Daily_Check_In') }}
              </span>
              <el-button
                type="success"
                size="small"
                :loading="loading.dailyCheckInSubmitting"
                :disabled="isAuthenticated && checkInStatus.checkedIn"
                @click="handleDailyCheckIn"
              >
                <i
                  :class="checkInStatus.checkedIn ? 'el-icon-circle-check' : 'el-icon-date'"
                ></i>
                {{
                  checkInStatus.checkedIn
                    ? $t('m.Checked_In_Today')
                    : $t('m.Check_In_Now')
                }}
              </el-button>
            </div>
          </template>
          <div v-loading="loading.dailyCheckIn" class="daily-check-in-content">
            <div
              v-if="isAuthenticated && checkInStatus.checkedIn"
              class="daily-fortune-panel"
            >
              <div class="daily-fortune-owner">
                <strong
                  :class="fortuneToneClass"
                  :style="fortuneColorStyle"
                >
                  {{ checkInStatus.displayName || checkInStatus.username }}
                </strong>
                <span>{{ $t('m.Daily_Fortune_Of') }}</span>
              </div>

              <div
                class="daily-fortune-level"
                :class="fortuneToneClass"
                :style="fortuneColorStyle"
              >
                § {{ fortuneLabel }} §
              </div>
              <div
                v-if="checkInStatus.fortuneDescription"
                class="daily-fortune-summary"
              >
                {{ checkInStatus.fortuneDescription }}
              </div>

              <div class="daily-fortune-advice-grid">
                <div class="daily-fortune-advice-column">
                  <div
                    v-for="(item, index) in checkInStatus.recommendedItems"
                    :key="`recommended-${index}-${item.title}`"
                    class="daily-fortune-advice-item"
                  >
                    <div class="daily-fortune-advice-title recommended">
                      <strong>{{ $t('m.Recommended') }}：</strong>{{ item.title }}
                    </div>
                    <div v-if="item.description" class="daily-fortune-advice-description">
                      {{ item.description }}
                    </div>
                  </div>
                </div>

                <div class="daily-fortune-advice-column">
                  <div
                    v-for="(item, index) in checkInStatus.avoidItems"
                    :key="`avoid-${index}-${item.title}`"
                    class="daily-fortune-advice-item"
                  >
                    <div class="daily-fortune-advice-title avoid">
                      <strong>{{ $t('m.Avoid') }}：</strong>{{ item.title }}
                    </div>
                    <div v-if="item.description" class="daily-fortune-advice-description">
                      {{ item.description }}
                    </div>
                  </div>
                </div>
              </div>

              <div class="daily-check-in-streak">
                {{ $t('m.Consecutive_Check_In_Prefix') }}
                <strong>{{ checkInStatus.consecutiveCheckInDays || 0 }}</strong>
                {{ $t('m.Days_Unit') }}
              </div>
            </div>
            <div
              v-else-if="isAuthenticated"
              class="daily-check-in-login-prompt daily-check-in-pending"
            >
              <span>{{ $t('m.Not_Checked_In_Today') }}</span>
            </div>
            <div v-else class="daily-check-in-login-prompt">
              <i class="fa fa-calendar-check-o"></i>
              <span>{{ $t('m.Check_In_Login_Prompt') }}</span>
            </div>
          </div>
        </el-card>

        <template v-if="contests.length">
          <el-card class="recent-contests-card card-top">
            <template #header>
              <div
                  class="clearfix title content-center"
              >
                <div class="home-title home-contest">
                  <i class="el-icon-trophy"></i> {{ $t('m.Recent_Contest') }}
                </div>
              </div>
            </template>
            <div class="contest-card-list">
            <el-card
              shadow="hover"
              v-for="(contest, index) in contests"
              :key="contest.id || index"
              class="contest-card"
              :class="
                contest.status == 0
                  ? 'contest-card-running'
                  : 'contest-card-schedule'
              "
            >
              <template #header>
                <div
                    class="clearfix contest-header"
                >
                  <a
                    class="contest-title"
                    @click="goContest(contest.id)"
                  >{{
                    contest.title
                  }}</a>
                  <div class="contest-status">
                    <el-tag
                      effect="dark"
                      size="default"
                      :color="CONTEST_STATUS_REVERSE[contest.status]['color']"
                    >
                      <i
                        class="fa fa-circle"
                        aria-hidden="true"
                      ></i>
                      {{
                        $t('m.' + CONTEST_STATUS_REVERSE[contest.status]['name'])
                      }}
                    </el-tag>
                  </div>
                </div>
              </template>
              <div class="contest-type-auth">
                <template v-if="contest.type == 0">
                  <el-button
                    :type="'primary'"
                    round
                    @click="goContestList(contest.type)"
                    size="small"
                    style="margin-right: 10px;"
                  ><i class="fa fa-trophy"></i>
                    {{ $filters.parseContestType(contest.type) }}
                  </el-button>
                </template>
                <template v-else>
                  <el-tooltip
                    :content="
                      $t('m.Contest_Rank') +
                        '：' +
                        (contest.oiRankScoreType == 'Recent'
                          ? $t(
                              'm.Based_on_The_Recent_Score_Submitted_Of_Each_Problem'
                            )
                          : $t(
                              'm.Based_on_The_Highest_Score_Submitted_For_Each_Problem'
                            ))
                    "
                    placement="top"
                  >
                    <el-button
                      :type="'warning'"
                      round
                      @click="goContestList(contest.type)"
                      size="small"
                      style="margin-right: 10px;"
                    ><i class="fa fa-trophy"></i>
                      {{ $filters.parseContestType(contest.type) }}
                    </el-button>
                  </el-tooltip>
                </template>
                <el-tooltip
                  :content="$t('m.' + CONTEST_TYPE_REVERSE[contest.auth].tips)"
                  placement="top"
                  effect="light"
                >
                  <el-tag
                    :type="CONTEST_TYPE_REVERSE[contest.auth]['color']"
                    size="default"
                    effect="plain"
                  >
                    {{ $t('m.' + CONTEST_TYPE_REVERSE[contest.auth]['name']) }}
                  </el-tag>
                </el-tooltip>
              </div>
              <ul class="contest-info">
                <li>
                  <el-button
                    type="primary"
                    round
                    size="small"
                    style="margin-top: 4px;"
                  ><i class="fa fa-calendar"></i>
                    {{ $filters.localtime(contest.startTime, 'MM-DD HH:mm') }}
                  </el-button>
                </li>
                <li>
                  <el-button
                    type="success"
                    round
                    size="small"
                    style="margin-top: 4px;"
                  ><i class="fa fa-clock-o"></i>
                    {{ getDuration(contest.startTime, contest.endTime) }}
                  </el-button>
                </li>
                <li>
                  <el-button
                    size="small"
                    round
                    plain
                    v-if="contest.count != null"
                  >
                    <i
                      class="el-icon-user-solid"
                      style="color:rgb(48, 145, 242);"
                    ></i>x{{ contest.count }}
                  </el-button>
                </li>
              </ul>
            </el-card>
            </div>
          </el-card>
        </template>
        <el-card class="weekly-rank-card card-top">
          <template #header>
            <div
                class="clearfix"
            >
              <span class="panel-title home-title">
                <i class="el-icon-s-data weekly-rank-title-icon"></i> {{ $t('m.Recent_7_Days_AC_Rank')}}
              </span>
            </div>
          </template>
          <vxe-table
            class="weekly-rank-table"
            border="inner"
            stripe
            auto-resize
            align="center"
            :data="recentUserACRecord"
            max-height="500px"
            :loading="loading.recent7ACRankLoading"
          >
            <vxe-table-column
              type="seq"
              title="#"
              min-width="50"
            >
              <template v-slot="{ rowIndex }">
                <span :class="getRankTagClass(rowIndex)">{{ rowIndex + 1 }}
                </span>
                <span :class="'cite no' + rowIndex"></span>
              </template>
            </vxe-table-column>
            <vxe-table-column
              field="username"
              :title="$t('m.Username')"
              min-width="200"
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
                <a
                  @click="goUserHome(row.username, row.uid)"
                  style="color:#2d8cf0;"
                >{{ row.username }}</a>
                <span
                  style="margin-left:2px"
                  v-if="row.titleName"
                >
                  <el-tag
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
              field="ac"
              :title="$t('m.AC')"
              min-width="50"
              align="left"
            >
            </vxe-table-column>
          </vxe-table>
        </el-card>
        <el-card class="card-top">
          <template #header>
            <div
                class="clearfix title"
            >
              <span class="home-title panel-title">
                <i class="el-icon-monitor"></i> {{ $t('m.Supported_Remote_Online_Judge') }}
              </span>
            </div>
          </template>
          <el-row
            :gutter="20"
            class="remote-judge-grid"
          >
            <el-col
              :md="8"
              :sm="24"
              v-for="(oj, index) in remoteJudgeList"
              :key="index"
            >
              <a
                :href="oj.url"
                target="_blank"
              >
                <el-tooltip
                  :content="oj.name"
                  placement="top"
                >
                  <el-image
                    :src="oj.logo"
                    fit="fill"
                    class="oj-logo"
                    :class="
                      oj.status ? 'oj-normal ' + oj.name : 'oj-error ' + oj.name
                    "
                  >
                    <template #error>
                      <div
                          class="image-slot"
                      >
                        <i class="el-icon-picture-outline"></i>
                      </div>
                    </template>
                  </el-image>
                </el-tooltip>
              </a>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { defineAsyncComponent } from 'vue';
import time from "@/common/time";
import api from "@/common/api";
import {
  CONTEST_STATUS_REVERSE,
  CONTEST_TYPE_REVERSE,
} from "@/common/constants";
import { mapState, mapGetters } from "vuex";
import Avatar from "@/components/common/Avatar.vue";
import myMessage from "@/common/message";
import homeImage from '@/assets/home1.jpeg'
import hduLogo from '@/assets/hdu-logo.png'
import pojLogo from '@/assets/poj-logo.png'
import codeforcesLogo from '@/assets/codeforces-logo.png'
import gymLogo from '@/assets/gym-logo.png'
import atcoderLogo from '@/assets/atcoder-logo.png'
import spojLogo from '@/assets/spoj-logo.png'
import libreLogo from '@/assets/libre-logo.png'
const Announcements = defineAsyncComponent(() => import("@/components/oj/common/Announcements.vue"));
const SubmissionStatistic = defineAsyncComponent(() => import("@/components/oj/home/SubmissionStatistic.vue"));
const RECENT_AC_RANK_REFRESH_INTERVAL = 60000;
const FORTUNE_LABEL_KEYS = {
  great_luck: "m.Fortune_Great_Luck",
  medium_luck: "m.Fortune_Medium_Luck",
  small_luck: "m.Fortune_Small_Luck",
  neutral: "m.Fortune_Neutral",
  bad_luck: "m.Fortune_Bad_Luck",
  great_bad_luck: "m.Fortune_Great_Bad_Luck",
};
export default {
  name: "home",
  components: {
    Announcements,
    SubmissionStatistic,
    Avatar,
  },
  data() {
    return {
      interval: 5000,
      recentUpdatedProblems: [],
      recentUserACRecord: [],
      CONTEST_STATUS_REVERSE: {},
      CONTEST_TYPE_REVERSE: {},
      contests: [],
      loading: {
        recent7ACRankLoading: false,
        recent7ACRankRefreshing: false,
        recentUpdatedProblemsLoading: false,
        recentContests: false,
        dailyCheckIn: false,
        dailyCheckInSubmitting: false,
      },
      dailyCheckInTimer: null,
      recent7ACRankTimer: null,
      checkInStatus: {
        checkedIn: false,
        newlyCheckedIn: false,
        checkInDate: "",
        totalCheckInDays: 0,
        consecutiveCheckInDays: 0,
        displayName: "",
        fortuneType: "neutral",
        fortuneName: "",
        fortuneColor: "",
        fortuneDescription: "",
        recommendedItems: [],
        avoidItems: [],
        adviceIndex: 0,
        fortuneScore: 88,
        focusScore: 90,
        practiceScore: 85,
        examScore: 82,
      },
      carouselImgList: [
        {
          url: homeImage,
        },
      ],
      srcHight: "440px",
      remoteJudgeList: [
        {
          url: "http://acm.hdu.edu.cn",
          name: "HDU",
          logo: hduLogo,
          status: true,
        },
        {
          url: "http://poj.org",
          name: "POJ",
          logo: pojLogo,
          status: true,
        },
        {
          url: "https://codeforces.com",
          name: "Codeforces",
          logo: codeforcesLogo,
          status: true,
        },
        {
          url: "https://codeforces.com/gyms",
          name: "GYM",
          logo: gymLogo,
          status: true,
        },
        {
          url: "https://atcoder.jp",
          name: "AtCoder",
          logo: atcoderLogo,
          status: true,
        },
        {
          url: "https://www.spoj.com",
          name: "SPOJ",
          logo: spojLogo,
          status: true,
        },
        {
          url: "https://loj.ac/",
          name: "LibreOJ",
          logo: libreLogo,
          status: true,
        },
      ],
    };
  },
  mounted() {
    let screenWidth = window.screen.width;
    if (screenWidth < 768) {
      this.srcHight = "200px";
    } else {
      this.srcHight = "440px";
    }
    this.CONTEST_STATUS_REVERSE = Object.assign({}, CONTEST_STATUS_REVERSE);
    this.CONTEST_TYPE_REVERSE = Object.assign({}, CONTEST_TYPE_REVERSE);
    this.getHomeCarousel();
    this.getRecentContests();
    this.getRecent7ACRank();
    this.getRecentUpdatedProblemList();
    this.recent7ACRankTimer = window.setInterval(() => {
      this.getRecent7ACRank(false);
    }, RECENT_AC_RANK_REFRESH_INTERVAL);
    if (this.isAuthenticated) {
      this.getDailyCheckInStatus();
    }
    this.dailyCheckInTimer = window.setInterval(() => {
      if (
        this.isAuthenticated &&
        this.checkInStatus.checkInDate &&
        this.checkInStatus.checkInDate !== this.getChinaDateString()
      ) {
        this.getDailyCheckInStatus();
      }
    }, 30000);
  },
  beforeUnmount() {
    if (this.dailyCheckInTimer) {
      window.clearInterval(this.dailyCheckInTimer);
    }
    if (this.recent7ACRankTimer) {
      window.clearInterval(this.recent7ACRankTimer);
    }
  },
  methods: {
    getHomeCarousel() {
      api.getHomeCarousel().then((res) => {
        if (res.data.data != null && res.data.data.length > 0) {
          this.carouselImgList = res.data.data;
        }
      });
    },

    getRecentContests() {
      this.loading.recentContests = true;
      api.getRecentContests().then(
        (res) => {
          this.contests = res.data.data;
          this.loading.recentContests = false;
        },
        (err) => {
          this.loading.recentContests = false;
        }
      );
    },
    getRecentUpdatedProblemList() {
      this.loading.recentUpdatedProblemsLoading = true;
      api.getRecentUpdatedProblemList().then(
        (res) => {
          this.recentUpdatedProblems = res.data.data;
          this.loading.recentUpdatedProblemsLoading = false;
        },
        (err) => {
          this.loading.recentUpdatedProblemsLoading = false;
        }
      );
    },
    getRecent7ACRank(showLoading = true) {
      if (this.loading.recent7ACRankRefreshing) {
        return;
      }
      this.loading.recent7ACRankRefreshing = true;
      if (showLoading) {
        this.loading.recent7ACRankLoading = true;
      }
      api.getRecent7ACRank().then(
        (res) => {
          this.recentUserACRecord = res.data.data;
          this.loading.recent7ACRankLoading = false;
          this.loading.recent7ACRankRefreshing = false;
        },
        (err) => {
          this.loading.recent7ACRankLoading = false;
          this.loading.recent7ACRankRefreshing = false;
        }
      );
    },
    getDailyCheckInStatus() {
      this.loading.dailyCheckIn = true;
      api.getDailyCheckInStatus().then(
        (res) => {
          this.checkInStatus = {
            ...this.checkInStatus,
            ...res.data.data,
          };
          this.loading.dailyCheckIn = false;
        },
        () => {
          this.loading.dailyCheckIn = false;
        }
      );
    },
    handleDailyCheckIn() {
      if (!this.isAuthenticated) {
        myMessage.warning(this.$t("m.Please_login_first"));
        this.$store.dispatch("changeModalStatus", {
          mode: "Login",
          visible: true,
        });
        return;
      }
      if (this.checkInStatus.checkedIn) {
        return;
      }

      this.loading.dailyCheckInSubmitting = true;
      api.dailyCheckIn().then(
        (res) => {
          this.checkInStatus = {
            ...this.checkInStatus,
            ...res.data.data,
          };
          this.loading.dailyCheckInSubmitting = false;
          myMessage.success(this.$t("m.Check_In_Success"));
        },
        () => {
          this.loading.dailyCheckInSubmitting = false;
        }
      );
    },
    getChinaDateString() {
      const dateParts = new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Shanghai",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).formatToParts(new Date());
      const parts = {};
      dateParts.forEach((part) => {
        parts[part.type] = part.value;
      });
      return `${parts.year}-${parts.month}-${parts.day}`;
    },
    goContest(cid) {
      if (!this.isAuthenticated) {
        myMessage.warning(this.$t("m.Please_login_first"));
        this.$store.dispatch("changeModalStatus", { visible: true });
      } else {
        this.$router.push({
          name: "ContestDetails",
          params: { contestID: cid },
        });
      }
    },
    goContestList(type) {
      this.$router.push({
        name: "ContestList",
        query: {
          type,
        },
      });
    },
    goProblem(event) {
      this.$router.push({
        name: "ProblemDetails",
        params: {
          problemID: event.row.problemId,
        },
      });
    },
    goUserHome(username, uid) {
      this.$router.push({
        path: "/user-home",
        query: { uid, username },
      });
    },
    getDuration(startTime, endTime) {
      return time.formatSpecificDuration(startTime, endTime);
    },
    getRankTagClass(rowIndex) {
      return "rank-tag no" + (rowIndex + 1);
    },
  },
  computed: {
    ...mapState(["websiteConfig"]),
    ...mapGetters(["isAuthenticated"]),
    fortuneLabel() {
      if (this.checkInStatus.fortuneName) {
        return this.checkInStatus.fortuneName;
      }
      const key = FORTUNE_LABEL_KEYS[this.checkInStatus.fortuneType];
      return key
        ? this.$t(key)
        : this.$t("m.Fortune_Neutral");
    },
    fortuneColorStyle() {
      return this.checkInStatus.fortuneColor
        ? { color: this.checkInStatus.fortuneColor }
        : {};
    },
    fortuneToneClass() {
      if (
        ["great_luck", "medium_luck", "small_luck"].includes(
          this.checkInStatus.fortuneType
        )
      ) {
        return "fortune-tone-lucky";
      }
      if (this.checkInStatus.fortuneType === "neutral") {
        return "fortune-tone-neutral";
      }
      return "fortune-tone-unlucky";
    },
  },
  watch: {
    isAuthenticated(isAuthenticated) {
      if (isAuthenticated) {
        this.getDailyCheckInStatus();
      } else {
        this.checkInStatus = {
          checkedIn: false,
          newlyCheckedIn: false,
          checkInDate: "",
          totalCheckInDays: 0,
          consecutiveCheckInDays: 0,
          displayName: "",
          fortuneType: "neutral",
          fortuneName: "",
          fortuneColor: "",
          fortuneDescription: "",
          recommendedItems: [],
          avoidItems: [],
          adviceIndex: 0,
          fortuneScore: 88,
          focusScore: 90,
          practiceScore: 85,
          examScore: 82,
        };
      }
    },
  },
};
</script>
<style>
.contest-card-running {
  --contest-card-border-color: rgb(25, 190, 107);
}
.contest-card-schedule {
  --contest-card-border-color: #f90;
}
</style>
<style scoped>
@font-face {
  font-family: 'hoj-element-icons';
  src: url('../../assets/fonts/element-icons.woff') format('woff');
  font-style: normal;
  font-weight: 400;
  font-display: block;
}
:deep(.el-card__header) {
  padding: 0.6rem 1.25rem !important;
}
.card-top {
  margin-top: 20px;
}
.weekly-rank-card > :deep(.el-card__body) {
  padding-bottom: 13px;
}
.daily-check-in-card > :deep(.el-card__body) {
  padding: 24px 28px;
}
.daily-check-in-card > :deep(.el-card__header) {
  padding-bottom: 0 !important;
  padding-top: 0 !important;
}
.daily-check-in-title-icon {
  margin-right: 8px;
}
.daily-check-in-content {
  min-height: 332px;
}
.daily-check-in-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  width: 100%;
}
.daily-check-in-header :deep(.el-button) {
  border-radius: 5px;
  font-weight: 600;
  min-width: 112px;
}
.daily-check-in-header :deep(.el-button i) {
  margin-right: 6px;
}
.daily-check-in-header :deep(.el-button--success.is-disabled) {
  background-color: #18bc6b;
  border-color: #18bc6b;
  color: #fff;
  opacity: 1;
}
.daily-fortune-panel {
  border: 1px solid #dfe4ea;
  border-radius: 9px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  min-height: 332px;
  padding: 24px 34px 20px;
}
.daily-fortune-owner {
  align-items: baseline;
  display: flex;
  font-size: 18px;
  font-weight: 600;
  gap: 5px;
  justify-content: center;
  line-height: 26px;
  text-align: center;
}
.daily-fortune-owner > strong {
  font-weight: 700;
  overflow-wrap: anywhere;
}
.daily-fortune-level {
  font-size: 42px;
  font-weight: 700;
  letter-spacing: 5px;
  line-height: 56px;
  margin-top: 8px;
  text-align: center;
}
.daily-fortune-summary {
  color: #8a8f99;
  font-size: 14px;
  line-height: 20px;
  margin-top: 2px;
  text-align: center;
}
.fortune-tone-lucky {
  color: #f5222d;
}
.fortune-tone-neutral {
  color: #52b947;
}
.fortune-tone-unlucky {
  color: #202124;
}
.daily-fortune-advice-grid {
  display: grid;
  gap: 26px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 18px;
}
.daily-fortune-advice-column {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}
.daily-fortune-advice-item {
  min-height: 52px;
  text-align: center;
}
.daily-fortune-advice-title {
  font-size: 16px;
  line-height: 22px;
  overflow-wrap: anywhere;
}
.daily-fortune-advice-title.recommended {
  color: #f5222d;
}
.daily-fortune-advice-title.avoid {
  color: #202124;
}
.daily-fortune-advice-title > strong {
  font-weight: 700;
}
.daily-fortune-advice-description {
  color: #8a8f99;
  font-size: 13px;
  line-height: 20px;
  margin-top: 6px;
  overflow-wrap: anywhere;
}
.daily-check-in-streak {
  color: #8a8f99;
  font-size: 15px;
  line-height: 22px;
  margin-top: auto;
  padding-top: 18px;
  text-align: center;
}
.daily-check-in-streak > strong {
  color: #5e6470;
  font-size: 17px;
}
.daily-check-in-login-prompt {
  align-items: center;
  border: 1px solid #dfe4ea;
  border-radius: 9px;
  color: #8a8f99;
  display: flex;
  flex-direction: column;
  font-size: 16px;
  gap: 12px;
  justify-content: center;
  min-height: 332px;
}
.daily-check-in-login-prompt > i {
  color: #409eff;
  font-size: 34px;
}
.daily-check-in-pending {
  font-size: 20px;
}
.weekly-rank-title-icon::before {
  content: '\e7a8' !important;
  font-family: 'hoj-element-icons' !important;
  font-style: normal;
  font-variant: normal;
  font-weight: 400 !important;
  line-height: 1;
  text-rendering: auto;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
:deep(.weekly-rank-table .vxe-table--header-wrapper) {
  background: #fff !important;
}
:deep(.weekly-rank-table .vxe-header--column) {
  color: #000;
}
:deep(.weekly-rank-table .vxe-body--row),
:deep(.weekly-rank-table .vxe-body--column) {
  height: 45px;
}
.latest-problem-table-header {
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e8eaec;
  color: #000;
  display: grid;
  font-weight: 700;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  min-height: 40px;
  text-align: center;
}
:deep(.latest-problem-table .vxe-table--body-wrapper) {
  border-top: 0;
}
:deep(.latest-problem-table .vxe-body--column) {
  font-weight: 400;
}
.home-contest {
  text-align: left;
  font-size: 21px;
  font-weight: 500;
  line-height: 30px;
}
.oj-logo {
  box-sizing: border-box;
  border: 1px solid rgba(0, 0, 0, 0.15);
  border-radius: 4px;
  margin-bottom: 1rem;
  padding: 0.5rem 1rem;
  background: rgb(255, 255, 255);
  min-height: 47px;
}
:deep(.oj-logo .el-image__inner) {
  height: auto;
}
:deep(.remote-judge-grid > .el-col) {
  height: 68px;
}
.oj-normal {
  border-color: #409eff;
}
.oj-error {
  border-color: #e65c47;
}

.el-carousel__item h3 {
  color: #475669;
  font-size: 14px;
  opacity: 0.75;
  line-height: 200px;
  margin: 0;
}

.contest-card-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-bottom: 20px;
}
.recent-contests-card > :deep(.el-card__body) {
  padding-left: 16px;
  padding-right: 16px;
}
.contest-card {
  border: 1px solid var(--contest-card-border-color) !important;
  border-radius: 4px;
  margin: 0;
  overflow: hidden;
}
.contest-header {
  line-height: 26px;
}
:deep(.contest-card .el-button--small) {
  height: 28px;
  padding: 7px 16px;
}
:deep(.contest-card .el-tag) {
  height: 28px;
  line-height: 26px;
}
:deep(.contest-card .el-button i),
:deep(.contest-card .el-tag i) {
  margin-right: 4px;
}
.contest-title {
  font-size: 1.15rem;
  font-weight: 600;
}
.contest-type-auth {
  text-align: center;
  margin-top: -10px;
  margin-bottom: 5px;
}
ul,
li {
  padding: 0;
  margin: 0;
  list-style: none;
}
.contest-info {
  text-align: center;
}
.contest-info li {
  display: inline-block;
  padding-right: 10px;
}

:deep(.contest-card-running .el-card__header) {
  border-color: rgb(25, 190, 107);
  background-color: rgba(94, 185, 94, 0.15);
}
.contest-card-running .contest-title {
  color: #5eb95e;
}

:deep(.contest-card-schedule .el-card__header) {
  border-color: #f90;
  background-color: rgba(243, 123, 29, 0.15);
}

.contest-card-schedule .contest-title {
  color: #f37b1d;
}

.content-center {
  text-align: center;
}
.clearfix:before,
.clearfix:after {
  display: table;
  content: "";
}
.clearfix:after {
  clear: both;
}
.welcome-title {
  font-weight: 600;
  font-size: 25px;
  font-family: "Raleway";
}
.contest-status {
  float: right;
}
.img-carousel {
  height: 490px;
}

@media screen and (max-width: 768px) {
  .daily-check-in-card > :deep(.el-card__body) {
    padding: 18px 14px;
  }
  .daily-check-in-header {
    gap: 12px;
  }
  .daily-fortune-panel {
    min-height: 0;
    padding: 20px 18px;
  }
  .daily-fortune-level {
    font-size: 34px;
    line-height: 48px;
  }
  .daily-fortune-advice-grid {
    gap: 20px;
    grid-template-columns: 1fr;
  }
  .daily-check-in-login-prompt {
    min-height: 250px;
  }
  .contest-status {
    text-align: center;
    float: none;
    margin-top: 5px;
  }
  .contest-header {
    text-align: center;
  }
  .img-carousel {
    height: 220px;
    overflow: hidden;
  }
  .phone-margin {
    margin-top: 20px;
  }
}
.title .el-link {
  font-size: 21px;
  font-weight: 500;
  color: #444;
}
.clearfix h2 {
  color: #409eff;
}
.el-link.el-link--default:hover {
  color: #409eff;
  transition: all 0.28s ease;
}
.contest .content-info {
  padding: 0 70px 40px 70px;
}
.contest .contest-description {
  margin-top: 25px;
}
span.rank-tag.no1 {
  line-height: 24px;
  background: #bf2c24;
}

span.rank-tag.no2 {
  line-height: 24px;
  background: #e67225;
}

span.rank-tag.no3 {
  line-height: 24px;
  background: #e6bf25;
}

span.rank-tag {
  font: 16px/22px FZZCYSK;
  min-width: 14px;
  height: 22px;
  padding: 0 4px;
  text-align: center;
  color: #fff;
  background: #000;
  background: rgba(0, 0, 0, 0.6);
}
.user-avatar {
  margin-right: 5px !important;
  vertical-align: middle;
}
.cite {
  display: block;
  width: 14px;
  height: 0;
  margin: 0 auto;
  margin-top: -3px;
  border-right: 11px solid transparent;
  border-bottom: 0 none;
  border-left: 11px solid transparent;
}
.cite.no0 {
  border-top: 5px solid #bf2c24;
}
.cite.no1 {
  border-top: 5px solid #e67225;
}
.cite.no2 {
  border-top: 5px solid #e6bf25;
}

@media screen and (min-width: 1050px) {
  :deep(.vxe-table--body-wrapper) {
    overflow-x: hidden !important;
  }
}
:deep(.img-carousel .el-image) {
  height: 100%;
  width: 100%;
}
</style>
