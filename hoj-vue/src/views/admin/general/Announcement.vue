<template>
  <div>
    <el-card>
      <template #header>
        <div>
          <span class="panel-title home-title">{{
            $t('m.General_Announcement')
          }}</span>
        </div>
      </template>
      <div class="create">
        <el-button
          class="announcement-create-button"
          type="primary"
          size="small"
          @click="openAnnouncementDialog(null)"
          ><span class="announcement-create-content"
            ><i class="el-icon-plus" aria-hidden="true"></i
            ><span>{{ $t('m.Create') }}</span></span
          ></el-button
        >
      </div>
      <div class="list">
        <vxe-table
          :loading="loading"
          ref="table"
          :data="announcementList"
          auto-resize
          stripe
        >
          <vxe-table-column min-width="50" field="id" title="ID">
          </vxe-table-column>
          <vxe-table-column
            min-width="150"
            field="title"
            show-overflow
            :title="$t('m.Announcement_Title')"
          >
          </vxe-table-column>
          <vxe-table-column
            min-width="150"
            field="gmtCreate"
            :title="$t('m.Created_Time')"
          >
            <template v-slot="{ row }">
              {{ $filters.localtime(row.gmtCreate) }}
            </template>
          </vxe-table-column>
          <vxe-table-column
            min-width="150"
            field="gmtModified"
            :title="$t('m.Modified_Time')"
          >
            <template v-slot="{ row }">
              {{ $filters.localtime(row.gmtModified) }}
            </template>
          </vxe-table-column>
          <vxe-table-column
            min-width="150"
            field="username"
            show-overflow
            :title="$t('m.Author')"
          >
          </vxe-table-column>
          <vxe-table-column
            min-width="100"
            field="status"
            :title="$t('m.Announcement_visible')"
          >
            <template v-slot="{ row }">
              <el-switch
                v-model="row.status"
                active-text=""
                inactive-text=""
                :active-value="0"
                :inactive-value="1"
                @change="handleVisibleSwitch(row)"
              >
              </el-switch>
            </template>
          </vxe-table-column>
          <vxe-table-column title="Option" min-width="150" fixed="right">
            <template v-slot="row">
              <el-tooltip
                class="item"
                effect="dark"
                :content="$t('m.Edit_Announcement')"
                placement="top"
              >
                <el-button
                  class="announcement-action-button"
                  :aria-label="$t('m.Edit_Announcement')"
                  @click="openAnnouncementDialog(row.row)"
                  size="small"
                  type="primary"
                  ><i
                    class="el-icon-edit-outline"
                    aria-hidden="true"
                  ></i
                ></el-button>
              </el-tooltip>
              <el-tooltip
                class="item"
                effect="dark"
                :content="$t('m.Delete_Announcement')"
                placement="top"
              >
                <el-button
                  class="announcement-action-button"
                  :aria-label="$t('m.Delete_Announcement')"
                  @click="deleteAnnouncement(row.row.id)"
                  size="small"
                  type="danger"
                  ><i
                    class="el-icon-delete-solid"
                    aria-hidden="true"
                  ></i
                ></el-button>
              </el-tooltip>
            </template>
          </vxe-table-column>
        </vxe-table>

        <div class="panel-options">
          <el-pagination
            v-if="!contestID"
            class="page"
            layout="prev, pager, next"
            @current-change="currentChange"
            :page-size="pageSize"
            :total="total"
          >
          </el-pagination>
        </div>
      </div>
    </el-card>

    <!--编辑公告对话框-->
    <el-dialog
      class="announcement-dialog"
      :title="announcementDialogTitle"
      v-model="showEditAnnouncementDialog"
      width="min(1120px, calc(100vw - 48px))"
      top="4vh"
      :close-on-click-modal="false"
      destroy-on-close
      @open="onOpenEditDialog"
    >
      <el-form label-position="top" :model="announcement">
        <el-form-item :label="$t('m.Announcement_Title')" required>
          <el-input
            v-model="announcement.title"
            :placeholder="$t('m.Announcement_Title')"
            class="title-input"
          >
          </el-input>
        </el-form-item>
        <el-form-item :label="$t('m.Announcement_Content')" required>
          <Editor v-model:value="announcement.content"></Editor>
        </el-form-item>
        <div class="visible-box">
          <span>{{ $t('m.Announcement_visible') }}</span>
          <el-switch
            v-model="announcement.status"
            :active-value="0"
            :inactive-value="1"
            active-text=""
            inactive-text=""
          >
          </el-switch>
        </div>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button
            type="danger"
            @click="showEditAnnouncementDialog = false"
            >{{ $t('m.Cancel') }}</el-button
          >
          <el-button type="primary" @click="submitAnnouncement">{{
            $t('m.OK')
          }}</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { defineAsyncComponent } from 'vue';
