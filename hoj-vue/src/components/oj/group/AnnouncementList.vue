<template>
  <div>
    <vxe-table
      stripe
      auto-resize
      :data="adminAnnouncementList"
      :loading="loading"
      align="center"
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
            :disabled="!isGroupRoot && row.uid != userInfo.uid"
          >
          </el-switch>
        </template>
      </vxe-table-column>
      <vxe-table-column :title="$t('m.Option')" min-width="150">
        <template v-slot="{ row }">
          <el-tooltip
            class="item"
            effect="dark"
            :content="$t('m.Edit_Announcement')"
            placement="top"
            v-if="isGroupRoot || row.uid == userInfo.uid"
          >
            <el-button
              :icon="legacyElementIcons['el-icon-edit-outline']"
              @click="openAnnouncementDialog(row)"
              size="small"
              type="primary"
            ></el-button>
          </el-tooltip>
          <el-tooltip
            class="item"
            effect="dark"
            :content="$t('m.Delete_Announcement')"
            placement="top"
            v-if="isGroupRoot || row.uid == userInfo.uid"
          >
            <el-button
              :icon="legacyElementIcons['el-icon-delete-solid']"
              @click="deleteAnnouncement(row.id)"
              size="small"
              type="danger"
            ></el-button>
          </el-tooltip>
        </template>
      </vxe-table-column>
    </vxe-table>
    <Pagination
      :total="adminTotal"
      :page-size="limit"
      @on-change="currentChange"
      v-model:current="currentPage"
      @on-page-size-change="onPageSizeChange"
      :layout="'prev, pager, next, sizes'"
    ></Pagination>
    <el-dialog
      class="group-announcement-dialog"
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
import { mapGetters } from 'vuex';
import Pagination from '@/components/oj/common/Pagination';
import api from '@/common/api';
import mMessage from '@/common/message';
import Editor from '@/components/admin/Editor.vue';
export default {
  name: 'GroupAnnouncementList',
  components: {
    Pagination,
    Editor
  },
  props: {
    contestId: {
      type: Number,
      default: null
    },
  },
  data() {
    return {
      showEditAnnouncementDialog: false,
      adminTotal: 0,
      currentPage: 1,
      limit: 10,
      mode: 'create',
      adminAnnouncementList: [],
      announcement: {
        id: null,
        title: '',
        content: '',
        status: 0,
        uid: '',
      },
      announcementDialogTitle: 'Edit Announcement',
      loading: false,
      routeName: '',
    };
  },
  mounted() {
    this.init();
  },
  methods: {
    init() {
      if (this.contestId) {
        this.getGroupContestAnnouncementList();
      } else {
        this.getGroupAdminAnnouncementList();
      }
    },
    onPageSizeChange(pageSize) {
      this.limit = pageSize;
      this.init();
    },
    currentChange(page) {
      this.currentPage = page;
      this.init();
    },
    getGroupAdminAnnouncementList() {
      this.loading = true;
      api.getGroupAdminAnnouncementList(this.currentPage, this.limit, this.$route.params.groupID).then(
        (res) => {
          this.adminAnnouncementList = res.data.data.records;
          this.adminTotal = res.data.data.total;
          this.loading = false;
        },
        (err) => {
          this.loading = false;
        }
      );
    },
    getGroupContestAnnouncementList() {
      this.loading = true;
      api.getGroupContestAnnouncementList(this.currentPage, this.limit, this.contestId).then(
        (res) => {
          this.adminAnnouncementList = res.data.data.records;
          this.adminTotal = res.data.data.total;
          this.loading = false;
        },
        (err) => {
          this.loading = false;
        }
      );
    },
    onOpenEditDialog() {
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
    submitAnnouncement(data = undefined) {
      let funcName = '';
      if (!data.id) {
        data = this.announcement;
      }
      data.gid = this.$route.params.groupID;
      let requestData;
      if (this.contestId) {
        let announcement = {
          announcement: data,
          cid: this.contestId,
        };
        requestData = announcement;
        funcName =
          this.mode === 'edit'
            ? 'updateGroupContestAnnouncement'
            : 'addGroupContestAnnouncement';
      } else {
        funcName =
          this.mode === 'edit'
            ? 'updateGroupAnnouncement'
            : 'addGroupAnnouncement';
        requestData = data;
      }
      api[funcName](requestData)
        .then((res) => {
          this.showEditAnnouncementDialog = false;
          mMessage.success(this.$t('m.Post_successfully'));
          this.currentChange(1);
        })
        .catch();
    },
    deleteAnnouncement(announcementId) {
      this.$confirm(this.$t('m.Delete_Announcement_Tips'), this.$t('m.Warning'), {
        confirmButtonText: this.$t('m.OK'),
        cancelButtonText: this.$t('m.Cancel'),
        type: 'warning',
      })
        .then(() => {
          this.loading = true;
          if (this.contestId) {
            api.deleteGroupContestAnnouncement(announcementId, this.contestId).then((res) => {
              this.loading = true;
              mMessage.success(this.$t('m.Delete_successfully'));
              this.init();
            });
          } else {
            api.deleteGroupAnnouncement(announcementId).then((res) => {
              this.loading = true;
              mMessage.success(this.$t('m.Delete_successfully'));
              this.init();
            });
          }
        })
        .catch(() => {
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
        this.mode = 'add';
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
  computed: {
    ...mapGetters(['userInfo', 'isGroupRoot']),
  },
};
</script>

<style scoped>
.title-input {
  margin-bottom: 20px;
}

.visible-box {
  margin-top: 10px;
  min-height: 32px;
  display: flex;
  align-items: center;
}
.visible-box span {
  margin-right: 10px;
}
.el-form-item {
  margin-bottom: 2px !important;
}

:deep(.group-announcement-dialog) {
  margin-bottom: 4vh;
  border-radius: 6px;
  overflow: hidden;
}

:deep(.group-announcement-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 18px 24px;
  border-bottom: 1px solid #ebeef5;
}

:deep(.group-announcement-dialog .el-dialog__body) {
  max-height: calc(92vh - 150px);
  overflow-y: auto;
  padding: 18px 24px 20px;
}

:deep(.group-announcement-dialog .el-dialog__footer) {
  padding: 14px 24px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
}
</style>
