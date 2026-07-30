<template>
  <el-row class="training-list-page">
    <el-card>
      <section>
        <span class="find-training">{{ $t('m.Search_Training') }}</span>
        <vxe-input
          v-model="query.keyword"
          :placeholder="$t('m.Enter_keyword')"
          type="search"
          size="medium"
          style="width:230px"
          @keyup.enter="filterByKeyword"
          @search-click="filterByKeyword"
        ></vxe-input>
      </section>
      <section>
        <b class="training-category">{{ $t('m.Training_Auth') }}</b>
        <div>
          <el-tag
            size="default"
            class="category-item"
            :effect="query.auth ? 'plain' : 'dark'"
            role="button"
            tabindex="0"
            :aria-pressed="!query.auth"
            @click="filterByAuthType(null)"
            @keydown.enter.prevent="filterByAuthType(null)"
            @keydown.space.prevent="filterByAuthType(null)"
            >{{ $t('m.All') }}</el-tag
          >
          <el-tag
            size="default"
            class="category-item"
            v-for="(key, index) in TRAINING_TYPE"
            :type="key.color"
            :effect="query.auth == key.name ? 'dark' : 'plain'"
            :key="index"
            role="button"
            tabindex="0"
            :aria-pressed="query.auth == key.name"
            @click="filterByAuthType(key.name)"
            @keydown.enter.prevent="filterByAuthType(key.name)"
            @keydown.space.prevent="filterByAuthType(key.name)"
            >{{ $t('m.Training_' + key.name) }}</el-tag
          >
        </div>
      </section>
      <section>
        <b class="training-category">{{ $t('m.Training_Category') }}</b>
        <div>
          <el-tag
            size="default"
            class="category-item"
            :style="getCategoryBlockColor(null)"
            role="button"
            tabindex="0"
            :aria-pressed="!query.categoryId"
            @click="filterByCategory(null)"
            @keydown.enter.prevent="filterByCategory(null)"
            @keydown.space.prevent="filterByCategory(null)"
            >{{ $t('m.All') }}</el-tag
          >
          <el-tag
            size="default"
            class="category-item"
            v-for="(category, index) in categoryList"
            :style="getCategoryBlockColor(category)"
            :key="index"
            role="button"
            tabindex="0"
            :aria-pressed="query.categoryId == category.id"
            @click="filterByCategory(category.id)"
            @keydown.enter.prevent="filterByCategory(category.id)"
            @keydown.space.prevent="filterByCategory(category.id)"
            >{{ category.name }}</el-tag
          >
        </div>
      </section>
    </el-card>

    <el-card
      class="training-table-card"
      style="margin-top:2em"
    >
      <div
        class="training-table-header"
        :class="{ 'training-table-header-mobile': mobileView }"
        role="row"
      >
        <span
          v-if="!mobileView"
          role="columnheader"
        >{{ $t('m.Number') }}</span>
        <span role="columnheader">{{ $t('m.Title') }}</span>
        <span role="columnheader">{{ $t('m.Auth') }}</span>
        <span
          v-if="!mobileView"
          role="columnheader"
        >{{ $t('m.Category') }}</span>
        <span
          v-if="isAuthenticated && !mobileView"
          role="columnheader"
        >{{ $t('m.Progress') }}</span>
        <span
          v-if="!mobileView"
          role="columnheader"
        >{{ $t('m.Problem_Number') }}</span>
        <span
          v-if="!mobileView"
          role="columnheader"
        >{{ $t('m.Author') }}</span>
        <span
          v-if="!mobileView"
          role="columnheader"
        >{{ $t('m.Recent_Update') }}</span>
      </div>
      <vxe-table
        class="training-data-table"
        border="inner"
        stripe
        ref="trainingList"
        auto-resize
        :data="trainingList"
        :loading="loading"
        :show-header="false"
      >
        <vxe-table-column
          field="rank"
          :title="$t('m.Number')"
          width="9%"
          min-width="60"
          show-overflow
          :visible="!mobileView"
        >
        </vxe-table-column>
        <vxe-table-column
          field="title"
          :title="$t('m.Title')"
          :width="mobileView ? '70%' : '18.3%'"
          min-width="200"
          align="center"
        >
          <template v-slot="{ row }"
            ><el-link type="primary" @click="toTraining(row.id)">{{
              row.title
            }}</el-link>
          </template>
        </vxe-table-column>

        <vxe-table-column
          field="auth"
          :title="$t('m.Auth')"
          :width="mobileView ? '30%' : '11.6%'"
          :min-width="mobileView ? 86 : 100"
          align="center"
        >
          <template v-slot="{ row }">
            <el-tag
              class="training-auth-tag"
              :type="TRAINING_TYPE[row.auth]['color']"
              size="large"
              effect="dark"
            >
              {{ $t('m.Training_' + row.auth) }}
            </el-tag>
          </template>
        </vxe-table-column>
        <vxe-table-column
          field="categoryName"
          :title="$t('m.Category')"
          width="13.6%"
          min-width="130"
          align="center"
          :visible="!mobileView"
        >
          <template v-slot="{ row }">
            <el-tag
              size="default"
              class="category-item"
              :style="
                'background-color: #fff;color: ' +
                  row.categoryColor +
                  ';border-color: ' +
                  row.categoryColor +
                  ';'
              "
              :key="index"
              >{{ row.categoryName }}</el-tag
            >
          </template>
        </vxe-table-column>

        <vxe-table-column 
          field="acCount" 
          :title="$t('m.Progress')" 
          width="13%"
          min-width="120"
          align="center"
          :visible="isAuthenticated && !mobileView">
          <template v-slot="{ row }">
            <span>
              <el-tooltip
                effect="dark"
                :content="row.acCount + '/' + row.problemCount"
                placement="top"
              >
                <el-progress
                  :text-inside="true"
                  :stroke-width="20"
                  :percentage="getPassingRate(row.acCount, row.problemCount)"
                ></el-progress>
              </el-tooltip>
            </span>
          </template>
        </vxe-table-column>

        <vxe-table-column
          field="problemCount"
          :title="$t('m.Problem_Number')"
          width="9.6%"
          min-width="70"
          align="center"
          :visible="!mobileView"
        >
        </vxe-table-column>
        <vxe-table-column
          field="author"
          :title="$t('m.Author')"
          width="13.6%"
          min-width="130"
          align="center"
          show-overflow
          :visible="!mobileView"
        >
          <template v-slot="{ row }"
            ><el-link type="info" @click="goUserHome(row.author)">{{
              row.author
            }}</el-link>
          </template>
        </vxe-table-column>
        <vxe-table-column
          field="gmtModified"
          :title="$t('m.Recent_Update')"
          width="11.3%"
          min-width="96"
          align="center"
          show-overflow
          :visible="!mobileView"
        >
          <template v-slot="{ row }">
            <span>
                <el-tooltip
                  :content="$filters.localtime(row.gmtModified)"
                  placement="top"
                >
                  <span>{{ $filters.fromNow(row.gmtModified) }}</span>
                </el-tooltip>
              </span>
          </template>
        </vxe-table-column>
      </vxe-table>
    </el-card>
    <Pagination
      :total="total"
      :pageSize="limit"
      @on-change="filterByPage"
      v-model:current="currentPage"
    ></Pagination>
  </el-row>
