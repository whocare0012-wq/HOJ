<template>
  <div class="discussion-admin-page">
    <el-card class="discussion-admin-card">
      <template #header>
        <div>
          <span class="panel-title home-title">{{
            $t('m.Discussion_Admin')
          }}</span>
          <div class="filter-row">
            <span>
              <el-button
                type="danger"
                :icon="legacyElementIcons['el-icon-delete-solid']"
                @click="deleteDiscussion(null)"
                size="small"
                >{{ $t('m.Delete') }}
              </el-button>
            </span>
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
        class="discussion-table"
        stripe
        auto-resize
        :data="discussionList"
        ref="xTable"
        align="center"
        :loading="discussionLoadingTable"
        :checkbox-config="{ highlight: true, range: true }"
        @checkbox-change="handleSelectionChange"
        @checkbox-all="handlechangeAll"
      >
        <vxe-table-column type="checkbox" width="60"></vxe-table-column>
        <vxe-table-column field="id" title="ID" width="60"></vxe-table-column>
        <vxe-table-column
          field="title"
          :title="$t('m.Title')"
          show-overflow
          min-width="150"
        ></vxe-table-column>
        <vxe-table-column
          field="author"
          :title="$t('m.Author')"
          min-width="150"
          show-overflow
        ></vxe-table-column>
        <vxe-table-column
          field="likeNum"
          :title="$t('m.Likes')"
          min-width="96"
        ></vxe-table-column>
        <vxe-table-column
          field="viewNum"
          :title="$t('m.Views')"
          min-width="96"
        ></vxe-table-column>
        <vxe-table-column
          field="gmtCreate"
          :title="$t('m.Created_Time')"
          min-width="150"
        >
          <template v-slot="{ row }">
            {{ $filters.localtime(row.gmtCreate) }}
          </template>
        </vxe-table-column>
        <vxe-table-column
          field="status"
          :title="$t('m.Status')"
          min-width="100"
        >
          <template v-slot="{ row }">
            <el-select
              v-model="row.status"
              @change="changeDiscussionStatus(row)"
              size="small"
            >
              <el-option :label="$t('m.Normal')" :value="0" :key="0"></el-option
              ><el-option
                :label="$t('m.Disable')"
                :value="1"
                :key="1"
              ></el-option>
            </el-select>
          </template>
        </vxe-table-column>
        <vxe-table-column
          min-width="100"
          field="topPriority"
          :title="$t('m.Top')"
        >
          <template v-slot="{ row }">
            <el-switch
              v-model="row.topPriority"
              active-text=""
              inactive-text=""
              :active-value="true"
              :inactive-value="false"
              @change="handleTopSwitch(row)"
            >
            </el-switch>
          </template>
        </vxe-table-column>
        <vxe-table-column
          :title="$t('m.Option')"
          min-width="130"
          fixed="right"
        >
          <template v-slot="{ row }">
            <el-tooltip effect="dark" :content="$t('m.Delete')" placement="top">
              <el-button
                :icon="legacyElementIcons['el-icon-delete-solid']"
                size="small"
                @click="deleteDiscussion([row.id])"
                type="danger"
              >
              </el-button>
            </el-tooltip>
            <el-tooltip
              effect="dark"
              :content="$t('m.View_Discussion')"
              placement="top"
            >
              <el-button
                :icon="legacyElementIcons['el-icon-search']"
                size="small"
                @click="toDiscussion(row.id, row.gid)"
                type="primary"
              >
              </el-button>
            </el-tooltip>
          </template>
        </vxe-table-column>
      </vxe-table>
      <div class="panel-options discussion-pagination">
        <el-pagination
          class="page"
          layout="prev, pager, next"
          @current-change="discussionCurrentChange"
          v-model:current-page="discussionCurrentPage"
          :page-size="pageSize"
          :total="discussionTotal"
        >
        </el-pagination>
      </div>
    </el-card>
    <el-card class="discussion-admin-card" style="margin-top:20px">
      <template #header>
        <div>
          <span class="panel-title home-title">{{
            $t('m.Discussion_Report')
          }}</span>
        </div>
      </template>
      <vxe-table
        class="discussion-report-table"
        :loading="discussionReportLoadingTable"
        ref="table"
        align="center"
        :data="discussionReportList"
        auto-resize
        stripe
      >
        <vxe-table-column min-width="60" field="id" title="ID">
        </vxe-table-column>
        <vxe-table-column
          min-width="100"
          field="did"
          :title="$t('m.Discussion_ID')"
        >
        </vxe-table-column>
         <vxe-table-column
          field="discussionTitle"
          :title="$t('m.Title')"
          show-overflow
          min-width="150"
        ></vxe-table-column>
        <vxe-table-column
          field="discussionAuthor"
          :title="$t('m.Author')"
          min-width="150"
          show-overflow
        ></vxe-table-column>
        <vxe-table-column
          min-width="150"
          field="reporter"
          show-overflow
          :title="$t('m.Reporter')"
        >
        </vxe-table-column>
        <vxe-table-column
          min-width="150"
          field="gmtCreate"
          :title="$t('m.Report_Time')"
        >
          <template v-slot="{ row }">
            {{ $filters.localtime(row.gmtCreate) }}
          </template>
        </vxe-table-column>
        <vxe-table-column
          min-width="100"
          field="status"
          :title="$t('m.Checked')"
        >
          <template v-slot="{ row }">
            <el-switch
              v-model="row.status"
              active-text=""
              inactive-text=""
              :active-value="true"
              :inactive-value="false"
              @change="handleCheckedSwitch(row)"
            >
            </el-switch>
          </template>
        </vxe-table-column>
        <vxe-table-column
          :title="$t('m.Option')"
          min-width="150"
          fixed="right"
        >
          <template v-slot="{ row }">
            <el-tooltip
              class="item"
              effect="dark"
              :content="$t('m.View_Report_content')"
              placement="top"
            >
              <el-button
                :icon="legacyElementIcons['el-icon-document']"
                @click="openReportDialog(row.content)"
                size="small"
                type="success"
              ></el-button>
            </el-tooltip>
            <el-tooltip
              effect="dark"
              :content="$t('m.View_Discussion')"
              placement="top"
            >
              <el-button
                :icon="legacyElementIcons['el-icon-search']"
                size="small"
                @click="toDiscussion(row.did, row.gid)"
                type="primary"
              >
              </el-button>
            </el-tooltip>
          </template>
        </vxe-table-column>
      </vxe-table>

      <div class="panel-options discussion-pagination">
        <el-pagination
          class="page"
          layout="prev, pager, next"
          @current-change="discussionReportCurrentChange"
          v-model:current-page="discussionReportCurrentPage"
          :page-size="pageSize"
          :total="discussionReportTotal"
        >
        </el-pagination>
      </div>
    </el-card>
  </div>
