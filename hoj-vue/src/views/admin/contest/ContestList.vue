<template>
  <div class="contest-list-page">
    <el-card class="contest-list-card">
      <template #header>
        <div>
          <span class="panel-title home-title">{{ $t('m.Contest_List') }}</span>
          <div class="filter-row">
            <span>
              <vxe-input
                v-model="keyword"
                :placeholder="$t('m.Enter_keyword')"
                type="search"
                size="medium"
                @search-click="filterByKeyword"
                @keyup.enter="filterByKeyword"
              ></vxe-input>
            </span>
          </div>
        </div>
      </template>
      <vxe-table
        class="contest-list-table"
        :loading="loading"
        ref="xTable"
        :data="contestList"
        auto-resize
        stripe
        align="center"
      >
        <vxe-table-column field="id" width="80" title="ID"> </vxe-table-column>
        <vxe-table-column
          field="title"
          min-width="150"
          :title="$t('m.Title')"
          show-overflow
        >
        </vxe-table-column>
        <vxe-table-column :title="$t('m.Type')" width="100">
          <template v-slot="{ row }">
            <el-tag type="gray">{{ $filters.parseContestType(row.type) }}</el-tag>
          </template>
        </vxe-table-column>
        <vxe-table-column :title="$t('m.Auth')" width="100">
          <template v-slot="{ row }">
            <el-tooltip
              :content="$t('m.' + CONTEST_TYPE_REVERSE[row.auth].tips)"
              placement="top"
              effect="light"
            >
              <el-tag
                :type="CONTEST_TYPE_REVERSE[row.auth].color"
                effect="plain"
              >
                {{ CONTEST_TYPE_REVERSE[row.auth].name }}
              </el-tag>
            </el-tooltip>
          </template>
        </vxe-table-column>
        <vxe-table-column :title="$t('m.Status')" width="100">
          <template v-slot="{ row }">
            <el-tag
              effect="dark"
              :color="CONTEST_STATUS_REVERSE[row.status].color"
              size="default"
            >
              {{ CONTEST_STATUS_REVERSE[row.status].name }}
            </el-tag>
          </template>
        </vxe-table-column>
        <vxe-table-column :title="$t('m.Visible')" min-width="80">
          <template v-slot="{ row }">
            <el-switch
              v-model="row.visible"
              :disabled="!isSuperAdmin && userInfo.uid != row.uid"
              @change="changeContestVisible(row)"
            >
            </el-switch>
          </template>
        </vxe-table-column>
        <vxe-table-column min-width="210" :title="$t('m.Info')">
          <template v-slot="{ row }">
            <p>Start Time: {{ $filters.localtime(row.startTime) }}</p>
            <p>End Time: {{ $filters.localtime(row.endTime) }}</p>
            <p>Created Time: {{ $filters.localtime(row.gmtCreate) }}</p>
            <p>Creator: {{ row.author }}</p>
          </template>
        </vxe-table-column>
        <vxe-table-column
          min-width="150"
          :title="$t('m.Option')"
          fixed="right"
        >
          <template v-slot="{ row }">
            <template v-if="isSuperAdmin || userInfo.uid == row.uid">
              <div style="margin-bottom:10px">
                <el-tooltip
                  effect="dark"
                  :content="$t('m.Edit')"
                  placement="top"
                >
                  <el-button
                    :icon="legacyElementIcons['el-icon-edit']"
                    size="small"
                    @click="goEdit(row.id)"
                    type="primary"
                  >
                  </el-button>
                </el-tooltip>
                <el-tooltip
                  effect="dark"
                  :content="$t('m.View_Contest_Problem_List')"
                  placement="top"
                >
                  <el-button
                    :icon="legacyElementIcons['el-icon-tickets']"
                    size="small"
                    @click="goContestProblemList(row.id)"
                    type="success"
                  >
                  </el-button>
                </el-tooltip>
              </div>
              <div style="margin-bottom:10px">
                <el-tooltip
                  effect="dark"
                  :content="$t('m.View_Contest_Announcement_List')"
                  placement="top"
                >
                  <el-button
                    :icon="legacyElementIcons['el-icon-info']"
                    size="small"
                    @click="goContestAnnouncement(row.id)"
                    type="info"
                  >
                  </el-button>
                </el-tooltip>

                <el-tooltip
                  effect="dark"
                  :content="$t('m.Download_Contest_AC_Submission')"
                  placement="top"
                >
                  <el-button
                    :icon="legacyElementIcons['el-icon-download']"
                    size="small"
                    @click="openDownloadOptions(row.id)"
                    type="warning"
                  >
                  </el-button>
                </el-tooltip>
              </div>
            </template>
            <el-tooltip
              effect="dark"
              :content="$t('m.Delete')"
              placement="top"
              v-if="isSuperAdmin"
            >
              <el-button
                :icon="legacyElementIcons['el-icon-delete']"
                size="small"
                @click="deleteContest(row.id)"
                type="danger"
              >
              </el-button>
            </el-tooltip>
          </template>
        </vxe-table-column>
      </vxe-table>
      <div class="panel-options contest-list-pagination">
        <el-pagination
          class="page"
          layout="prev, pager, next"
          @current-change="currentChange"
          :page-size="pageSize"
          v-model:current-page="currentPage"
          :total="total"
        >
        </el-pagination>
      </div>
    </el-card>
    <el-dialog
      class="contest-download-dialog"
      :title="$t('m.Download_Contest_AC_Submission')"
      width="320px"
      v-model="downloadDialogVisible"
    >
      <el-switch
        v-model="excludeAdmin"
        :active-text="$t('m.Exclude_admin_submissions')"
      ></el-switch>
      <el-radio-group v-model="splitType" style="margin-top:10px">
        <el-radio value="user">{{ $t('m.SplitType_User') }}</el-radio>
        <el-radio value="problem">{{ $t('m.SplitType_Problem') }}</el-radio>
      </el-radio-group>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="downloadSubmissions">{{
            $t('m.OK')
          }}</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import api from '@/common/api';
