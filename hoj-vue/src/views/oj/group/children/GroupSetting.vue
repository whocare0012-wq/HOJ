<template>
  <el-card>
    <div class="section-title">{{ $t('m.Avatar_Setting') }}</div>
    <div class="section-main">
      <avatar
        :inline="true"
        :size="130"
        color="#FFF"
        :src="group.avatar ? group.avatar : defaultAvatar"
      ></avatar>
      <div v-if="!avatarOption.imgSrc" class="avatar-upload-wrap">
        <el-upload
          class="upload-container"
          action=""
          drag
          :before-upload="handleSelectFile"
        >
          <div style="padding: 20px 0">
            <i class="el-icon-upload" style="color: #3399ff;font-size:52px"></i>
            <p>{{ $t('m.Upload_avatar_hint') }}</p>
          </div>
        </el-upload>
      </div>
      <template v-else>
        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <div class="cropper-main inline">
              <VueCropper
                ref="cropper"
                auto-crop
                fixed
                :auto-crop-width="200"
                :auto-crop-height="200"
                :img="avatarOption.imgSrc"
                :output-size="avatarOption.size"
                :output-type="avatarOption.outputType"
                :info="true"
                @real-time="realTime"
              >
              </VueCropper>
            </div>
            <div class="cropper-btn">
              <el-tooltip
                class="item"
                effect="dark"
                content="向左旋转90°"
                trigger="hover"
                placement="bottom"
              >
                <el-button
                  @click="rotate('left')"
                  :icon="legacyElementIcons['el-icon-refresh-left']"
                  size="small"
                ></el-button>
              </el-tooltip>
              <el-tooltip
                class="item"
                effect="dark"
                content="向右旋转90°"
                trigger="hover"
                placement="bottom"
              >
                <el-button
                  @click="rotate('right')"
                  :icon="legacyElementIcons['el-icon-refresh-right']"
                  size="small"
                ></el-button>
              </el-tooltip>
              <el-tooltip
                class="item"
                effect="dark"
                content="关闭图像截取"
                trigger="hover"
                placement="bottom"
              >
                <el-button
                  @click="reselect"
                  :icon="legacyElementIcons['el-icon-refresh']"
                  size="small"
                ></el-button>
              </el-tooltip>
              <el-tooltip
                class="item"
                effect="dark"
                trigger="hover"
                content="确定图像截取"
                placement="bottom"
              >
                <el-button
                  @click="finishCrop"
                  :icon="legacyElementIcons['el-icon-check']"
                  size="small"
                ></el-button>
              </el-tooltip>
            </div>
          </el-col>
          <el-col :xs="24" :md="12">
            <div class="cropper-preview" :style="previewStyle">
              <div :style="preview.div">
                <img :src="avatarOption.imgSrc" :style="preview.img" />
              </div>
            </div>
          </el-col>
        </el-row>
      </template>
      <el-dialog
        v-model="uploadModalVisible"
        :title="$t('m.Upload')"
        width="350px"
      >
        <div class="upload-modal">
          <p class="notice">{{ $t('m.Your_new_avatar') + ':' }}</p>
          <img :src="uploadImgSrc" />
        </div>
        <template #footer>
          <div>
            <el-button
              @click="uploadAvatar"
              :loading="loadingUploadBtn"
              type="primary"
              >{{ $t('m.Upload') }}</el-button
            >
          </div>
        </template>
      </el-dialog>
    </div>
    <div class="section-title profile-section-title">
      {{ $t('m.UserInfo_Setting') }}
    </div>
    <el-form
      class="group-profile-form"
      label-position="top"
      :model="group"
      :rules="rules"
      ref="formGroup"
    >
      <el-row :gutter="20">
        <el-col :md="12" :xs="24">
          <el-form-item :label="$t('m.Group_Name')" required prop="name">
            <el-input
              v-model="group.name"
              :placeholder="$t('m.Group_Name')"
              class="title-input"
              minlength="5"
              maxlength="25"
              show-word-limit
            >
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :md="12" :xs="24">
          <el-form-item
            :label="$t('m.Group_Short_Name')"
            required
            prop="shortName"
          >
            <el-input
              v-model="group.shortName"
              :placeholder="$t('m.Group_Short_Name')"
              class="title-input"
              minlength="5"
              maxlength="10"
              show-word-limit
            >
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item :label="$t('m.Group_Brief')" required prop="brief">
            <el-input
              v-model="group.brief"
              :placeholder="$t('m.Group_Brief')"
              class="title-input"
              minlength="5"
              maxlength="50"
              show-word-limit
            >
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :md="8" :xs="24">
          <el-form-item :label="$t('m.Group_Auth')" required prop="auth">
            <el-select v-model="group.auth" class="group-auth-select">
              <el-tooltip
                :content="$t('m.Group_Public_Tips')"
                placement="right"
              >
                <el-option :label="$t('m.Group_Public')" :value="1"></el-option>
              </el-tooltip>
              <el-tooltip
                :content="$t('m.Group_Protected_Tips')"
                placement="right"
              >
                <el-option
                  :label="$t('m.Group_Protected')"
                  :value="2"
                ></el-option>
              </el-tooltip>
              <el-tooltip
                :content="$t('m.Group_Private_Tips')"
                placement="right"
              >
                <el-option
                  :label="$t('m.Group_Private')"
                  :value="3"
                ></el-option>
              </el-tooltip>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :md="8" :xs="24" v-if="group.auth != 1">
          <el-form-item :label="$t('m.Group_Code')" required prop="code">
            <el-input
              v-model="group.code"
              :placeholder="$t('m.Group_Code')"
              class="title-input"
              minlength="6"
              maxlength="6"
              show-word-limit
            >
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :md="8" :xs="24">
          <el-form-item :label="$t('m.Visible')" required prop="visible">
            <el-switch
              v-model="group.visible"
              :active-text="$t('m.Group_Visible')"
              :inactive-text="$t('m.Group_Not_Visible')"
            >
            </el-switch>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item
            class="group-description-item"
            :label="$t('m.Group_Description')"
            required
            prop="description"
          >
            <Editor
              v-model:value="group.description"
              class="group-description-editor"
            ></Editor>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <div class="group-save-actions">
      <el-button
        class="group-save-button"
        type="primary"
        @click="updateGroup"
        :loading="loadingSaveBtn"
        >{{ $t('m.Save') }}</el-button
      >
    </div>
  </el-card>
