<template>
  <div class="page" :class="{ 'legacy-pagination': legacySize }">
    <el-pagination
      background
      :size="mobilePagination ? 'small' : 'default'"
      :total="total"
      :pager-count="5"
      :page-size="pageSize"
      @current-change="onChange"
      @size-change="onPageSizeChange"
      :layout="layout"
      :page-sizes="pageSizes"
      :current-page="current"
      :hide-on-single-page="total == 0"
    ></el-pagination>
  </div>
</template>

<script>
export default {
  name: 'pagination',
  props: {
    total: {
      required: true,
      type: Number,
    },
    pageSize: {
      required: false,
      type: Number,
    },
    pageSizes: {
      required: false,
      type: Array,
      default: [10, 15, 30, 50, 100],
    },
    showSizer: {
      required: false,
      type: Boolean,
      default: false,
    },
    current: {
      required: false,
      type: Number,
    },
    layout: {
      require: false,
      type: String,
      default: 'prev, pager, next',
    },
    isMobile: {
      type: Boolean,
      default: false,
    },
    legacySize: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    mobilePagination() {
      return this.isMobile || window.screen.width < 768;
    },
  },
  methods: {
    onChange(page) {
      if (page < 1) {
        page = 1;
      }
      this.$emit('update:current', page);
      this.$emit('on-change', page);
    },
    onPageSizeChange(pageSize) {
      this.$emit('update:pageSize', pageSize);
      this.$emit('on-page-size-change', pageSize);
    },
  },
};
</script>

<style scoped>
.page {
  align-items: center;
  clear: both;
  display: flex;
  justify-content: flex-end;
  margin: 20px 0 0;
  min-width: 0;
  width: 100%;
}
:deep(.el-pagination) {
  flex-wrap: wrap;
  justify-content: flex-end;
  max-width: 100%;
  padding: 0 !important;
}
:deep(.el-pagination__sizes) {
  margin: 0px !important;
}
:deep(.el-pagination .el-select .el-input) {
  margin-right: 0px !important;
}

@media screen and (max-width: 767px) {
  .page,
  :deep(.el-pagination) {
    justify-content: center;
  }
}

@media screen and (min-width: 768px) {
  .legacy-pagination :deep(.el-pagination) {
    flex-wrap: nowrap;
    font-size: 12px;
    height: 32px;
    padding: 2px 0 2px 5px !important;
  }

  .legacy-pagination :deep(.el-pagination button),
  .legacy-pagination :deep(.el-pager li) {
    box-sizing: border-box;
    font-size: 13px;
    height: 28px;
    line-height: 28px;
    margin: 0 5px !important;
    min-width: 30px;
    width: 30px;
  }

  .legacy-pagination :deep(.el-pagination button) {
    padding: 0;
  }

  .legacy-pagination :deep(.el-pagination__sizes),
  .legacy-pagination :deep(.el-pagination__sizes .el-select) {
    font-size: 13px;
    height: 28px;
    line-height: 28px;
    width: 105px;
  }

  .legacy-pagination :deep(.el-pagination__sizes .el-select__wrapper) {
    box-sizing: border-box;
    font-size: 13px;
    height: 28px;
    min-height: 28px;
    padding: 0 8px;
  }
}
</style>