</template>

<script>
import { defineAsyncComponent } from 'vue';
import api from '@/common/api';
import utils from '@/common/utils';
import { TRAINING_TYPE } from '@/common/constants';
import myMessage from '@/common/message';
import { mapGetters } from 'vuex';
const Pagination = defineAsyncComponent(() => import('@/components/oj/common/Pagination'));
export default {
  name: 'TrainingList',
  components: {
    Pagination,
  },
  data() {
    return {
      query: {
        keyword: '',
        categoryId: null,
        auth: null,
      },
      total: 0,
      currentPage: 1,
      limit: 15,
      categoryList: [],
      trainingList: [],
      TRAINING_TYPE: {},
      loading: false,
      mobileView: false,
    };
  },
  created() {
    let route = this.$route.query;
    this.currentPage = parseInt(route.currentPage) || 1;
    this.TRAINING_TYPE = Object.assign({}, TRAINING_TYPE);
    if(!this.isAuthenticated){
      setTimeout(() => {
        // 将指定列设置为隐藏状态
        this.$refs.trainingList.getColumnByField('acCount').visible = false;
        this.$refs.trainingList.refreshColumn();
      }, 200);
    }

    this.getTrainingCategoryList();
  },
  mounted() {
    this.updateMobileView();
    window.addEventListener('resize', this.updateMobileView);
    this.init();
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.updateMobileView);
  },
  methods: {
    updateMobileView() {
      this.mobileView = window.innerWidth < 768;
    },
    init() {
      let route = this.$route.query;
      this.query.keyword = route.keyword || '';
      this.query.categoryId = route.categoryId || null;
      this.query.auth = route.auth || null;
      this.currentPage = parseInt(route.currentPage) || 1;
      this.getTrainingList();
    },

    filterByPage(page) {
      this.currentPage = page;
      this.filterByChange();
    },

    filterByCategory(categoryId) {
      this.query.categoryId = categoryId;
      this.currentPage = 1;
      this.filterByChange();
    },

    filterByAuthType(auth) {
      this.query.auth = auth;
      this.currentPage = 1;
      this.filterByChange();
    },

    filterByKeyword() {
      this.currentPage = 1;
      this.filterByChange();
    },

    filterByChange() {
      let query = Object.assign({}, this.query);
      query.currentPage = this.currentPage;
      this.$router.push({
        path: '/training',
        query: utils.filterEmptyValue(query),
      });
    },
    getTrainingList() {
      this.loading = true;
      let query = Object.assign({}, this.query);
      api.getTrainingList(this.currentPage, this.limit, query).then(
        (res) => {
          this.trainingList = res.data.data.records;
          this.total = res.data.data.total;
          this.loading = false;
        },
        (err) => {
          this.loading = false;
        }
      );
    },
    getTrainingCategoryList() {
      api.getTrainingCategoryList().then((res) => {
        this.categoryList = res.data.data;
      });
    },

    toTraining(trainingID) {
      if (!this.isAuthenticated) {
        myMessage.warning(this.$t('m.Please_login_first'));
        this.$store.dispatch('changeModalStatus', { visible: true });
      } else {
        this.$router.push({
          name: 'TrainingDetails',
          params: { trainingID: trainingID },
        });
      }
    },
    goUserHome(username) {
      this.$router.push({
        path: '/user-home',
        query: { username },
      });
    },

    getCategoryBlockColor(category) {
      if (category == null) {
        if (!this.query.categoryId) {
          return 'color: #fff;background-color: #409EFF;background-color: #409EFF';
        } else {
          return 'background-color: #fff;color: #409EFF;border-color: #409EFF';
        }
      }

      if (category.id == this.query.categoryId) {
        return (
          'color: #fff;background-color: ' +
          category.color +
          ';background-color: ' +
          category.color +
          ';'
        );
      } else {
        return (
          'background-color: #fff;color: ' +
          category.color +
          ';border-color: ' +
          category.color +
          ';'
        );
      }
    },
    getPassingRate(ac, total) {
      if (!total) {
        return 0;
      }
      const percentage = ((Number(ac) || 0) / Number(total)) * 100;
      return Math.min(100, Math.max(0, percentage)).toFixed(2);
    },
  },
  computed: {
    ...mapGetters(['isAuthenticated']),
  },
  watch: {
    $route(newVal, oldVal) {
      if (newVal !== oldVal) {
        this.init();
      }
    },
    isAuthenticated(newVal, oldVal){
      setTimeout(() => {
        // 将指定列设置为隐藏状态
        this.$refs.trainingList.getColumnByField('acCount').visible = newVal && !this.mobileView;
        this.$refs.trainingList.refreshColumn();
      }, 200);
      this.init();
    }
  },
};
</script>

