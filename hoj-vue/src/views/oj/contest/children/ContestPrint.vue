<template>
  <el-card shadow="always">
    <template #header>
      <div>
        <span class="panel-title">{{ $t('m.Print') }}</span>
      </div>
    </template>
    <div class="print-tips">
      <el-alert
        :title="$t('m.Print_Title')"
        type="warning"
        :closable="false"
        center
        :description="$t('m.Print_tips')"
        show-icon
      >
      </el-alert>
    </div>
    <el-form
      :model="ruleForm"
      :rules="rules"
      ref="ruleForm"
      label-width="100px"
      class="demo-ruleForm"
    >
      <el-form-item :label="$t('m.Content')" prop="content">
        <el-input
          type="textarea"
          v-model="ruleForm.content"
          :rows="20"
        ></el-input>
      </el-form-item>
      <el-form-item style="text-align: center;">
        <el-button type="primary" @click="onSubmit">{{
          $t('m.Submit')
        }}</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script>
import mMessage from '@/common/message';
import api from '@/common/api';
export default {
  data() {
    return {
      ruleForm: {
        content: '',
      },
      rules: {
        content: [{ required: true, trigger: 'blur' }],
      },
      contestID: null,
    };
  },
  mounted() {
    this.contestID = this.$route.params.contestID;
  },
  methods: {
    onSubmit() {
      if (!this.ruleForm.content) {
        mMessage.error(this.$t('m.Content_cannot_be_empty'));
        return;
      }
      if (this.ruleForm.content.length < 50) {
        mMessage.error(
          this.$t('m.The_number_of_content_cannot_be_less_than_50')
        );
        return;
      }
      let data = {
        cid: this.contestID,
        content: this.ruleForm.content,
      };
      api.submitPrintText(data).then((res) => {
        this.$confirm(
          this.$t('m.Success_submit_tips'),
          this.$t('m.Submit_code_successfully'),
          {
            type: 'success',
            center: true,
            confirmButtonText: this.$t('m.OK'),
          }
        );
      });
    },
  },
};
</script>

<style scoped>
.print-tips {
  margin-left: 50px;
  padding: 30px;
  padding-top: 0px;
}
</style>
