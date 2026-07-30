<template>
  <div class="import-export-problem-page">
    <el-card class="import-export-card export-problem-card">
      <template #header>
        <div>
          <span class="panel-title home-title">{{ $t('m.Export_Problem') }}</span>
          <div class="filter-row">
            <span>
              <el-button
                class="export-problem-button"
                type="primary"
                size="small"
                @click="exportProblems"
                :icon="legacyElementIcons['el-icon-arrow-down']"
                >{{ $t('m.Export') }}
              </el-button>
            </span>
            <span>
              <vxe-input
                v-model="keyword"
                :placeholder="$t('m.Enter_keyword')"
                type="search"
                size="medium"
                @keyup.enter="filterByKeyword"
                @search-click="filterByKeyword"
              ></vxe-input>
            </span>
          </div>
        </div>
      </template>
      <vxe-table
        :data="problems"
        stripe
        auto-resize
        ref="xTable"
        :loading="loadingProblems"
        :checkbox-config="{ labelField: '', highlight: true, range: true }"
        @checkbox-change="handleSelectionChange"
        @checkbox-all="handlechangeAll"
      >
        <vxe-table-column type="checkbox" width="60"> </vxe-table-column>
        <vxe-table-column title="ID" min-width="100" field="id">
        </vxe-table-column>
        <vxe-table-column min-width="150" :title="$t('m.Title')" field="title">
        </vxe-table-column>
        <vxe-table-column
          min-width="150"
          field="author"
          :title="$t('m.Author')"
        >
        </vxe-table-column>

        <vxe-table-column field="gmtCreate" :title="$t('m.Created_Time')">
          <template v-slot="{ row }">
            {{ $filters.localtime(row.gmtCreate) }}
          </template>
        </vxe-table-column>
      </vxe-table>

      <div class="panel-options">
        <el-pagination
          class="page"
          layout="prev, pager, next, sizes"
          @current-change="getProblems"
          :page-size="limit"
          :page-sizes="[10, 50, 100, 500]"
          v-model:current-page="currentPage"
          @size-change="handleSizeChange"
          :total="total"
        >
        </el-pagination>
      </div>
    </el-card>

    <el-card class="import-export-card import-problem-card" style="margin-top:15px">
      <template #header>
        <div>
          <span class="panel-title home-title">{{ $t('m.Import_Problem') }}</span>
        </div>
      </template>
      <el-upload
        ref="hoj"
        action="/api/file/import-problem"
        name="file"
        :file-list="fileList1"
        :show-file-list="true"
        :with-credentials="true"
        :limit="3"
        :on-change="onFile1Change"
        :auto-upload="false"
        :on-success="
          (response, file, fileList) =>
            uploadSucceeded('hoj', response, file, fileList)
        "
        :on-error="(error, file, fileList) => uploadFailed('hoj', error, file, fileList)"
      >
        <template #trigger>
          <el-button
            size="small"
            :loading="loading.hoj"
            type="primary"
              :icon="legacyElementIcons['el-icon-folder-opened']"
            >{{ $t('m.Choose_File') }}</el-button
          >
        </template>
        <el-button
          style="margin-left: 10px;"
          size="small"
          type="success"
          @click="submitUpload('hoj')"
          :loading="loading.hoj"
          :disabled="!fileList1.length"
          :icon="legacyElementIcons['el-icon-upload']"
          >{{ $t('m.Upload') }}</el-button
        >
      </el-upload>
    </el-card>

    <el-card class="import-export-card import-problem-card" style="margin-top:15px">
      <template #header>
        <div>
          <span class="panel-title home-title">{{
            $t('m.Import_QDUOJ_Problem')
          }}</span>
        </div>
      </template>
      <el-upload
        ref="qduoj"
        action="/api/file/import-qdoj-problem"
        name="file"
        :file-list="fileList2"
        :show-file-list="true"
        :with-credentials="true"
        :limit="3"
        :on-change="onFile2Change"
        :auto-upload="false"
        :on-success="
          (response, file, fileList) =>
            uploadSucceeded('qduoj', response, file, fileList)
        "
        :on-error="
          (error, file, fileList) =>
            uploadFailed('qduoj', error, file, fileList)
        "
      >
        <template #trigger>
          <el-button
            size="small"
            type="primary"
              :loading="loading.qduoj"
            :icon="legacyElementIcons['el-icon-folder-opened']"
            >{{ $t('m.Choose_File') }}</el-button
          >
        </template>
        <el-button
          style="margin-left: 10px;"
          size="small"
          type="success"
          @click="submitUpload('qduoj')"
          :loading="loading.qduoj"
          :icon="legacyElementIcons['el-icon-upload']"
          :disabled="!fileList2.length"
          >{{ $t('m.Upload') }}</el-button
        >
      </el-upload>
    </el-card>

    <el-card class="import-export-card import-problem-card" style="margin-top:15px">
      <template #header>
        <div>
          <span class="panel-title home-title">{{
            $t('m.Import_FPS_Problem')
          }}</span>
        </div>
      </template>
      <el-upload
        ref="fps"
        action="/api/file/import-fps-problem"
        name="file"
        :file-list="fileList3"
        :show-file-list="true"
        :with-credentials="true"
        :limit="3"
        :on-change="onFile3Change"
        :auto-upload="false"
        :on-success="
          (response, file, fileList) =>
            uploadSucceeded('fps', response, file, fileList)
        "
        :on-error="(error, file, fileList) => uploadFailed('fps', error, file, fileList)"
      >
        <template #trigger>
          <el-button
            size="small"
            type="primary"
              :loading="loading.fps"
            :icon="legacyElementIcons['el-icon-folder-opened']"
            >{{ $t('m.Choose_File') }}</el-button
          >
        </template>
        <el-button
          style="margin-left: 10px;"
          size="small"
          type="success"
          @click="submitUpload('fps')"
          :loading="loading.fps"
          :icon="legacyElementIcons['el-icon-upload']"
          :disabled="!fileList3.length"
          >{{ $t('m.Upload') }}</el-button
        >
      </el-upload>
    </el-card>

    <el-card class="import-export-card import-problem-card" style="margin-top:15px">
      <template #header>
        <div>
          <span class="panel-title home-title">{{
            $t('m.Import_Hydro_Problem')
          }}</span>
        </div>
      </template>
      <el-upload
        ref="hydro"
        action="/api/file/import-hydro-problem"
        name="file"
        :file-list="fileList4"
        :show-file-list="true"
        :with-credentials="true"
        :limit="3"
        :on-change="onFile4Change"
        :auto-upload="false"
        :on-success="
          (response, file, fileList) =>
            uploadSucceeded('hydro', response, file, fileList)
        "
        :on-error="
          (error, file, fileList) =>
            uploadFailed('hydro', error, file, fileList)
        "
      >
        <template #trigger>
          <el-button
            size="small"
            type="primary"
              :loading="loading.hydro"
            :icon="legacyElementIcons['el-icon-folder-opened']"
            >{{ $t('m.Choose_File') }}</el-button
          >
        </template>
        <el-button
          style="margin-left: 10px;"
          size="small"
          type="success"
          @click="submitUpload('hydro')"
          :loading="loading.hydro"
          :icon="legacyElementIcons['el-icon-upload']"
          :disabled="!fileList4.length"
          >{{ $t('m.Upload') }}</el-button
        >
      </el-upload>
    </el-card>
  </div>
