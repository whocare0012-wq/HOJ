<template>
  <div class="view training-editor-page">
    <el-card class="training-editor-card">
      <template #header>
        <div>
          <span class="panel-title home-title">
            {{ title }}
          </span>
        </div>
      </template>
      <el-form class="training-form" label-position="top">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$t('m.Training_rank')" required>
              <el-input-number
                v-model="training.rank"
                @change="handleChange"
                :min="0"
                :max="2147483647"
                :label="$t('m.Training_rank')"
              ></el-input-number>
            </el-form-item>
          </el-col>

          <el-col :span="24">
            <el-form-item :label="$t('m.Training_Title')" required>
              <el-input
                v-model="training.title"
                :placeholder="$t('m.Training_Title')"
              ></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="$t('m.Training_Description')" required>
              <Editor v-model:value="training.description"></Editor>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="$t('m.Category')" required>
              <el-select
                class="training-category-select"
                v-model="trainingCategoryId"
              >
                <el-option
                  :label="category.name"
                  :value="category.id"
                  v-for="(category, index) in trainingCategoryList"
                  :key="index"
                ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="8" :xs="24">
            <el-form-item :label="$t('m.Training_Auth')" required>
              <el-select class="training-auth-select" v-model="training.auth">
                <el-option
                  :label="$t('m.Public_Training')"
                  value="Public"
                ></el-option>
                <el-option
                  :label="$t('m.Private_Training')"
                  value="Private"
                ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="8" :xs="24">
            <el-form-item
              :label="$t('m.Training_Password')"
              v-show="training.auth != 'Public'"
              :required="training.auth != 'Public'"
            >
              <el-input
                class="training-password-input"
                v-model="training.privatePwd"
                :placeholder="$t('m.Training_Password')"
              ></el-input>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-button
        class="training-save-button"
        type="primary"
        @click="saveTraining"
        >{{
        $t('m.Save')
      }}</el-button>
    </el-card>
  </div>
</template>

<script>
import { defineAsyncComponent } from 'vue';
import api from '@/common/api';
import { mapGetters } from 'vuex';
import myMessage from '@/common/message';
const Editor = defineAsyncComponent(() => import('@/components/admin/Editor.vue'));
export default {
  name: 'CreateTraining',
  components: {
    Editor,
  },
  data() {
    return {
      title: 'Create Training',
      training: {
        rank: 1000,
        title: '',
        description: '',
        privatePwd: '',
        auth: 'Public',
      },
      trainingCategoryId: null,
      trainingCategoryList: [],
    };
  },
  mounted() {
    this.init();
  },
  watch: {
    $route() {
      if (this.$route.name === 'admin-edit-training') {
        this.title = this.$t('m.Edit_Training');
        this.getTraining();
      } else {
        this.title = this.$t('m.Create_Training');
        this.training = {
          rank: 1000,
          title: '',
          description: '',
          privatePwd: '',
          auth: 'Public',
        };
        this.trainingCategoryId = null;
      }
    },
  },
  computed: {
    ...mapGetters(['userInfo']),
  },
  methods: {
    init() {
      api.getTrainingCategoryList().then((res) => {
        let data = res.data.data;
        if (!data || !data.length) {
          this.$alert(
            this.$t('m.Redirect_To_Category'),
            this.$t('m.Redirect'),
            {
              confirmButtonText: this.$t('m.OK'),
              showClose: false,
              callback: (action) => {
                this.$router.push({
                  path: '/admin/training/category',
                });
              },
            }
          );
        } else {
          this.trainingCategoryList = data;
          if (this.$route.name === 'admin-edit-training') {
            this.title = this.$t('m.Edit_Training');
            this.getTraining();
          } else {
            this.title = this.$t('m.Create_Training');
          }
        }
      });
    },

    getTraining() {
      api
        .admin_getTraining(this.$route.params.trainingId)
        .then((res) => {
          let data = res.data.data;
          this.training = data.training || {};
          this.trainingCategoryId = data.trainingCategory.id || null;
        })
        .catch(() => {});
    },

    saveTraining() {
      if (!this.training.rank && this.training.rank != 0) {
        myMessage.error(
          this.$t('m.Training_rank') + ' ' + this.$t('m.is_required')
        );
        return;
      }

      if (!this.training.title) {
        myMessage.error(
          this.$t('m.Training_Title') + ' ' + this.$t('m.is_required')
        );
        return;
      }
      if (!this.training.description) {
        myMessage.error(
          this.$t('m.Training_Description') +
            ' ' +
            this.$t('m.is_required')
        );
        return;
      }

      if (!this.trainingCategoryId) {
        myMessage.error(
          this.$t('m.Training_Category') +
            ' ' +
            this.$t('m.is_required')
        );
        return;
      }

      if (this.training.auth != 'Public' && !this.training.privatePwd) {
        myMessage.error(
          this.$t('m.Training_Password') +
            ' ' +
            this.$t('m.is_required')
        );
        return;
      }

      let funcName =
        this.$route.name === 'admin-edit-training'
          ? 'admin_editTraining'
          : 'admin_createTraining';

      let data = Object.assign({}, this.training);
      if (funcName === 'admin_createTraining') {
        data['author'] = this.userInfo.username;
      }
      let trainingDto = {
        training: data,
        trainingCategory: {
          id: this.trainingCategoryId,
        },
      };

      api[funcName](trainingDto)
        .then((res) => {
          myMessage.success('success');
          this.$router.push({
            name: 'admin-training-list',
            query: { refresh: 'true' },
          });
        })
        .catch(() => {});
    },
  },
};
</script>
<style scoped>
.training-editor-card {
  display: block;
}

.training-editor-page :deep(.training-form .el-form-item) {
  margin-bottom: 22px;
}

.training-editor-page :deep(.training-form .el-form-item__label) {
  box-sizing: border-box;
  height: 50px;
  line-height: 40px;
  margin: 0;
  padding: 0 0 10px;
}

.training-editor-page :deep(.training-form .el-form-item__content) {
  line-height: 40px;
  min-height: 40px;
}

.training-editor-page :deep(.training-form .el-input__wrapper),
.training-editor-page :deep(.training-form .el-select__wrapper) {
  box-sizing: border-box;
  min-height: 40px;
}

.training-editor-page :deep(.training-form .el-input-number) {
  height: 40px;
  line-height: 40px;
  width: 180px;
}

.training-editor-page :deep(.training-form .el-input-number .el-input__wrapper) {
  height: 40px;
  padding-left: 50px;
  padding-right: 50px;
}

.training-editor-page :deep(.training-form .el-input-number__decrease),
.training-editor-page :deep(.training-form .el-input-number__increase) {
  box-sizing: border-box;
  height: 38px;
  line-height: 38px;
  width: 40px;
}

.training-category-select,
.training-auth-select {
  width: 217px;
}

.training-save-button {
  box-sizing: border-box;
  height: 40px;
  padding: 12px 20px;
}

.userPreview {
  padding-left: 10px;
  padding-top: 20px;
  padding-bottom: 20px;
  color: red;
  font-size: 16px;
  margin-bottom: 10px;
}
</style>
