<template>
  <div class="reset-password-panel">
    <el-form :model="formResetPassword" :rules="rules" ref="formResetPassword">
      <el-form-item prop="email">
        <el-input
          v-model="formResetPassword.email"
          :prefix-icon="legacyElementIcons['el-icon-message']"
          :placeholder="$t('m.Reset_Password_Email')"
        >
        </el-input>
      </el-form-item>
      <el-form-item prop="captcha">
        <div id="captcha">
          <div id="captchaCode">
            <el-input
              v-model="formResetPassword.captcha"
              :prefix-icon="legacyElementIcons['el-icon-s-check']"
              :placeholder="$t('m.Reset_Password_Captcha')"
            ></el-input>
          </div>
          <div id="captchaImg">
            <el-tooltip content="Click to refresh" placement="top">
              <img :src="captchaSrc" @click="getCaptcha" />
            </el-tooltip>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <div class="footer">
      <el-button
        type="primary"
        @click="handleResetPwd"
        :loading="btnResetPwdLoading"
        :disabled="btnResetPwdDisabled"
      >
        {{ resetText }}
      </el-button>
      <el-link type="primary" @click="switchMode('Login')">{{
        $t('m.Remember_Passowrd_To_Login')
      }}</el-link>
    </div>
  </div>
</template>
<script>
import { mapGetters, mapActions } from 'vuex';
import api from '@/common/api';
import mMessage from '@/common/message';
export default {
  data() {
    const CheckEmailNotExist = (rule, value, callback) => {
      api.checkUsernameOrEmail(undefined, value).then(
        (res) => {
          if (res.data.data.email === false) {
            callback(new Error(this.$t('m.The_email_does_not_exists')));
          } else {
            callback();
          }
        },
        (_) => callback()
      );
    };
    return {
      resetText: 'Send Password Reset Email',
      btnResetPwdLoading: false,
      btnResetPwdDisabled: false,
      captchaSrc: '',
      formResetPassword: {
        captcha: '',
        email: '',
        captchaKey: '',
      },
      rules: {
        captcha: [
          {
            required: true,
            message: this.$t('m.Code_Check_Required'),
            trigger: 'blur',
            min: 1,
            max: 8,
          },
        ],
        email: [
          {
            required: true,
            message: this.$t('m.Email_Check_Required'),
            type: 'email',
            trigger: 'blur',
          },
          { validator: CheckEmailNotExist, trigger: 'blur' },
        ],
      },
    };
  },
  mounted() {
    this.resetText = this.$t('m.Send_Password_Reset_Email');
    this.getCaptcha();
  },
  methods: {
    ...mapActions(['changeModalStatus', 'changeResetTimeOut', 'startTimeOut']),
    getCaptcha() {
      api.getCaptcha().then((res) => {
        this.captchaSrc = res.data.data.img;
        this.formResetPassword.captchaKey = res.data.data.captchaKey;
      });
    },
    switchMode(mode) {
      this.changeModalStatus({
        mode,
        visible: true,
      });
    },
    countDown() {
      let i = this.time;
      this.resetText = i + 's, ' + this.$t('m.Waiting_Can_Resend_Email');
      if (i == 0) {
        this.btnResetPwdDisabled = false;
        this.resetText = this.$t('m.Send_Password_Reset_Email');
        return;
      }
      setTimeout(() => {
        this.countDown();
      }, 1000);
    },
    handleResetPwd() {
      this.$refs['formResetPassword'].validate((valid) => {
        if (valid) {
          this.resetText = 'Waiting...';
          mMessage.info(this.$t('m.The_system_is_processing'));
          this.btnResetPwdLoading = true;
          this.btnResetPwdDisabled = true;
          api.applyResetPassword(this.formResetPassword).then(
            (res) => {
              mMessage.message(
                'success',
                this.$t('m.ResetPwd_Send_Email_Msg'),
                10000
              );
              this.countDown();
              this.startTimeOut({ name: 'resetTimeOut' });
              this.btnResetPwdLoading = false;
              this.formResetPassword.captcha = '';
              this.formResetPassword.captchaKey = '';
              this.getCaptcha();
            },
            (err) => {
              this.formResetPassword.captcha = '';
              this.formResetPassword.captchaKey = '';
              this.btnResetPwdLoading = false;
              this.btnResetPwdDisabled = false;
              this.resetText = this.$t('m.Send_Password_Reset_Email');
              this.getCaptcha();
            }
          );
        }
      });
    },
  },
  computed: {
    ...mapGetters(['resetTimeOut', 'modalStatus']),
    time: {
      get() {
        return this.resetTimeOut;
      },
      set(value) {
        this.changeResetTimeOut({ time: value });
      },
    },
  },
  created() {
    if (this.time != 90 && this.time != 0) {
      this.btnResetPwdDisabled = true;
      this.countDown();
    }
  },
};
</script>
<style scoped>
#captcha {
  align-items: center;
  display: flex;
  flex-wrap: nowrap;
  justify-content: space-between;
  width: 100%;
  height: 40px;
}
#captchaCode {
  flex: 1 1 auto;
  min-width: 0;
}
#captchaImg {
  align-items: center;
  display: flex;
  flex: 0 0 96px;
  height: 40px;
  justify-content: center;
  margin-left: 10px;
  padding: 0;
}
#captchaImg img {
  cursor: pointer;
  display: block;
  max-height: 40px;
  max-width: 96px;
}

.footer {
  overflow: auto;
  margin-top: 22px;
  margin-bottom: 0;
  text-align: center;
}
:deep(.el-form-item) {
  margin-bottom: 22px;
}
:deep(.el-input__wrapper) {
  min-height: 40px;
}
:deep(.footer .el-button--primary) {
  height: 40px;
  margin: 0 0 20px 0;
  padding: 0 20px;
  width: 100%;
}

:deep(.el-form-item__content) {
  margin-left: 0px !important;
}
</style>