<style scoped>
.training-list-page {
  display: block;
  width: 100%;
}
.training-list-page > .el-card {
  width: 100%;
}
.training-list-page > :deep(.el-card__body) {
  font-size: 16px;
}
section {
  display: flex;
  min-height: 3em;
  margin-bottom: 1em;
  align-items: center;
}
.find-training {
  margin-right: 1em;
  white-space: nowrap;
  font-size: 1.7em;
  margin-top: 0;
  font-family: inherit;
  font-weight: bold;
  line-height: 1.2;
  color: inherit;
}
.training-category {
  margin-right: 1.5em;
  font-weight: bolder;
  white-space: nowrap;
  font-size: 16px;
  margin-top: 8px;
}
.category-item {
  margin-right: 1em;
  margin-top: 0.5em;
  font-size: 16px;
}
.category-item:hover {
  cursor: pointer;
}
.training-list-page :deep(.vxe-input .vxe-input--inner) {
  font-size: 14px;
}
.training-list-page :deep(.el-tag) {
  font-size: 14px;
}
.training-list-page section :deep(.el-tag) {
  height: 28px;
  line-height: 26px;
  padding: 0 10px;
}
.training-table-card > :deep(.el-card__body) {
  padding-top: 20px;
}
.training-table-header {
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  color: #303133;
  display: grid;
  font-size: 14px;
  font-weight: 700;
  grid-template-columns: 9% 18.3% 11.6% 13.6% 13% 9.6% 13.6% 11.3%;
  min-height: 42px;
  text-align: center;
}
.training-table-header-mobile {
  grid-template-columns: 70% 30%;
}
.training-data-table {
  font-size: 14px;
}
.training-data-table :deep(.vxe-body--row),
.training-data-table :deep(.vxe-body--column) {
  height: 52px;
}
.training-data-table :deep(.vxe-cell) {
  font-size: 14px;
  line-height: 22px;
}
.training-data-table :deep(.el-link) {
  font-size: 14px;
}
.training-data-table :deep(.training-auth-tag) {
  font-size: 14px;
  height: 32px;
  line-height: 30px;
  padding: 0 10px;
}
.training-data-table :deep(.category-item) {
  font-size: 14px;
  height: 28px;
  line-height: 26px;
  padding: 0 9px;
}
.training-data-table :deep(.vxe-table--body-wrapper) {
  min-height: 0 !important;
}
.training-data-table :deep(.el-progress__text) {
  font-size: 12px !important;
}
.training-data-table :deep(.el-progress-bar__innerText) {
  color: #606266;
}
</style>
