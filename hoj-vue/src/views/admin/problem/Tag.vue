<template>
  <div class="tag-management-page">
    <el-card class="tag-overview-card">
      <template #header>
        <div>
          <span class="panel-title home-title">{{ $t('m.Admin_Tag') }}</span>
          <div class="tag-filter">
            <span>
              <el-button
                type="primary"
                size="small"
                class="tag-filter-button"
                @click="openTagDialog('add', null)"
                :icon="legacyElementIcons['el-icon-plus']"
                >{{ $t('m.Add_Tag') }}
              </el-button>
            </span>
            <span>
              <el-button
                type="warning"
                size="small"
                class="tag-filter-button"
                @click="openTagClassificationDialog('add', null)"
                :icon="legacyElementIcons['el-icon-plus']"
                >{{ $t('m.Add_Tag_Classification') }}
              </el-button>
            </span>
            <span>
              <el-select
                v-model="tagOj"
                @change="init"
                size="small"
                class="tag-oj-select"
              >
                <el-option :label="$t('m.My_OJ')" :value="'ME'"></el-option>
                <el-option
                  :label="remoteOj.name"
                  :key="index"
                  :value="remoteOj.key"
                  v-for="(remoteOj, index) in REMOTE_OJ"
                ></el-option>
              </el-select>
            </span>
          </div>
        </div>
      </template>
      <h3 class="tag-tips">{{ $t('m.Tag_Tips') }}</h3>
    </el-card>
    <div class="tag-classification-list" v-loading="getTagListLoading">
      <el-row :gutter="20">
          <el-col v-for="(tagsAndClassification,index)  in tagsAndClassificationList"  
            :key="index" :md="8" :xs="24">
            <el-card class="tag-classification-card">
              <el-collapse v-model="activeTagClassificationIdList">
                  <el-collapse-item :name="tagsAndClassification.classification == null?-1:tagsAndClassification.classification.id">
                      <template #title>
                        <span class="tag-classification-name">{{ tagsAndClassification.classification!=null?
                          tagsAndClassification.classification.name:$t('m.Unclassified')
                        }}
                        </span>
                        <span class="tag-classification-action"
                          v-if="tagsAndClassification.classification!=null">
                          <el-button type="primary" 
                            :icon="legacyElementIcons['el-icon-edit']"
                            class="tag-classification-action-button"
                            circle
                            size="small"
                            @click.stop="openTagClassificationDialog('update',tagsAndClassification.classification)"
                          ></el-button>
                        </span>
                        <span class="tag-classification-action"
                          v-if="tagsAndClassification.classification!=null">
                          <el-button type="danger" 
                            :icon="legacyElementIcons['el-icon-delete']"
                            class="tag-classification-action-button"
                            circle
                            size="small"
                            @click.stop="deleteTagClassification(tagsAndClassification.classification)"
                          ></el-button>
                        </span>
                      </template>
                      <el-button
                        class="button-new-tag"
                        size="small"
                        @click="openTagDialog('add', null,tagsAndClassification.classification)"
                        >+ {{ $t('m.Add_Tag') }}</el-button
                      >
                      <el-tag
                        :key="index"
                        v-for="(tag, index) in tagsAndClassification.tagList"
                        closable
                        :color="tag.color ? tag.color : '#409eff'"
                        effect="dark"
                        :disable-transitions="false"
                        @close="deleteTag(tag)"
                        @click="openTagDialog('update', tag)"
                        class="tag"
                      >
                        {{ tag.name }}
                      </el-tag>
                  </el-collapse-item>
              </el-collapse>
            </el-card>
          </el-col>
      </el-row>
    </div>

    <el-dialog
      :title="$t('m.' + upsertTagTitle)"
      width="350px"
      class="tag-upsert-dialog"
      v-model="addTagDialogVisible"
      :close-on-click-modal="false"
    >
      <el-form class="tag-upsert-form">
        <el-form-item :label="$t('m.Tag_Name')" required>
          <el-input v-model="tag.name" size="small"></el-input>
        </el-form-item>
        <el-form-item :label="$t('m.Tag_Color')" required>
          <el-color-picker
            v-model="tag.color"
            :class="{ 'tag-color-picker-empty': !tag.color }"
          ></el-color-picker>
        </el-form-item>

        <el-form-item :label="$t('m.Tag_Attribution')" required="">
          <el-select v-model="tag.oj" size="small" style="width: 150px;">
            <el-option :label="$t('m.My_OJ')" :value="'ME'"></el-option>
            <el-option
              :label="remoteOj.name"
              :key="index"
              :value="remoteOj.key"
              v-for="(remoteOj, index) in REMOTE_OJ"
            ></el-option>
          </el-select>
        </el-form-item>

        <el-form-item :label="$t('m.Tag_Classification')" required="">
          <el-select
            v-model="tag.tcid"
            size="small"
            style="width: 150px;"
            :placeholder="$t('m.Unclassified')"
          >
            <el-option
              :label="classification.name"
              :key="index"
              :value="classification.id"
              v-for="(classification, index) in chooseTagClassificationList"
            ></el-option>
            <el-option :label="$t('m.Unclassified')" :value="null"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item style="text-align:center">
          <el-button
            type="primary"
            @click="upsertTag"
            :loading="upsertTagLoading"
            >{{ $t('m.' + upsertTagBtn) }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-dialog>

     <el-dialog
      :title="$t('m.' + upsertTagClassificationTitle)"
      width="350px"
      class="tag-classification-upsert-dialog"
      v-model="addTagClassificationDialogVisible"
      :close-on-click-modal="false"
    >
      <el-form class="tag-classification-upsert-form">
        <el-form-item :label="$t('m.Tag_Classification_Name')" required>
          <el-input v-model="tagClassification.name" size="small"></el-input>
        </el-form-item>
        <el-form-item :label="$t('m.Tag_Classification_Attribution')" required="">
          <el-select v-model="tagClassification.oj" size="small" style="width: 150px;" :disabled="true">
            <el-option :label="$t('m.My_OJ')" :value="'ME'"></el-option>
            <el-option
              :label="remoteOj.name"
              :key="index"
              :value="remoteOj.key"
              v-for="(remoteOj, index) in REMOTE_OJ"
            ></el-option>
          </el-select>
        </el-form-item>

        <el-form-item :label="$t('m.Tag_Classification_Rank')">
          <el-input-number
              v-model="tagClassification.rank"
              :min="0"
              :max="2147483647"
            ></el-input-number>
        </el-form-item>

        <el-form-item style="text-align:center">
          <el-button
            type="primary"
            @click="upsertTagClassification"
            :loading="upsertTagClassificationLoading"
            >{{ $t('m.' + upsertTagBtn) }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>
<script>
import myMessage from '@/common/message';
import api from '@/common/api';
import { REMOTE_OJ } from '@/common/constants';
export default {
  data() {
    return {
      tagOj: 'ME',
      REMOTE_OJ: {},
      getTagListLoading: false,
      tagsAndClassificationList: [],
      tagClassificationList :[],
      chooseTagClassificationList:[],
      addTagDialogVisible: false,
      upsertTagTitle: 'Add_Tag',
      upsertTagBtn: 'To_Add',
      upsertTagLoading: false,
      tag: {
        id: null,
        name: null,
        color: null,
        oj: 'ME',
        tcid: null,
      },
      tagClassification:{
        id:null,
        name:null,
        rank:0,
        oj:'ME'
      },
      addTagClassificationDialogVisible: false,
      upsertTagClassificationTitle: 'Add_Tag_Classification',
      upsertTagClassificationLoading:false,
      activeTagClassificationIdList:[-1]
    };
  },
  mounted() {
    this.REMOTE_OJ = Object.assign({}, REMOTE_OJ);
    this.init();
  },
  methods: {
    init(){
      this.getTagClassification();
      this.getProblemTagsAndClassification();
    },
    getProblemTagsAndClassification() {
      this.getTagListLoading = true;
      api.getProblemTagsAndClassification(this.tagOj).then(
        (res) => {
          this.tagsAndClassificationList = res.data.data;
          this.getTagListLoading = false;
        },
        (err) => {
          this.getTagListLoading = false;
        }
      );
    },

    deleteTag(tag) {
      this.$confirm(this.$t('m.Delete_Tag_Tips'), 'Tips', {
        type: 'warning',
      }).then(
        () => {
          api
            .admin_deleteTag(tag.id)
            .then((res) => {
              myMessage.success(this.$t('m.Delete_successfully'));
              this.getProblemTagsAndClassification();
            })
            .catch(() => {});
        },
        () => {}
      );
    },
    openTagDialog(action, tag, classification = null) {
      if (action == 'add') {
        this.upsertTagTitle = 'Add_Tag';
        this.upsertTagBtn = 'To_Add';
        this.tag = {
          id: null,
          name: null,
          color: null,
          oj: this.tagOj,
          tcid: classification == null? null: classification.id
        };
      } else {
        this.upsertTagTitle = 'Update_Tag';
        this.upsertTagBtn = 'To_Update';
        this.tag = Object.assign({}, tag);
      }
      this.addTagDialogVisible = true;
    },

     upsertTag() {
      if (this.tag.id) {
        this.upsertTagLoading = true;
        api.admin_updateTag(this.tag).then(
          (res) => {
            this.upsertTagLoading = false;
            myMessage.success(this.$t('m.Update_Successfully'));
            this.addTagDialogVisible = false;
            this.getProblemTagsAndClassification();
          },
          (err) => {
            this.upsertTagLoading = false;
          }
        );
      } else {
        this.upsertTagLoading = true;
        api.admin_addTag(this.tag).then(
          (res) => {
            this.upsertTagLoading = false;
            myMessage.success(this.$t('m.Add_Successfully'));
            this.addTagDialogVisible = false;
            this.getProblemTagsAndClassification();
          },
          (err) => {
            this.upsertTagLoading = false;
          }
        );
      }
    },

    getTagClassification(){
      api.admin_getTagClassification(this.tagOj).then((res)=>{
        this.tagClassificationList = res.data.data;
        this.chooseTagClassificationList = this.tagClassificationList.filter(c=>c.oj == this.tag.oj);
      })
    },

    upsertTagClassification() {
      if (this.tagClassification.id) {
        this.upsertTagClassificationLoading = true;
        api.admin_updateTagClassification(this.tagClassification).then(
          (res) => {
            this.upsertTagClassificationLoading = false;
            myMessage.success(this.$t('m.Update_Successfully'));
            this.addTagClassificationDialogVisible = false;
            this.getProblemTagsAndClassification();
          },
          (err) => {
            this.upsertTagClassificationLoading = false;
          }
        );
      } else {
        this.upsertTagClassificationLoading = true;
        api.admin_addTagClassification(this.tagClassification).then(
          (res) => {
            this.upsertTagClassificationLoading = false;
            myMessage.success(this.$t('m.Add_Successfully'));
            this.tagsAndClassificationList.unshift(
              {
                classification :res.data.data,
                tagList:[]
              });
            this.chooseTagClassificationList.push(res.data.data);
            this.addTagClassificationDialogVisible = false;
          },
          (err) => {
            this.upsertTagClassificationLoading = false;
          }
        );
      }
    },

    deleteTagClassification(tagClassification) {
      this.$confirm(this.$t('m.Delete_Tag_Classification_Tips'), 'Tips', {
        type: 'warning',
      }).then(
        () => {
          api
            .admin_deleteTagClassification(tagClassification.id)
            .then((res) => {
              myMessage.success(this.$t('m.Delete_successfully'));
              this.getProblemTagsAndClassification();
            })
            .catch(() => {});
        },
        () => {}
      );
    },

    openTagClassificationDialog(action, tagClassification) {
      if (action == 'add') {
        this.upsertTagClassificationTitle = 'Add_Tag_Classification';
        this.upsertTagBtn = 'To_Add';
        this.tagClassification = {
          id: null,
          name: null,
          rank:0,
          oj: this.tagOj,
        };
      } else {
        this.upsertTagClassificationTitle = 'Update_Tag_Classification';
        this.upsertTagBtn= 'To_Update';
        this.tagClassification = Object.assign({}, tagClassification);
      }
      this.addTagClassificationDialogVisible = true;
    },

  },
  watch:{
    'tag.oj'(newVal){
      this.chooseTagClassificationList = this.tagClassificationList.filter(c=>c.oj == newVal);
      this.tag.tcid = null;
    }
  }
};
</script>
<style scoped>
.tag-filter {
  box-sizing: border-box;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  height: 42px;
  margin-top: 10px;
  padding-top: 10px;
}
.tag-filter > span {
  display: inline-flex;
  align-items: center;
}
.tag-filter-button {
  height: 32px;
  min-height: 32px;
  padding: 9px 15px;
}
.tag-filter > span:first-child .tag-filter-button {
  width: 97px;
}
.tag-filter > span:nth-child(2) .tag-filter-button {
  width: 121px;
}
.tag-oj-select {
  width: 150px;
}
.tag-oj-select :deep(.el-select__wrapper) {
  box-sizing: border-box;
  font-size: 13px;
  height: 32px;
  min-height: 32px;
  padding-left: 15px;
}
.tag-tips {
  line-height: 1.5;
  margin: -5px;
}
.tag-classification-card {
  margin-top: 15px;
}
.tag-classification-action {
  display: inline-flex;
  margin-left: 10px;
  transform: translateY(2px);
}
.tag-classification-action-button {
  box-sizing: border-box;
  height: 28px;
  min-height: 28px;
  min-width: 28px;
  padding: 7px;
  width: 28px;
}
.el-tag {
  margin-left: 10px;
  margin-top: 10px;
}
.tag {
  cursor: pointer;
}
.button-new-tag {
  margin-left: 10px;
  height: 32px;
  line-height: 30px;
  padding-top: 0;
  padding-bottom: 0;
  margin-top: 10px;
}

:deep(.el-collapse-item__header){
  --el-collapse-header-height: 40px;
  font-weight: bolder !important;
  height:40px !important;
  min-height: 40px !important;
  line-height: 40px !important;
  padding-right: 0;
  font-size: 15px !important;
}
:deep(.el-collapse-item__content) {
  padding-bottom: 10px !important;
}
:deep(.el-collapse-item__arrow) {
  margin-right: 8px;
}

:global(.tag-upsert-dialog.el-dialog) {
  --el-dialog-padding-primary: 0px;
  padding: 0;
}

:global(.tag-upsert-dialog .el-dialog__header) {
  box-sizing: border-box;
  height: 54px;
  margin: 0;
  padding: 20px 20px 10px;
}

:global(.tag-upsert-dialog .el-dialog__title) {
  color: #303133;
  font-size: 18px;
  font-weight: 400;
  line-height: 24px;
}

:global(.tag-upsert-dialog .el-dialog__headerbtn) {
  height: 24px;
  right: 20px;
  top: 20px;
  width: 16px;
}

:global(.tag-upsert-dialog .el-dialog__body) {
  box-sizing: border-box;
  color: #606266;
  font-size: 14px;
  padding: 30px 20px;
  word-break: break-all;
}

:global(.tag-upsert-form) {
  width: 310px;
}

:global(.tag-upsert-form .el-form-item) {
  display: block;
  margin-bottom: 22px;
}

:global(.tag-upsert-form .el-form-item::after),
:global(.tag-upsert-form .el-form-item::before) {
  content: "";
  display: table;
}

:global(.tag-upsert-form .el-form-item::after) {
  clear: both;
}

:global(.tag-upsert-form .el-form-item__label) {
  display: block;
  float: left;
  font-size: 14px;
  height: 40px;
  line-height: 40px;
  padding: 0 12px 0 0;
}

:global(.tag-upsert-form .el-form-item__content) {
  display: block;
  font-size: 14px;
  line-height: 40px;
  min-height: 40px;
  position: relative;
}

:global(.tag-upsert-form .el-form-item:nth-child(1) .el-form-item__content) {
  min-height: 81px;
}

:global(.tag-upsert-form .el-form-item:nth-child(2) .el-form-item__content) {
  min-height: 55px;
}

:global(.tag-upsert-form .el-form-item:nth-child(3) .el-form-item__content),
:global(.tag-upsert-form .el-form-item:nth-child(4) .el-form-item__content) {
  min-height: 41px;
}

:global(.tag-upsert-form .el-form-item:last-child .el-form-item__content) {
  height: 40px;
  min-height: 40px;
}

:global(.tag-upsert-form .el-input) {
  line-height: 40px;
}

:global(.tag-upsert-form .el-input--small .el-input__wrapper),
:global(.tag-upsert-form .el-select--small .el-select__wrapper) {
  box-sizing: border-box;
  font-size: 13px;
  height: 32px;
  min-height: 32px;
}

:global(.tag-upsert-form .el-input--small .el-input__wrapper) {
  padding: 1px 15px;
}

:global(.tag-upsert-form .el-select--small .el-select__wrapper) {
  padding-left: 15px;
}

:global(.tag-upsert-form .el-color-picker),
:global(.tag-upsert-form .el-color-picker__trigger) {
  height: 40px;
  width: 40px;
}

:global(.tag-upsert-form .el-color-picker__trigger) {
  padding: 4px;
}

:global(.tag-upsert-form .tag-color-picker-empty .is-icon-arrow-down) {
  display: none;
}

:global(.tag-upsert-form .tag-color-picker-empty .is-icon-close) {
  color: #909399;
  display: inline-flex !important;
}

:global(.tag-upsert-form .el-button--primary) {
  box-sizing: border-box;
  height: 40px;
  padding: 12px 20px;
}

:global(.tag-upsert-form .el-form-item:last-child .el-button--primary) {
  vertical-align: top;
}

:global(.tag-classification-upsert-dialog.el-dialog) {
  --el-dialog-padding-primary: 0px;
  padding: 0;
}

:global(.tag-classification-upsert-dialog .el-dialog__header) {
  box-sizing: border-box;
  height: 54px;
  margin: 0;
  padding: 20px 20px 10px;
}

:global(.tag-classification-upsert-dialog .el-dialog__title) {
  color: #303133;
  font-size: 18px;
  font-weight: 400;
  line-height: 24px;
}

:global(.tag-classification-upsert-dialog .el-dialog__headerbtn) {
  height: 24px;
  right: 20px;
  top: 20px;
  width: 16px;
}

:global(.tag-classification-upsert-dialog .el-dialog__body) {
  box-sizing: border-box;
  color: #606266;
  font-size: 14px;
  padding: 30px 20px;
  word-break: break-all;
}

:global(.tag-classification-upsert-form) {
  width: 310px;
}

:global(.tag-classification-upsert-form .el-form-item) {
  display: block;
  margin-bottom: 22px;
}

:global(.tag-classification-upsert-form .el-form-item::after),
:global(.tag-classification-upsert-form .el-form-item::before) {
  content: "";
  display: table;
}

:global(.tag-classification-upsert-form .el-form-item::after) {
  clear: both;
}

:global(.tag-classification-upsert-form .el-form-item__label) {
  display: block;
  float: left;
  font-size: 14px;
  height: 40px;
  line-height: 40px;
  padding: 0 12px 0 0;
}

:global(.tag-classification-upsert-form .el-form-item__content) {
  display: block;
  font-size: 14px;
  line-height: 40px;
  min-height: 40px;
  position: relative;
}

:global(.tag-classification-upsert-form .el-form-item:nth-child(1) .el-form-item__content) {
  height: 81px;
  min-height: 81px;
}

:global(.tag-classification-upsert-form .el-form-item:nth-child(2) .el-form-item__content) {
  min-height: 41px;
}

:global(.tag-classification-upsert-form .el-form-item:nth-child(3) .el-form-item__content) {
  height: 40px;
  min-height: 40px;
}

:global(.tag-classification-upsert-form .el-form-item:last-child .el-form-item__content) {
  height: 40px;
  min-height: 40px;
}

:global(.tag-classification-upsert-form .el-input) {
  line-height: 40px;
}

:global(.tag-classification-upsert-form .el-input--small),
:global(.tag-classification-upsert-form .el-select) {
  height: 40px;
}

:global(.tag-classification-upsert-form .el-input--small .el-input__wrapper),
:global(.tag-classification-upsert-form .el-select--small .el-select__wrapper) {
  box-sizing: border-box;
  font-size: 13px;
  height: 32px;
  min-height: 32px;
}

:global(.tag-classification-upsert-form .el-input--small .el-input__wrapper) {
  margin-top: 4px;
  padding: 1px 15px;
}

:global(.tag-classification-upsert-form .el-select) {
  display: inline-block;
  line-height: 40px;
  position: relative;
  top: 1px;
  vertical-align: top;
}

:global(.tag-classification-upsert-form .el-select--small .el-select__wrapper) {
  margin-top: 4px;
  padding-left: 15px;
}

:global(.tag-classification-upsert-form .el-input-number) {
  display: inline-flex;
  height: 40px;
  line-height: 38px;
  vertical-align: top;
  width: 180px;
}

:global(.tag-classification-upsert-form .el-input-number .el-input),
:global(.tag-classification-upsert-form .el-input-number .el-input__wrapper) {
  height: 40px;
  min-height: 40px;
  width: 180px;
}

:global(.tag-classification-upsert-form .el-input-number__decrease),
:global(.tag-classification-upsert-form .el-input-number__increase) {
  height: 38px;
  line-height: 38px;
  top: 1px;
  width: 40px;
}

:global(.tag-classification-upsert-form .el-button--primary) {
  box-sizing: border-box;
  height: 40px;
  padding: 12px 20px;
}

:global(.tag-classification-upsert-form .el-form-item:last-child .el-button--primary) {
  vertical-align: top;
}
</style>
