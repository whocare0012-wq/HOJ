<template>
  <div class="training-category-page">
    <el-card class="training-category-card">
      <template #header>
        <div>
          <span class="panel-title home-title">{{ $t('m.Admin_Category') }}</span>
          <div class="filter">
            <span>
              <el-button
                type="primary"
                size="small"
                @click="openCategoryDialog('add', null)"
                :icon="legacyElementIcons['el-icon-plus']"
                >{{ $t('m.Add_Category') }}
              </el-button>
            </span>
          </div>
        </div>
      </template>

      <el-tag
        :key="index"
        v-for="(category, index) in categoryList"
        closable
        :color="category.color ? category.color : '#409eff'"
        effect="dark"
        :disable-transitions="false"
        @close="deleteCategory(category)"
        @click="openCategoryDialog('update', category)"
        class="category"
      >
        {{ category.name }}
      </el-tag>

      <el-button
        class="button-new-category"
        size="small"
        @click="openCategoryDialog('add', null)"
        >+ New Category</el-button
      >
    </el-card>

    <el-dialog
      class="training-category-dialog"
      :title="$t('m.' + upsertTitle)"
      width="350px"
      v-model="addCategoryDialogVisible"
      :close-on-click-modal="false"
    >
      <el-form class="training-category-form">
        <el-form-item :label="$t('m.Category_Name')" required>
          <el-input v-model="category.name" size="small"></el-input>
        </el-form-item>
        <el-form-item :label="$t('m.Category_Color')" required>
          <el-color-picker v-model="category.color"></el-color-picker>
        </el-form-item>
        <el-form-item style="text-align:center">
          <el-button
            type="primary"
            @click="upsertCategory"
            :loading="upsertCategoryLoading"
            >{{ $t('m.' + upsertCategoryBtn) }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>
<script>
import myMessage from '@/common/message';
import api from '@/common/api';
export default {
  data() {
    return {
      getCategoryListLoading: false,
      categoryList: [],
      addCategoryDialogVisible: false,
      upsertTitle: 'Add_Category',
      upsertCategoryBtn: 'To_Add',
      upsertCategoryLoading: false,
      category: {
        id: null,
        name: null,
        color: null,
      },
    };
  },
  mounted() {
    this.getTrainingCategoryList();
  },
  methods: {
    getTrainingCategoryList() {
      this.getCategoryListLoading = true;
      api.getTrainingCategoryList().then(
        (res) => {
          this.categoryList = res.data.data;
          this.getCategoryListLoading = false;
        },
        (err) => {
          this.getCategoryListLoading = false;
        }
      );
    },

    deleteCategory(category) {
      this.$confirm(this.$t('m.Delete_Category_Tips'), 'Tips', {
        type: 'warning',
      }).then(
        () => {
          api
            .admin_deleteCategory(category.id)
            .then((res) => {
              myMessage.success(this.$t('m.Delete_successfully'));
              this.categoryList.splice(this.categoryList.indexOf(category), 1);
            })
            .catch(() => {});
        },
        () => {}
      );
    },
    openCategoryDialog(action, category) {
      if (action == 'add') {
        this.upsertTitle = 'Add_Category';
        this.upsertCategoryBtn = 'To_Add';
        this.category = {
          id: null,
          name: null,
          color: '#409eff',
        };
      } else {
        this.upsertTitle = 'Update_Category';
        this.upsertCategoryBtn = 'To_Update';
        this.category = Object.assign({}, category);
      }
      this.addCategoryDialogVisible = true;
    },

    upsertCategory() {
      this.category.name =
        typeof this.category.name === 'string'
          ? this.category.name.trim()
          : '';
      if (!this.category.name) {
        myMessage.error(
          this.$t('m.Category_Name') + ' ' + this.$t('m.is_required')
        );
        return;
      }
      if (!this.category.color) {
        myMessage.error(
          this.$t('m.Category_Color') + ' ' + this.$t('m.is_required')
        );
        return;
      }

      if (this.category.id) {
        this.upsertCategoryLoading = true;
        api.admin_updateCategory(this.category).then(
          (res) => {
            this.upsertCategoryLoading = false;
            myMessage.success(this.$t('m.Update_Successfully'));
            this.addCategoryDialogVisible = false;
            this.getTrainingCategoryList();
          },
          (err) => {
            this.upsertCategoryLoading = false;
          }
        );
      } else {
        this.upsertCategoryLoading = true;
        api.admin_addCategory(this.category).then(
          (res) => {
            this.upsertCategoryLoading = false;
            myMessage.success(this.$t('m.Add_Successfully'));
            this.categoryList.push(res.data.data);
            this.addCategoryDialogVisible = false;
          },
          (err) => {
            this.upsertCategoryLoading = false;
          }
        );
      }
    },
  },
};
</script>
<style scoped>
.training-category-card {
  display: block;
}

.filter {
  margin-top: 10px;
}
.filter span {
  margin-right: 10px;
}
.el-tag {
  margin-left: 10px;
  margin-top: 10px;
}
.category {
  cursor: pointer;
}

.training-category-page :deep(.filter .el-button--small) {
  box-sizing: border-box;
  min-height: 32px;
  padding: 9px 15px;
}

.training-category-page :deep(.el-tag) {
  box-sizing: border-box;
  height: 32px;
  line-height: 30px;
  padding: 0 10px;
}

.button-new-category {
  margin-left: 10px;
  height: 32px;
  line-height: 30px;
  padding-top: 0;
  padding-bottom: 0;
  padding-left: 15px;
  padding-right: 15px;
  margin-top: 10px;
}

:global(.training-category-dialog.el-dialog) {
  --el-dialog-padding-primary: 0px;
  padding: 0;
}

:global(.training-category-dialog .el-dialog__header) {
  box-sizing: border-box;
  height: 54px;
  margin: 0;
  padding: 20px 20px 10px;
}

:global(.training-category-dialog .el-dialog__title) {
  color: #303133;
  font-size: 18px;
  font-weight: 400;
  line-height: 24px;
}

:global(.training-category-dialog .el-dialog__headerbtn) {
  height: 24px;
  right: 20px;
  top: 20px;
  width: 16px;
}

:global(.training-category-dialog .el-dialog__body) {
  box-sizing: border-box;
  color: #606266;
  font-size: 14px;
  padding: 30px 20px;
  word-break: break-all;
}

:global(.training-category-form) {
  width: 310px;
}

:global(.training-category-form .el-form-item) {
  display: block;
  margin-bottom: 22px;
}

:global(.training-category-form .el-form-item::after),
:global(.training-category-form .el-form-item::before) {
  content: "";
  display: table;
}

:global(.training-category-form .el-form-item::after) {
  clear: both;
}

:global(.training-category-form .el-form-item__label) {
  display: block;
  float: left;
  font-size: 14px;
  height: 40px;
  line-height: 40px;
  padding: 0 12px 0 0;
}

:global(.training-category-form .el-form-item__content) {
  display: block;
  font-size: 14px;
  line-height: 40px;
  min-height: 40px;
  position: relative;
}

:global(.training-category-form .el-form-item:nth-child(1) .el-form-item__content) {
  clear: both;
  min-height: 81px;
}

:global(.training-category-form .el-form-item:nth-child(2) .el-form-item__content) {
  min-height: 55px;
}

:global(.training-category-form .el-form-item:last-child) {
  margin-bottom: 0;
}

:global(.training-category-form .el-form-item:last-child .el-form-item__content) {
  height: 40px;
  min-height: 40px;
}

:global(.training-category-form .el-input) {
  display: inline-block;
  flex: none;
  line-height: 40px;
  width: 310px;
}

:global(.training-category-form .el-input--small .el-input__wrapper) {
  box-sizing: border-box;
  font-size: 13px;
  height: 32px;
  min-height: 32px;
  padding: 1px 15px;
  width: 310px;
}

:global(.training-category-form .el-color-picker),
:global(.training-category-form .el-color-picker__trigger) {
  height: 40px;
  width: 40px;
}

:global(.training-category-form .el-color-picker__trigger) {
  padding: 4px;
}

:global(.training-category-form .el-button--primary) {
  box-sizing: border-box;
  height: 40px;
  padding: 12px 20px;
}
</style>
