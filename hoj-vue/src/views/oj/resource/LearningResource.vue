<template>
  <div class="learning-resource-page">
    <section class="resource-card library-card" v-loading="folderLoading">
      <header class="library-header">
        <div class="library-title-block">
          <div class="library-title">
            <i class="fa fa-folder library-heading-icon" aria-hidden="true"></i>
            <span>资料库</span>
          </div>
          <p>管理您创建的学习资料库，便于教学与分享</p>
        </div>
        <div v-if="isAdminRole" class="library-actions">
          <el-button type="primary" :icon="Plus" @click="openCreateFolder">
            新建资料库
          </el-button>
          <el-button
            type="danger"
            plain
            :icon="Delete"
            :disabled="!selectedFolder"
            @click="confirmDeleteFolder"
          >
            删除资料库
          </el-button>
        </div>
      </header>

      <div v-if="folders.length" class="folder-grid">
        <button
          v-for="folder in folders"
          :key="folder.id"
          type="button"
          class="folder-card"
          :class="{ selected: selectedFolderId === folder.id }"
          @click="selectFolder(folder)"
        >
          <span
            v-if="selectedFolderId === folder.id"
            class="current-folder-badge"
          >
            当前资料库
          </span>
          <span class="folder-name-row">
            <i
              class="fa fa-folder folder-icon"
              :style="{ color: folder.color }"
              aria-hidden="true"
            ></i>
            <strong :title="folder.name">{{ folder.name }}</strong>
          </span>
          <span class="folder-meta">
            <span>创建者</span>
            <span :title="folder.creatorUsername">{{ folder.creatorUsername }}</span>
          </span>
          <span class="folder-meta">
            <span>文件数</span>
            <span>{{ folder.fileCount }} 个文件</span>
          </span>
        </button>
      </div>
      <el-empty
        v-else
        class="folder-empty"
        description="暂无资料库，管理员可以新建一个资料库"
      />
    </section>

    <section class="resource-card file-card">
      <header class="file-header">
        <div>
          <div class="current-library-title">
            <i class="fa fa-folder library-heading-icon" aria-hidden="true"></i>
            <span>当前资料库：</span>
            <strong>{{ selectedFolder ? selectedFolder.name : '暂未选择' }}</strong>
          </div>
          <p>共 {{ total }} 个文件</p>
        </div>
        <el-upload
          v-if="isAdminRole"
          :auto-upload="false"
          :show-file-list="false"
          :disabled="!selectedFolder || uploading"
          :on-change="handleUploadChange"
          action="#"
        >
          <el-button
            type="primary"
            :icon="Upload"
            :loading="uploading"
            :disabled="!selectedFolder"
          >
            上传资料
          </el-button>
        </el-upload>
      </header>

      <div class="file-table-wrap" v-loading="fileLoading">
        <el-table
          v-if="selectedFolder"
          :data="files"
          class="file-table"
          empty-text="当前资料库暂无文件"
        >
          <el-table-column label="文件名" min-width="320">
            <template #default="{ row }">
              <div class="file-name-cell">
                <span :class="['file-kind-icon', 'kind-' + row.category]">
                  <i :class="fileIconClass(row)"></i>
                </span>
                <span class="file-name" :title="row.name">{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="150">
            <template #default="{ row }">
              <el-tag
                size="small"
                effect="plain"
                :type="row.category === 'image' ? 'success' : 'primary'"
              >
                {{ fileTypeLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="150">
            <template #default="{ row }">{{ formatSize(row.size) }}</template>
          </el-table-column>
          <el-table-column
            prop="uploaderUsername"
            label="上传人"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column label="上传时间" min-width="210">
            <template #default="{ row }">
              {{ formatTime(row.gmtCreate) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" :width="isAdminRole ? 300 : 210" fixed="right">
            <template #default="{ row }">
              <div class="file-actions">
                <el-button
                  type="primary"
                  plain
                  size="small"
                  :icon="View"
                  :disabled="!row.previewable"
                  @click="previewFile(row)"
                >
                  查看
                </el-button>
                <el-button
                  type="primary"
                  plain
                  size="small"
                  :icon="Download"
                  @click="downloadFile(row)"
                >
                  下载
                </el-button>
                <el-button
                  v-if="isAdminRole"
                  type="danger"
                  plain
                  size="small"
                  :icon="Delete"
                  @click="confirmDeleteFile(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="请先从上方选择一个资料库" />
      </div>

      <footer v-if="selectedFolder" class="file-footer">
        <span>共 {{ total }} 条</span>
        <Pagination
          v-model:current="currentPage"
          :total="total"
          :page-size="limit"
          :page-sizes="[10, 20, 50]"
          layout="sizes, prev, pager, next"
          @on-change="changePage"
          @on-page-size-change="changePageSize"
        />
      </footer>
    </section>

    <el-dialog
      v-model="createFolderVisible"
      title="新建资料库"
      width="430px"
      :close-on-click-modal="false"
      @closed="newFolderName = ''"
    >
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="资料库名称" required>
          <el-input
            v-model="newFolderName"
            maxlength="60"
            show-word-limit
            placeholder="请输入资料库名称"
            @keyup.enter="createFolder"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createFolderVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="folderSubmitting"
          @click="createFolder"
        >
          创建
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="previewVisible"
      :title="previewFileName"
      width="86%"
      top="5vh"
      class="resource-preview-dialog"
      destroy-on-close
      @closed="clearPreview"
    >
      <div class="preview-body" v-loading="previewLoading">
        <img
          v-if="previewCategory === 'image' && previewUrl"
          :src="previewUrl"
          :alt="previewFileName"
        />
        <iframe
          v-else-if="previewUrl"
          :src="previewUrl"
          :title="previewFileName"
        ></iframe>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" :icon="Download" @click="downloadPreviewFile">
          下载
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import axios from 'axios';
import {
  Delete,
  Download,
  Plus,
  Upload,
  View,
} from '@element-plus/icons-vue';
import { ElMessageBox } from 'element-plus';
import { mapGetters } from 'vuex';
import api from '@/common/api';
import mMessage from '@/common/message';
import Pagination from '@/components/oj/common/Pagination.vue';

export default {
  name: 'LearningResource',
  components: {
    Pagination,
  },
  data() {
    return {
      Delete,
      Download,
      Plus,
      Upload,
      View,
      folders: [],
      files: [],
      selectedFolderId: null,
      currentPage: 1,
      limit: 10,
      total: 0,
      folderLoading: false,
      fileLoading: false,
      uploading: false,
      createFolderVisible: false,
      folderSubmitting: false,
      newFolderName: '',
      previewVisible: false,
      previewLoading: false,
      previewUrl: '',
      previewFileName: '',
      previewCategory: '',
      previewRow: null,
    };
  },
  computed: {
    ...mapGetters(['isAdminRole']),
    selectedFolder() {
      return this.folders.find((folder) => folder.id === this.selectedFolderId) || null;
    },
  },
  mounted() {
    this.loadFolders();
  },
  beforeUnmount() {
    this.clearPreview();
  },
  methods: {
    async loadFolders(preferredFolderId) {
      this.folderLoading = true;
      try {
        const response = await api.getLearningResourceFolders();
        this.folders = response.data.data || [];
        const targetId = preferredFolderId || this.selectedFolderId;
        const matched = this.folders.find((folder) => folder.id === targetId);
        if (matched) {
          this.selectedFolderId = matched.id;
        } else if (this.folders.length) {
          this.selectedFolderId = this.folders[0].id;
        } else {
          this.selectedFolderId = null;
        }
        this.currentPage = 1;
        await this.loadFiles();
      } finally {
        this.folderLoading = false;
      }
    },
    async loadFiles() {
      if (!this.selectedFolderId) {
        this.files = [];
        this.total = 0;
        return;
      }
      this.fileLoading = true;
      try {
        const response = await api.getLearningResourceFiles(
          this.selectedFolderId,
          this.currentPage,
          this.limit
        );
        const data = response.data.data || {};
        this.files = data.records || [];
        this.total = Number(data.total) || 0;
      } finally {
        this.fileLoading = false;
      }
    },
    selectFolder(folder) {
      if (this.selectedFolderId === folder.id) {
        return;
      }
      this.selectedFolderId = folder.id;
      this.currentPage = 1;
      this.loadFiles();
    },
    openCreateFolder() {
      this.newFolderName = '';
      this.createFolderVisible = true;
    },
    async createFolder() {
      const name = this.newFolderName.trim();
      if (!name) {
        mMessage.warning('请输入资料库名称');
        return;
      }
      this.folderSubmitting = true;
      try {
        const response = await api.createLearningResourceFolder({ name });
        const folder = response.data.data;
        this.createFolderVisible = false;
        mMessage.success('资料库创建成功');
        await this.loadFolders(folder && folder.id);
      } finally {
        this.folderSubmitting = false;
      }
    },
    async confirmDeleteFolder() {
      if (!this.selectedFolder) {
        return;
      }
      try {
        await ElMessageBox.confirm(
          `确定删除资料库“${this.selectedFolder.name}”吗？资料库中有文件时不能删除。`,
          '删除资料库',
          {
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            type: 'warning',
            confirmButtonClass: 'el-button--danger',
          }
        );
        await api.deleteLearningResourceFolder(this.selectedFolder.id);
        mMessage.success('资料库删除成功');
        this.selectedFolderId = null;
        await this.loadFolders();
      } catch (error) {
        if (error !== 'cancel' && error !== 'close' && !error?.response) {
          throw error;
        }
      }
    },
    async handleUploadChange(uploadFile) {
      if (!uploadFile || !uploadFile.raw || !this.selectedFolderId) {
        return;
      }
      const formData = new FormData();
      formData.append('file', uploadFile.raw, uploadFile.name);
      this.uploading = true;
      try {
        await api.uploadLearningResourceFile(this.selectedFolderId, formData);
        mMessage.success('文件上传成功');
        this.currentPage = 1;
        await this.loadFolders(this.selectedFolderId);
      } finally {
        this.uploading = false;
      }
    },
    async confirmDeleteFile(row) {
      try {
        await ElMessageBox.confirm(
          `确定删除文件“${row.name}”吗？`,
          '删除文件',
          {
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            type: 'warning',
            confirmButtonClass: 'el-button--danger',
          }
        );
        await api.deleteLearningResourceFile(row.id);
        mMessage.success('文件删除成功');
        if (this.files.length === 1 && this.currentPage > 1) {
          this.currentPage -= 1;
        }
        await this.loadFolders(this.selectedFolderId);
      } catch (error) {
        if (error !== 'cancel' && error !== 'close' && !error?.response) {
          throw error;
        }
      }
    },
    async previewFile(row) {
      if (!row.previewable) {
        mMessage.warning('该格式不支持在线查看，请下载后打开');
        return;
      }
      this.clearPreview();
      this.previewRow = row;
      this.previewFileName = row.name;
      this.previewCategory = row.category;
      this.previewVisible = true;
      this.previewLoading = true;
      try {
        const response = await axios.get(
          `/api/learning-resource/files/${row.id}/preview`,
          { responseType: 'blob' }
        );
        this.previewUrl = window.URL.createObjectURL(response.data);
      } catch (error) {
        this.previewVisible = false;
        throw error;
      } finally {
        this.previewLoading = false;
      }
    },
    clearPreview() {
      if (this.previewUrl) {
        window.URL.revokeObjectURL(this.previewUrl);
      }
      this.previewUrl = '';
      this.previewFileName = '';
      this.previewCategory = '';
      this.previewRow = null;
      this.previewLoading = false;
    },
    async downloadFile(row) {
      const response = await axios.get(
        `/api/learning-resource/files/${row.id}/download`,
        { responseType: 'blob' }
      );
      const url = window.URL.createObjectURL(response.data);
      const link = document.createElement('a');
      link.href = url;
      link.download = row.name;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      mMessage.success('开始下载');
    },
    downloadPreviewFile() {
      if (this.previewRow) {
        this.downloadFile(this.previewRow);
      }
    },
    changePage(page) {
      this.currentPage = page;
      this.loadFiles();
    },
    changePageSize(size) {
      this.limit = size;
      this.currentPage = 1;
      this.loadFiles();
    },
    formatSize(bytes) {
      const value = Number(bytes) || 0;
      if (value < 1024) {
        return `${value} B`;
      }
      const units = ['KB', 'MB', 'GB'];
      let size = value / 1024;
      let index = 0;
      while (size >= 1024 && index < units.length - 1) {
        size /= 1024;
        index += 1;
      }
      const digits = size >= 100 ? 0 : size >= 10 ? 1 : 2;
      return `${size.toFixed(digits)} ${units[index]}`;
    },
    formatTime(value) {
      if (!value) {
        return '--';
      }
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) {
        return String(value).replace('T', ' ');
      }
      const pad = (part) => String(part).padStart(2, '0');
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
        date.getDate()
      )} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(
        date.getSeconds()
      )}`;
    },
    fileTypeLabel(row) {
      const labels = {
        image: '图片',
        pdf: '文档',
        text: '文本',
        word: '文档',
        excel: '表格',
        powerpoint: '演示文稿',
        archive: '压缩包',
        file: '文件',
      };
      return labels[row.category] || '文件';
    },
    fileIconClass(row) {
      const classes = {
        image: 'fa fa-file-image-o',
        pdf: 'fa fa-file-pdf-o',
        text: 'fa fa-file-text-o',
        word: 'fa fa-file-word-o',
        excel: 'fa fa-file-excel-o',
        powerpoint: 'fa fa-file-powerpoint-o',
        archive: 'fa fa-file-archive-o',
        file: 'fa fa-file-o',
      };
      return classes[row.category] || classes.file;
    },
  },
};
</script>

<style scoped>
.learning-resource-page {
  box-sizing: border-box;
  color: #303a4d;
  padding: 24px 0 38px;
  width: 100%;
}

.resource-card {
  background: #fff;
  border: 1px solid #edf0f5;
  border-radius: 8px;
  box-shadow: 0 7px 22px rgba(28, 55, 90, 0.08);
  overflow: hidden;
}

.library-card {
  margin-bottom: 24px;
}

.library-header,
.file-header {
  align-items: center;
  border-bottom: 1px solid #e9edf4;
  display: flex;
  justify-content: space-between;
}

.library-header {
  min-height: 92px;
  padding: 0 28px;
}

.library-title,
.current-library-title {
  align-items: center;
  display: flex;
}

.library-title {
  font-size: 23px;
  font-weight: 700;
  gap: 11px;
}

.library-heading-icon {
  color: #409eff;
}

.library-title-block p,
.file-header p {
  color: #7c879b;
  font-size: 14px;
  margin: 7px 0 0;
}

.library-actions {
  display: flex;
  gap: 12px;
}

.library-actions :deep(.el-button + .el-button),
.file-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.folder-grid {
  display: grid;
  gap: 24px;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  padding: 24px 30px 30px;
}

.folder-card {
  background: #fff;
  border: 1px solid #e3e8f0;
  border-radius: 10px;
  box-shadow: 0 5px 14px rgba(34, 58, 94, 0.07);
  color: #303a4d;
  cursor: pointer;
  font-family: inherit;
  min-height: 174px;
  padding: 24px 22px 18px;
  position: relative;
  text-align: left;
  transition: border-color 0.18s ease, box-shadow 0.18s ease,
    transform 0.18s ease;
}

.folder-card:hover {
  border-color: #85bdff;
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.12);
  transform: translateY(-1px);
}

.folder-card.selected {
  border: 2px solid #409eff;
  padding: 23px 21px 17px;
}

.current-folder-badge {
  background: #e8f3ff;
  border-radius: 4px;
  color: #409eff;
  font-size: 12px;
  padding: 5px 8px;
  position: absolute;
  right: 10px;
  top: 10px;
}

.folder-name-row {
  align-items: center;
  display: flex;
  gap: 14px;
  margin-bottom: 25px;
  min-width: 0;
}

.folder-icon {
  flex: none;
  font-size: 43px;
}

.folder-name-row strong {
  font-size: 17px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-meta {
  color: #8a94a7;
  display: flex;
  font-size: 14px;
  justify-content: space-between;
  margin-top: 14px;
}

.folder-meta span:last-child {
  color: #4b5568;
  margin-left: 12px;
  max-width: 65%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-empty {
  min-height: 226px;
}

.file-header {
  min-height: 88px;
  padding: 0 28px;
}

.current-library-title {
  font-size: 20px;
  font-weight: 600;
  gap: 7px;
}

.current-library-title strong {
  color: #409eff;
  font-weight: 600;
}

.file-table-wrap {
  min-height: 200px;
  padding: 0 28px;
}

.file-table {
  width: 100%;
}

.file-table :deep(th.el-table__cell) {
  background: #fff;
  color: #303a4d;
  font-size: 14px;
  font-weight: 600;
  height: 52px;
}

.file-table :deep(td.el-table__cell) {
  height: 58px;
}

.file-table :deep(.cell) {
  line-height: 22px;
}

.file-name-cell {
  align-items: center;
  display: flex;
  gap: 13px;
  min-width: 0;
}

.file-kind-icon {
  align-items: center;
  border-radius: 3px;
  color: #fff;
  display: inline-flex;
  flex: none;
  font-size: 19px;
  height: 26px;
  justify-content: center;
  width: 25px;
}

.kind-pdf {
  background: #e63b32;
}

.kind-image {
  background: #2bbb70;
}

.kind-word {
  background: #2f75d6;
}

.kind-excel {
  background: #198754;
}

.kind-powerpoint {
  background: #d65a2f;
}

.kind-text,
.kind-archive,
.kind-file {
  background: #7b8799;
}

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-actions {
  display: flex;
  gap: 9px;
}

.file-footer {
  align-items: center;
  color: #69758a;
  display: flex;
  justify-content: space-between;
  min-height: 70px;
  padding: 0 28px;
}

.file-footer :deep(.page) {
  margin: 0;
  width: auto;
}

.preview-body {
  align-items: center;
  background: #f6f8fb;
  display: flex;
  justify-content: center;
  min-height: 66vh;
  overflow: auto;
}

.preview-body img {
  max-height: 66vh;
  max-width: 100%;
  object-fit: contain;
}

.preview-body iframe {
  background: #fff;
  border: 0;
  height: 66vh;
  width: 100%;
}

@media screen and (max-width: 1360px) {
  .folder-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media screen and (max-width: 1050px) {
  .folder-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media screen and (max-width: 767px) {
  .learning-resource-page {
    padding: 12px 0 24px;
  }

  .library-header,
  .file-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
    padding: 20px;
  }

  .library-actions {
    width: 100%;
  }

  .library-actions :deep(.el-button) {
    flex: 1;
  }

  .folder-grid {
    gap: 14px;
    grid-template-columns: 1fr;
    padding: 18px;
  }

  .folder-card {
    min-height: 154px;
  }

  .file-table-wrap {
    overflow-x: auto;
    padding: 0 14px;
  }

  .file-table {
    min-width: 930px;
  }

  .file-footer {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
    padding: 18px;
  }

  .file-footer :deep(.page) {
    justify-content: flex-start;
    width: 100%;
  }
}
</style>