</template>
<script>
import api from '@/common/api';
import utils from '@/common/utils';
import myMessage from '@/common/message';
export default {
  name: 'import_and_export',
  data() {
    return {
      fileList1: [],
      fileList2: [],
      fileList3: [],
      fileList4: [],
      page: 1,
      currentPage: 1,
      limit: 10,
      total: 0,
      loadingProblems: false,
      loadingImporting: false,
      keyword: '',
      problems: [],
      selected_problems: [],
      loading: {
        hoj: false,
        qduoj: false,
        fps: false,
        hydro:false,
      },
    };
  },
  mounted() {
    this.getProblems();
  },
  methods: {
    // 题目表部分勾选 改变选中的内容
    handleSelectionChange({ records }) {
      this.selected_problems = records;
    },

    // 一键全部选中，改变选中的内容列表
    handlechangeAll() {
      this.selected_problems = this.$refs.xTable.getCheckboxRecords();
    },

    handleSizeChange(pageSize) {
      this.limit = pageSize;
      this.currentPage = 1;
      this.getProblems(1);
    },

    getProblems(page = 1) {
      this.currentPage = page;
      let params = {
        keyword: this.keyword,
        currentPage: page,
        limit: this.limit,
        oj: 'Mine',
      };
      this.loadingProblems = true;
      api.admin_getProblemList(params).then((res) => {
        this.problems = res.data.data.records;
        this.total = res.data.data.total;
        this.loadingProblems = false;
      });
    },
    exportProblems() {
      let params = [];
      if (this.selected_problems.length <= 0) {
        myMessage.error(this.$t('m.Export_Problem_NULL_Tips'));
        return;
      }
      for (let p of this.selected_problems) {
        params.push('pid=' + p.id);
      }
      let url = '/api/file/export-problem?' + params.join('&');
      utils.downloadFile(url);
    },
    submitUpload(ref) {
      this.loading[ref] = true;
      this.$refs[ref].submit();
    },
    onFile1Change(file, fileList) {
      this.fileList1 = fileList.slice(-1);
    },
    onFile2Change(file, fileList) {
      this.fileList2 = fileList.slice(-1);
    },
    onFile3Change(file, fileList) {
      this.fileList3 = fileList.slice(-1);
    },
    onFile4Change(file, fileList) {
      this.fileList4 = fileList.slice(-1);
    },
    clearUploadFiles(ref) {
      const fileListKey = {
        hoj: 'fileList1',
        qduoj: 'fileList2',
        fps: 'fileList3',
        hydro: 'fileList4',
      }[ref];
      this[fileListKey] = [];
      this.$refs[ref]?.clearFiles();
    },
    uploadSucceeded(ref, response) {
      this.loading[ref] = false;
      if (response.status != 200) {
        myMessage.error(response.msg);
        this.$notify.error({
          title: this.$t('m.Error'),
          message: response.msg,
          dangerouslyUseHTMLString: true,
          duration: 8000
        });
      } else {
        this.clearUploadFiles(ref);
        myMessage.success(this.$t('m.Upload_Problem_Succeeded'));
        this.getProblems(1);
      }
    },
    uploadFailed(ref) {
      this.loading[ref] = false;
      myMessage.error(this.$t('m.Upload_Problem_Failed'));
    },
    filterByKeyword() {
      this.currentPage = 1;
      this.getProblems(1);
    },
  },
};
</script>

<style scoped>
.import-export-card {
  display: block;
}

.filter-row {
  margin-top: 10px;
}

.import-export-problem-page :deep(.el-button--small) {
  box-sizing: border-box;
  min-height: 32px;
  padding: 9px 15px;
}

.import-export-problem-page :deep(.el-upload) {
  height: 32px;
  vertical-align: top;
}

.import-problem-card :deep(.el-upload-list) {
  margin-top: 0;
}

.import-export-problem-page :deep(.el-pagination.page) {
  box-sizing: border-box;
  width: 100%;
  height: 32px;
  padding: 2px 5px;
  --el-pagination-button-width: 35.5px;
  --el-pagination-button-height: 28px;
}

.import-export-problem-page :deep(.el-pagination.page .el-pagination__sizes) {
  width: 110px;
  height: 28px;
  margin-right: 10px;
  margin-left: 0;
}

.import-export-problem-page :deep(.el-pagination.page .el-pagination__sizes .el-select) {
  width: 110px;
  height: 28px;
}

.import-export-problem-page :deep(.el-pagination.page .el-pagination__sizes .el-select__wrapper) {
  box-sizing: border-box;
  min-height: 28px;
  padding: 2px 15px;
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