</template>

<script>
import { defineAsyncComponent } from 'vue';
import api from '@/common/api';
import mMessage from '@/common/message';
import { mapState, mapActions } from 'vuex';
import utils from '@/common/utils';
import VueCropper from '@/components/common/VueCropperAdapter.mjs';
import Avatar from '@/components/common/Avatar.vue';
import 'element-plus/theme-chalk/display.css';
import defaultAvatarImage from '@/assets/default.jpg'
const Editor = defineAsyncComponent(() => import('@/components/admin/Editor.vue'));
export default {
  components: {
    Avatar,
    VueCropper,
    Editor,
  },
  data() {
    return {
      loadingSaveBtn: false,
      loadingUploadBtn: false,
      uploadModalVisible: false,
      preview: {},
      uploadImgSrc: '',
      avatarOption: {
        imgSrc: '',
        size: 0.8,
        outputType: 'png',
      },
      defaultAvatar: defaultAvatarImage,
      rules: {
        name: [
          {
            required: true,
            message: this.$t('m.Group_Name_Check_Required'),
            trigger: 'blur',
          },
          {
            min: 5,
            max: 25,
            message: this.$t('m.Group_Name_Check_Min_Max'),
            trigger: 'blur',
          },
        ],
        shortName: [
          {
            required: true,
            message: this.$t('m.Group_Short_Name_Check_Required'),
            trigger: 'blur',
          },
          {
            min: 5,
            max: 10,
            message: this.$t('m.Group_Short_Name_Check_Min_Max'),
            trigger: 'blur',
          },
        ],
        brief: [
          {
            required: true,
            message: this.$t('m.Group_Brief_Check_Required'),
            trigger: 'blur',
          },
          {
            min: 5,
            max: 50,
            message: this.$t('m.Group_Brief_Check_Min_Max'),
            trigger: 'blur',
          },
        ],
        code: [
          {
            required: true,
            message: this.$t('m.Group_Code_Check_Required'),
            trigger: 'blur',
          },
          {
            min: 6,
            max: 6,
            message: this.$t('m.Group_Code_Check_Min_Max'),
            trigger: 'blur',
          },
        ],
        auth: [
          {
            required: true,
            message: this.$t('m.Group_Auth_Check_Required'),
            trigger: 'blur',
          },
        ],
        description: [
          {
            required: true,
            message: this.$t('m.Group_Description_Check_Required'),
            trigger: 'blur',
          },
          {
            min: 5,
            max: 1000,
            message: this.$t('m.Group_Description_Check_Min_Max'),
            trigger: 'blur',
          },
        ],
      },
    };
  },
  methods: {
    ...mapActions(['changeDomTitle']),
    checkFileType(file) {
      if (!/\.(gif|jpg|jpeg|png|bmp|webp|GIF|JPG|PNG|WEBP)$/.test(file.name)) {
        this.$notify.warning({
          title: this.$t('m.File_type_not_support'),
          message: file.name + this.$t('m.is_incorrect_format_file'),
        });
        return false;
      }
      return true;
    },
    checkFileSize(file) {
      // max size is 2MB
      if (file.size > 2 * 1024 * 1024) {
        this.$notify.warning({
          title: this.$t('m.Exceed_max_size_limit'),
          message: file.name + this.$t('m.File_Exceed_Tips'),
        });
        return false;
      }
      return true;
    },
    handleSelectFile(file) {
      let isOk = this.checkFileType(file) && this.checkFileSize(file);
      if (!isOk) {
        return false;
      }
      let reader = new window.FileReader();
      reader.onload = (e) => {
        this.avatarOption.imgSrc = e.target.result;
      };
      reader.readAsDataURL(file);
      return false;
    },
    realTime(data) {
      this.preview = data;
    },
    rotate(direction) {
      if (direction === 'left') {
        this.$refs.cropper.rotateLeft();
      } else {
        this.$refs.cropper.rotateRight();
      }
    },
    reselect() {
      this.$confirm(this.$t('m.Cancel_Avater_Tips'), 'Tips', {
        confirmButtonText: this.$t('m.OK'),
        cancelButtonText: this.$t('m.Cancel'),
        type: 'warning',
      }).then(() => {
        this.avatarOption.imgSrc = '';
      });
    },
    finishCrop() {
      this.$refs.cropper.getCropData((data) => {
        this.uploadImgSrc = data;
        this.uploadModalVisible = true;
      });
    },
    uploadAvatar() {
      this.$refs.cropper.getCropBlob((blob) => {
        let form = new window.FormData();
        let file = new window.File(
          [blob],
          'avatar.' + this.avatarOption.outputType
        );
        form.append('image', file);
        form.append('gid', this.$route.params.groupID);
        this.loadingUploadBtn = true;
        api.uploadGroupAvatar(form).then(
          (res) => {
            this.loadingUploadBtn = false;
            mMessage.success(this.$t('m.Upload_Avatar_Successfully'));
            this.uploadModalVisible = false;
            this.avatarOption.imgSrc = '';
            this.$store.dispatch('setGroup', res.data.data);
          },
          () => {
            this.loadingUploadBtn = false;
          }
        );
      });
    },
    updateGroup() {
      this.$refs['formGroup'].validate((valid) => {
        if (valid) {
          this.loadingSaveBtn = true;
          let updateData = utils.filterEmptyValue(
            Object.assign({}, this.group)
          );
          api.updateGroup(updateData).then(
            (res) => {
              mMessage.success(this.$t('m.Update_Successfully'));
              this.$store.dispatch('getGroup').then((res) => {
                this.changeDomTitle({ title: res.data.data.name });
              });
              this.loadingSaveBtn = false;
            },
            (_) => {
              this.loadingSaveBtn = false;
            }
          );
        }
      });
    },
  },
  computed: {
    ...mapState({
      group: (state) => state.group.group,
    }),
    avatar() {
      return this.$store.getters.group.avatar;
    },
    previewStyle() {
      return {
        width: this.preview.w + 'px',
        height: this.preview.h + 'px',
        overflow: 'hidden',
      };
    },
  },
};
</script>