import api from '@/common/api';
import myMessage from '@/common/message';
import { mapGetters } from 'vuex';
const Editor = defineAsyncComponent(() => import('@/components/admin/Editor.vue'));
export default {
  name: 'announcement',
  components: {
    Editor,
  },
  data() {
    return {
      contestID: '',
      // 显示编辑公告对话框
      showEditAnnouncementDialog: false,
      // 公告列表
      announcementList: [],
      // 一页显示的公告数
      pageSize: 15,
      // 总公告数
      total: 0,
      mode: 'create',
      // 公告 (new | edit) model

      announcement: {
        id: null,
        title: '',
        content: '',
        status: 0,
        uid: '',
      },
      // 对话框标题
      announcementDialogTitle: 'Edit Announcement',
      // 是否显示loading
      loading: false,
      // 当前页码
      currentPage: 0,
    };
  },
  mounted() {
    this.init();
  },
  methods: {
    init() {
      this.contestID = this.$route.params.contestId;
      if (this.contestID) {
        this.getContestAnnouncementList(1);
      } else {
        this.getAnnouncementList(1);
      }
    },
    // 切换页码回调
    currentChange(page) {
      this.currentPage = page;
      if (this.contestID) {
        this.getContestAnnouncementList(page);
      } else {
        this.getAnnouncementList(page);
      }
    },

    getAnnouncementList(page) {
      this.loading = true;
      api.admin_getAnnouncementList(page, this.pageSize).then(
        (res) => {
          this.loading = false;
          this.total = res.data.data.total;
          this.announcementList = res.data.data.records;
        },
        (res) => {
          this.loading = false;
        }
      );
    },
    getContestAnnouncementList(page) {
      this.loading = true;
      api
        .admin_getContestAnnouncementList(this.contestID, page, this.pageSize)
        .then((res) => {
          this.loading = false;
          this.total = res.data.data.total;
          this.announcementList = res.data.data.records;
        })
        .catch(() => {
          this.loading = false;
        });
    },
    // 打开编辑对话框的回调
    onOpenEditDialog() {
      // todo 优化
      // 暂时解决 文本编辑器显示异常bug
      setTimeout(() => {
        if (document.createEvent) {
          let event = document.createEvent('HTMLEvents');
          event.initEvent('resize', true, true);
          window.dispatchEvent(event);
        } else if (document.createEventObject) {
          window.fireEvent('onresize');
        }
      }, 0);
    },
    // 提交编辑
    // 默认传入MouseEvent
    submitAnnouncement(data = undefined) {
      let funcName = '';
      if (!data.id) {
        data = this.announcement;
      }
      let requestData;
      if (this.contestID) {
        let announcement = {
          announcement: data,
          cid: this.contestID,
        };
        requestData = announcement;
        funcName =
          this.mode === 'edit'
            ? 'admin_updateContestAnnouncement'
            : 'admin_createContestAnnouncement';
      } else {
        funcName =
          this.mode === 'edit'
            ? 'admin_updateAnnouncement'
            : 'admin_createAnnouncement';
        requestData = data;
      }
      api[funcName](requestData)
        .then((res) => {
          this.showEditAnnouncementDialog = false;
          myMessage.success(this.$t('m.Post_successfully'));
          this.init();
        })
        .catch();
    },

    // 删除公告
    deleteAnnouncement(announcementId) {
      this.$confirm(this.$t('m.Delete_Announcement_Tips'), 'Warning', {
        confirmButtonText: this.$t('m.OK'),
        cancelButtonText: this.$t('m.Cancel'),
        type: 'warning',
      })
        .then(() => {
          // then 为确定
          this.loading = true;
          let funcName = this.contestID
            ? 'admin_deleteContestAnnouncement'
            : 'admin_deleteAnnouncement';
          api[funcName](announcementId).then((res) => {
            this.loading = true;
            myMessage.success(this.$t('m.Delete_successfully'));
            this.init();
          });
        })
        .catch(() => {
          // catch 为取消
          this.loading = false;
        });
    },

    openAnnouncementDialog(row) {
      this.showEditAnnouncementDialog = true;
      if (row !== null) {
        this.announcementDialogTitle = this.$t('m.Edit_Announcement');
        this.announcement = Object.assign({}, row);
        this.mode = 'edit';
      } else {
        this.announcementDialogTitle = this.$t('m.Create_Announcement');
        this.announcement.title = '';
        this.announcement.status = 0;
        this.announcement.content = '';
        this.announcement.uid = this.userInfo.uid;
        this.announcement.username = this.userInfo.username;
        this.mode = 'create';
      }
    },
    handleVisibleSwitch(row) {
      this.mode = 'edit';
      this.submitAnnouncement({
        id: row.id,
        title: row.title,
        content: row.content,
        status: row.status,
        uid: row.uid,
      });
    },
  },
  watch: {
    $route() {
      this.init();
    },
  },
  computed: {
    ...mapGetters(['userInfo']),
  },
};
</script>