</template>
<script>
import api from '@/common/api';
import myMessage from '@/common/message';
export default {
  name: 'discussion',
  data() {
    return {
      pageSize: 10,
      discussionTotal: 0,
      discussionList: [],
      selectedDiscussions: [],
      keyword: '',
      queryKeyword: '',
      discussionLoadingTable: false,
      discussionCurrentPage: 1,

      discussionReportList: [],
      discussionReportTotal: 0,
      discussionReportCurrentPage: 1,
      discussionReportLoadingTable: false,
    };
  },
  mounted() {
    this.getDiscussionList(1);
    this.getDiscussionReportList();
  },
  methods: {
    discussionCurrentChange(page) {
      this.discussionCurrentPage = page;
      this.selectedDiscussions = [];
      this.getDiscussionList(page);
    },
    discussionReportCurrentChange(page) {
      this.discussionReportCurrentPage = page;
      this.getDiscussionReportList();
    },
    getDiscussionList(page) {
      this.discussionLoadingTable = true;
      let searchParams = {
        currentPage: page,
        keyword: this.queryKeyword,
        admin: true,
      };
      api.getDiscussionList(this.pageSize, searchParams).then(
        (res) => {
          this.discussionLoadingTable = false;
          this.discussionTotal = res.data.data.total;
          this.discussionList = res.data.data.records;
        },
        (res) => {
          this.discussionLoadingTable = false;
        }
      );
    },
    getDiscussionReportList() {
      this.discussionReportLoadingTable = true;
      api
        .admin_getDiscussionReport(
          this.discussionReportCurrentPage,
          this.pageSize
        )
        .then(
          (res) => {
            this.discussionReportLoadingTable = false;
            this.discussionReportList = res.data.data.records;
            this.discussionReportTotal = res.data.data.total;
          },
          (err) => {
            this.discussionReportLoadingTable = false;
          }
        );
    },
    filterByKeyword() {
      this.queryKeyword = this.keyword.trim();
      this.discussionCurrentChange(1);
      this.keyword = '';
    },
    // 用户表部分勾选 改变选中的内容
    handleSelectionChange({ records }) {
      this.selectedDiscussions = [];
      for (let num = 0; num < records.length; num++) {
        this.selectedDiscussions.push(records[num].id);
      }
    },
    // 一键全部选中，改变选中的内容列表
    handlechangeAll() {
      let discussion = this.$refs.xTable.getCheckboxRecords();
      this.selectedDiscussions = [];
      for (let num = 0; num < discussion.length; num++) {
        this.selectedDiscussions.push(discussion[num].id);
      }
    },
    changeDiscussionStatus(row) {
      const previousStatus = row.status === 0 ? 1 : 0;
      let discussion = {
        id: row.id,
        status: row.status,
      };
      api
        .admin_updateDiscussion(discussion)
        .then(() => {
          myMessage.success(this.$t('m.Update_Successfully'));
        })
        .catch(() => {
          row.status = previousStatus;
        });
    },
    handleTopSwitch(row) {
      const previousTopPriority = !row.topPriority;
      let discussion = {
        id: row.id,
        topPriority: row.topPriority,
      };
      api
        .admin_updateDiscussion(discussion)
        .then(() => {
          myMessage.success(this.$t('m.Update_Successfully'));
        })
        .catch(() => {
          row.topPriority = previousTopPriority;
        });
    },

    handleCheckedSwitch(row) {
      const previousStatus = !row.status;
      let discussionReport = {
        id: row.id,
        status: row.status,
      };
      api
        .admin_updateDiscussionReport(discussionReport)
        .then(() => {
          myMessage.success(this.$t('m.Update_Successfully'));
        })
        .catch(() => {
          row.status = previousStatus;
        });
    },

    toDiscussion(did, gid) {
      if(gid != null){
        window.open('/group/'+ gid +'/discussion-detail/' + did);
      }else{
        window.open('/discussion-detail/' + did);
      }
    },

    deleteDiscussion(didList) {
      if (!didList) {
        didList = this.selectedDiscussions;
      }
      if (didList.length > 0) {
        this.$confirm(this.$t('m.Delete_Discussion_Tips'), 'Tips', {
          type: 'warning',
        }).then(
          () => {
            api
              .admin_deleteDiscussion(didList)
              .then((res) => {
                myMessage.success(this.$t('m.Delete_successfully'));
                this.selectedDiscussions = [];
                const targetPage =
                  this.discussionCurrentPage > 1 &&
                  this.discussionList.length <= didList.length
                    ? this.discussionCurrentPage - 1
                    : this.discussionCurrentPage;
                this.discussionCurrentChange(targetPage);
              })
              .catch(() => {
                this.selectedDiscussions = [];
                this.getDiscussionList(this.discussionCurrentPage);
              });
          },
          () => {}
        );
      } else {
        myMessage.warning(
          this.$t('m.The_number_of_discussions_selected_cannot_be_empty')
        );
      }
    },
    openReportDialog(content) {
      content = String(content || '');
      let reg = '#(.*?)# ';
      let re = RegExp(reg, 'g');
      let tmp;
      let showContent = '<strong>' + this.$t('m.Tags') + '</strong>：';
      while ((tmp = re.exec(content))) {
        showContent += this.escapeHtml(tmp[1]) + ' ';
      }
      showContent +=
        '<br><br><strong>' +
        this.$t('m.Content') +
        '</strong>：' +
        this.escapeHtml(content.replace(/#(.*?)# /g, ''));
      this.$alert(showContent, this.$t('m.Report_Content'), {
        confirmButtonText: this.$t('m.OK'),
        dangerouslyUseHTMLString: true,
        customClass: 'discussion-report-message-box',
      });
    },
    escapeHtml(value) {
      return value.replace(/[&<>"']/g, (character) => {
        const entities = {
          '&': '&amp;',
          '<': '&lt;',
          '>': '&gt;',
          '"': '&quot;',
          "'": '&#039;',
        };
        return entities[character];
      });
    },
  },
};
</script>
<style scoped>
.filter-row {
  margin-top: 10px;
}
.discussion-admin-page :deep(.filter-row .el-button--small) {
  box-sizing: border-box;
  min-height: 32px;
  padding: 9px 15px;
}
.discussion-table,
.discussion-report-table {
  --vxe-ui-table-header-background-color: #f8f8f9;
}
.discussion-table {
  --vxe-ui-table-row-height-default: 50px;
}
.discussion-report-table {
  --vxe-ui-table-row-height-default: 48px;
}
.discussion-admin-page :deep(.vxe-table .el-select--small),
.discussion-admin-page :deep(.vxe-table .el-select__wrapper) {
  height: 32px;
  min-height: 32px;
}
.discussion-admin-page :deep(.vxe-table .el-button--small) {
  box-sizing: border-box;
  width: 44px;
  height: 29px;
  min-height: 29px;
  padding: 7px 15px;
}
.discussion-admin-page :deep(.vxe-table .el-button + .el-button) {
  margin-left: 10px;
}
.discussion-admin-page :deep(.vxe-table .el-switch) {
  height: 20px;
  line-height: 20px;
}
.discussion-pagination {
  display: block;
  min-height: 32px;
}
.discussion-pagination :deep(.el-pagination.page) {
  box-sizing: border-box;
  display: block;
  width: 100%;
  height: 32px;
  padding: 2px 5px;
  text-align: center;
  --el-pagination-button-width: 35.5px;
  --el-pagination-button-height: 28px;
}
.discussion-pagination :deep(.btn-prev),
.discussion-pagination :deep(.btn-next),
.discussion-pagination :deep(.el-pager) {
  display: inline-block;
  vertical-align: top;
}
.discussion-pagination :deep(.el-pager) {
  width: auto;
}
.discussion-pagination :deep(.el-pager li) {
  display: inline-block;
  vertical-align: top;
}
.discussion-pagination :deep(.btn-prev),
.discussion-pagination :deep(.btn-next),
.discussion-pagination :deep(.el-pager li) {
  width: 36px;
  min-width: 36px;
  height: 28px;
  line-height: 28px;
}
:global(.discussion-report-message-box) {
  box-sizing: border-box;
  padding: 0 0 10px;
  border: 1px solid #ebeef5;
  box-shadow: 0 2px 12px 0 rgb(0 0 0 / 10%);
}
:global(.discussion-report-message-box .el-message-box__header) {
  box-sizing: border-box;
  height: 43px;
  padding: 15px 15px 10px;
}
:global(.discussion-report-message-box .el-message-box__title) {
  height: 18px;
  line-height: 18px;
}
:global(.discussion-report-message-box .el-message-box__content) {
  box-sizing: border-box;
  min-height: 92px;
  padding: 10px 15px;
  line-height: 21px;
}
:global(.discussion-report-message-box .el-message-box__message) {
  width: 100%;
  line-height: 21px;
}
:global(.discussion-report-message-box .el-message-box__btns) {
  box-sizing: border-box;
  height: 37px;
  padding: 5px 15px 0;
}
:global(.discussion-report-message-box .el-message-box__btns .el-button) {
  width: 56px;
  height: 32px;
  min-height: 32px;
  padding: 9px 15px;
  font-size: 12px;
}
@media screen and (max-width: 768px) {
  .filter-row span {
    margin-right: 5px;
  }
}
@media screen and (min-width: 768px) {
  .filter-row span {
    margin-right: 20px;
  }
}
</style>