<style scoped>
.section-title {
  font-size: 21px;
  font-weight: 500;
  padding-top: 10px;
  padding-bottom: 20px;
  line-height: 30px;
  text-align: center;
}
.section-main {
  text-align: center;
  margin-bottom: 20px;
}

.profile-section-title {
  padding-bottom: 32px;
}

.group-profile-form :deep(.el-form-item) {
  margin-bottom: 37px;
}

.group-profile-form :deep(.el-form-item__label) {
  margin-bottom: 13px;
  padding: 0;
  line-height: 22px;
}

.group-profile-form :deep(.el-input__wrapper),
.group-profile-form :deep(.el-select__wrapper) {
  min-height: 40px;
}

.group-profile-form :deep(.el-input__inner) {
  height: 38px;
  line-height: 38px;
}

.group-profile-form :deep(.group-auth-select) {
  width: 217px;
}

.group-profile-form :deep(.group-description-item) {
  margin-bottom: 18px;
}

.group-description-editor :deep(.md-editor) {
  overflow: hidden;
  border-radius: 4px;
  box-shadow: 0 0 10px rgba(31, 45, 61, 0.08);
}

.group-save-actions {
  margin-top: 14px;
  text-align: center;
}

.group-save-button {
  min-width: 70px;
  min-height: 40px;
}

.avatar-upload-wrap {
  display: flex;
  width: 100%;
  margin-top: 24px;
  justify-content: center;
}

:deep(.upload-container) {
  width: min(320px, 100%);
}

:deep(.upload-container .el-upload) {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 100%;
}
:deep(.upload-container .el-upload:hover) {
  border-color: #409eff;
}
.inline {
  display: inline-block;
}
.cropper-btn {
  margin: 10px 0;
}
.copper-img {
  width: 400px;
  height: 300px;
}

.cropper-main {
  flex: none;
  width: min(400px, 100%);
  height: 300px;
}
.section-main .cropper-preview {
  flex: none;
  text-align: center;
  box-shadow: 0 0 1px 0;
}
@media screen and (max-width: 1080px) {
  .section-main .cropper-preview {
    margin: 0 auto;
  }
}
@media screen and (max-width: 768px) {
  .group-profile-form :deep(.group-auth-select) {
    width: 100%;
  }
}
.upload-modal .notice {
  font-size: 16px;
  display: inline-block;
  vertical-align: top;
  padding: 10px;
}
:deep(.el-dialog__body) {
  padding: 0;
}
:deep(.el-upload-dragger) {
  width: 100%;
  height: 100%;
}
.upload-modal img {
  box-shadow: 0 0 1px 0;
  border-radius: 50%;
  width: 250px;
  height: 250px;
}
</style>