<style scoped>
@font-face {
  font-family: 'announcement-element-icons';
  src: url('../../../assets/fonts/element-icons.woff') format('woff');
  font-style: normal;
  font-weight: 400;
  font-display: block;
}

.title-input {
  margin-bottom: 20px;
}

.visible-box {
  margin-top: 10px;
  width: 205px;
  float: left;
}
.visible-box span {
  margin-right: 10px;
}
.el-form-item {
  margin-bottom: 2px !important;
}
:deep(.announcement-dialog) {
  margin-bottom: 4vh;
  border-radius: 6px;
  overflow: hidden;
}

:deep(.announcement-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 18px 24px;
  border-bottom: 1px solid #ebeef5;
}

:deep(.announcement-dialog .el-dialog__body) {
  max-height: calc(92vh - 150px);
  overflow-y: auto;
  padding: 18px 24px 20px;
}

:deep(.announcement-dialog .el-dialog__footer) {
  padding: 14px 24px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
}
.create {
  margin-bottom: 5px;
}

.announcement-create-button {
  width: 73px;
  height: 32px;
  padding: 0;
}

.announcement-create-content {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.announcement-create-content .el-icon-plus {
  font-size: 12px;
}

.announcement-create-content .el-icon-plus::before {
  content: '\e6d9';
  font-family: 'announcement-element-icons';
}

.announcement-action-button {
  width: 40px;
  height: 24px;
  padding: 0;
}

.announcement-action-button .el-icon-edit-outline,
.announcement-action-button .el-icon-delete-solid {
  font-size: 14px;
}

.announcement-action-button .el-icon-edit-outline::before {
  content: '\e764';
  font-family: 'announcement-element-icons';
}

.announcement-action-button .el-icon-delete-solid::before {
  content: '\e7c9';
  font-family: 'announcement-element-icons';
}
</style>