import utils from '@/common/utils';
import {
  CONTEST_STATUS_REVERSE,
  CONTEST_TYPE_REVERSE,
} from '@/common/constants';
import { mapGetters } from 'vuex';
import myMessage from '@/common/message';
export default {
  name: 'ContestList',
  data() {
    return {
      pageSize: 10,
      total: 0,
      contestList: [],
      keyword: '',
      loading: false,
      excludeAdmin: true,
      splitType: 'user',
      currentPage: 1,
      currentId: 1,
      downloadDialogVisible: false,
      CONTEST_TYPE_REVERSE: {},
    };
  },
  mounted() {
    this.CONTEST_TYPE_REVERSE = Object.assign({}, CONTEST_TYPE_REVERSE);
    this.CONTEST_STATUS_REVERSE = Object.assign({}, CONTEST_STATUS_REVERSE);
    this.getContestList(this.currentPage);
  },
  watch: {
    $route() {
      let refresh = this.$route.query.refresh == 'true' ? true : false;
      if (refresh) {
        this.getContestList(1);
      }
    },
  },
  computed: {
    ...mapGetters(['isSuperAdmin', 'userInfo']),
  },
  methods: {
    // 切换页码回调
    currentChange(page) {
      this.currentPage = page;
      this.getContestList(page);
    },
    getContestList(page) {
      this.currentPage = page;
      this.loading = true;
      api.admin_getContestList(page, this.pageSize, this.keyword).then(
        (res) => {
          this.loading = false;
          this.total = res.data.data.total;
          this.contestList = res.data.data.records;
        },
        (res) => {
          this.loading = false;
        }
      );
    },
    openDownloadOptions(contestId) {
      this.downloadDialogVisible = true;
      this.currentId = contestId;
    },
    downloadSubmissions() {
      let url = `/api/file/download-contest-ac-submission?cid=${this.currentId}&excludeAdmin=${this.excludeAdmin}&splitType=${this.splitType}`;
      utils.downloadFile(url);
      this.downloadDialogVisible = false;
    },
    goEdit(contestId) {
      this.$router.push({ name: 'admin-edit-contest', params: { contestId } });
    },
    goContestAnnouncement(contestId) {
      this.$router.push({
        name: 'admin-contest-announcement',
        params: { contestId },
      });
    },
    goContestProblemList(contestId) {
      this.$router.push({
        name: 'admin-contest-problem-list',
        params: { contestId },
      });
    },
    deleteContest(contestId) {
      this.$confirm(this.$t('m.Delete_Contest_Tips'), 'Tips', {
        confirmButtonText: this.$t('m.OK'),
        cancelButtonText: this.$t('m.Cancel'),
        type: 'warning',
      }).then(() => {
        api.admin_deleteContest(contestId).then((res) => {
          myMessage.success(this.$t('m.Delete_successfully'));
          this.currentChange(1);
        });
      });
    },
    changeContestVisible(row) {
      api
        .admin_changeContestVisible(row.id, row.visible, row.uid)
        .then(() => {
          myMessage.success(this.$t('m.Update_Successfully'));
        })
        .catch(() => {
          row.visible = !row.visible;
        });
    },
    filterByKeyword() {
      this.currentChange(1);
    },
  },
};
</script>
<style scoped>
.filter-row {
  margin-top: 10px;
}
.contest-list-table {
  --vxe-ui-table-header-background-color: #f8f8f9;
}
.contest-list-table :deep(.el-button--small) {
  width: 44px;
  height: 29px;
  min-height: 29px;
  padding: 7px 15px;
}
.contest-list-table :deep(.el-button + .el-button) {
  margin-left: 10px;
}
.contest-list-table :deep(.el-tag) {
  height: 32px;
  line-height: 30px;
}
.contest-list-table :deep(.el-switch) {
  height: 20px;
  line-height: 20px;
}
.contest-list-pagination {
  min-height: 32px;
}
.contest-list-pagination :deep(.el-pagination.page) {
  display: block;
  width: 100%;
  height: 32px;
  padding: 2px 5px;
  text-align: center;
  box-sizing: border-box;
  --el-pagination-button-width: 35.5px;
  --el-pagination-button-height: 28px;
}
.contest-list-pagination :deep(.btn-prev),
.contest-list-pagination :deep(.btn-next),
.contest-list-pagination :deep(.el-pager) {
  display: inline-block;
  vertical-align: top;
}
.contest-list-pagination :deep(.el-pager) {
  width: auto;
}
.contest-list-pagination :deep(.el-pager li) {
  display: inline-block;
  vertical-align: top;
  width: 36px;
  min-width: 36px;
  height: 28px;
  line-height: 28px;
}
:global(.contest-download-dialog) {
  padding: 0;
}
:global(.contest-download-dialog .el-dialog__header) {
  box-sizing: border-box;
  height: 54px;
  padding: 20px 20px 10px;
}
:global(.contest-download-dialog .el-dialog__body) {
  box-sizing: border-box;
  padding: 31px 20px 30px;
}
:global(.contest-download-dialog .el-dialog__footer) {
  box-sizing: border-box;
  padding: 10px 20px 20px;
}
:global(.contest-download-dialog .el-switch) {
  height: 20px;
  line-height: 20px;
}
:global(.contest-download-dialog .el-radio-group) {
  display: block;
  line-height: 16.375px;
}
:global(.contest-download-dialog .el-radio) {
  display: inline-block;
  vertical-align: top;
  width: 150px;
  height: 16.375px;
  line-height: 16.375px;
}
:global(.contest-download-dialog .el-dialog__footer .el-button) {
  height: 40px;
  min-height: 40px;
  padding: 12px 20px;
}
@media screen and (max-width: 768px) {
  .filter-row span {
    margin-right: 5px;
  }
  .filter-row span div {
    width: 80% !important;
  }
}
@media screen and (min-width: 768px) {
  .filter-row span {
    margin-right: 20px;
  }
}
.el-tag--dark {
  border-color: #fff;
}
</style>
