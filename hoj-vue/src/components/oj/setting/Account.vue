<template>
  <div class="setting-main">
    <el-row :gutter="20">
      <el-col :sm="24" :md="10" :lg="10">
        <div class="left">
          <p class="section-title">{{ $t('m.Change_Password') }}</p>
          <el-form
            class="setting-content"
            ref="formPassword"
            :model="formPassword"
            :rules="rulePassword"
          >
            <el-form-item :label="$t('m.Old_Password')" prop="oldPassword">
              <el-input v-model="formPassword.oldPassword" type="password" />
            </el-form-item>
            <el-form-item :label="$t('m.New_Password')" prop="newPassword">
              <el-input v-model="formPassword.newPassword" type="password" />
            </el-form-item>
            <el-form-item
              :label="$t('m.Confirm_New_Password')"
              prop="againPassword"
            >
              <el-input v-model="formPassword.againPassword" type="password" />
            </el-form-item>
          </el-form>
          <el-popover
            placement="top"
            width="350"
            v-model="visible.passwordSlideBlock"
            trigger="click"
          >
            <template #reference>
              <el-button
                type="primary"
                  :loading="loading.btnPassword"
                :disabled="disabled.btnPassword"
                >{{ $t('m.Update_Password') }}</el-button
              >
            </template>
            <slide-verify
              :l="42"
              :r="10"
              :w="325"
              :h="100"
              :accuracy="3"
              :imgs="imgs"
              @success="changePassword"
              @again="onAgain('password')"
              :slider-text="$t('m.Slide_Verify')"
              ref="passwordSlideBlock"
              v-show="!verify.passwordSuccess"
            >
            </slide-verify>
            <el-alert
              :title="$t('m.Slide_Verify_Success')"
              type="success"
              :description="verify.passwordMsg"
              v-show="verify.passwordSuccess"
              :center="true"
              :closable="false"
              show-icon
            >
            </el-alert>
          </el-popover>
        </div>
        <el-alert
          v-show="visible.passwordAlert.show"
          :title="visible.passwordAlert.title"
          :type="visible.passwordAlert.type"
          :description="visible.passwordAlert.description"
          :closable="false"
          effect="dark"
          style="margin-top:15px"
          show-icon
        >
        </el-alert>
      </el-col>
      <el-col :md="4" :lg="4">
        <div class="separator hidden-md-and-down"></div>
        <p></p>
      </el-col>
      <el-col :sm="24" :md="10" :lg="10">
        <div class="right">
          <p class="section-title">{{ $t('m.Change_Email') }}</p>
          <el-form
            class="setting-content"
            ref="formEmail"
            :model="formEmail"
            :rules="ruleEmail"
          >
            <el-form-item :label="$t('m.Current_Password')" prop="password">
              <el-input v-model="formEmail.password" type="password" />
            </el-form-item>
            <el-form-item :label="$t('m.Old_Email')">
              <el-input v-model="formEmail.oldEmail" disabled />
            </el-form-item>
            <el-form-item :label="$t('m.New_Email')" prop="newEmail">
              <el-input
                v-model="formEmail.newEmail"
                class="email-captcha-input"
              >
                <template #append>
                  <el-button
                  @click="getChangeEmailCode"
                  :loading="loading.btnSendEmail"
                  :icon="legacyElementIcons['el-icon-message']">
                  {{$t('m.Get_Captcha')}}
                </el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item :label="$t('m.Captcha')" prop="code">
              <el-input v-model="formEmail.code" />
            </el-form-item>
          </el-form>
          <el-popover
            placement="top"
            width="350"
            v-model="visible.emailSlideBlock"
            trigger="click"
          >
            <template #reference>
              <el-button
                type="primary"
                :loading="loading.btnEmail"
                :disabled="disabled.btnEmail"
                >{{ $t('m.Update_Email') }}</el-button
              >
            </template>
            <slide-verify
              :l="42"
              :r="10"
              :w="325"
              :h="100"
              :accuracy="3"
              :imgs="imgs"
              @success="changeEmail"
              @again="onAgain('email')"
              :slider-text="$t('m.Slide_Verify')"
              ref="emailSlideBlock"
              v-show="!verify.emailSuccess"
            >
            </slide-verify>
            <el-alert
              :title="$t('m.Slide_Verify_Success')"
              type="success"
              :description="verify.emailMsg"
              v-show="verify.emailSuccess"
              :center="true"
              :closable="false"
              show-icon
            >
            </el-alert>
          </el-popover>
        </div>
        <el-alert
          v-show="visible.emailAlert.show"
          :title="visible.emailAlert.title"
          :type="visible.emailAlert.type"
          :description="visible.emailAlert.description"
          :closable="false"
          effect="dark"
          style="margin-top:15px"
          show-icon
        >
        </el-alert>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import api from '@/common/api';
import myMessage from '@/common/message';
import 'element-plus/theme-chalk/display.css';
export default {
  data() {
    const oldPasswordCheck = [
      {
        required: true,
        trigger: 'blur',
        message: this.$t('m.The_current_password_cannot_be_empty'),
      },
      {
        trigger: 'blur',
        min: 6,
        max: 20,
        message: this.$t('m.Password_Check_Between'),
      },
    ];
    const CheckAgainPassword = (rule, value, callback) => {
      if (value !== this.formPassword.newPassword) {
        return callback(new Error(this.$t('m.Password_does_not_match')));
      }
      callback();
    };
    const CheckNewPassword = (rule, value, callback) => {
      if (this.formPassword.oldPassword !== '') {
        if (this.formPassword.oldPassword === this.formPassword.newPassword) {
          callback(
            new Error(this.$t('m.The_new_password_does_not_change'))
          );
        } else {
          // 对第二个密码框再次验证
          this.$refs.formPassword.validateField('againPassword');
        }
      }
      callback();
    };
    const CheckEmail = (rule, value, callback) => {
      if (this.formEmail.oldEmail !== '') {
        if (this.formEmail.oldEmail === this.formEmail.newEmail) {
          callback(new Error(this.$t('m.The_new_email_does_not_change')));
        }
      }
      callback();
    };
    return {
      loading: {
        btnPassword: false,
        btnEmail: false,
        btnSendEmail: false,
      },
      disabled: {
        btnPassword: false,
        btnEmail: false,
      },
      verify: {
        passwordSuccess: false,
        passwordMsg: '',
        emailSuccess: false,
        emailMsg: '',
      },
      visible: {
        passwordAlert: {
          type: 'success',
          show: false,
          title: '',
          description: '',
        },
        emailAlert: {
          type: 'success',
          show: false,
          title: '',
          description: '',
        },
        passwordSlideBlock: false,
        emailSlideBlock: false,
      },
      formPassword: {
        oldPassword: '',
        newPassword: '',
        againPassword: '',
      },
      formEmail: {
        password: '',
        oldEmail: '',
        newEmail: '',
      },
      rulePassword: {
        oldPassword: oldPasswordCheck,
        newPassword: [
          {
            required: true,
            trigger: 'blur',
            message: this.$t('m.The_new_password_cannot_be_empty'),
          },
          {
            trigger: 'blur',
            min: 6,
            max: 20,
            message: this.$t('m.Password_Check_Between'),
          },
          { validator: CheckNewPassword, trigger: 'blur' },
        ],
        againPassword: [
          {
            required: true,
            trigger: 'blur',
            message: this.$t('m.Password_Again_Check_Required'),
          },
          { validator: CheckAgainPassword, trigger: 'blur' },
        ],
      },
      ruleEmail: {
        password: oldPasswordCheck,
        newEmail: [
          {
            required: true,
            message: this.$t('m.Email_Check_Required'),
            trigger: 'blur',
          },
          {
            type: 'email',
            trigger: 'change',
            message: this.$t('m.Email_Check_Format'),
          },
          { validator: CheckEmail, trigger: 'blur' },
        ],
        code:[
          {
              required: true,
              message: this.$t('m.Code_Check_Required'),
              trigger: 'blur',
          },
        ]
      },
      imgs:[
        "https://picsum.photos/325/100?random=1",
        "https://picsum.photos/325/100?random=2",
        "https://picsum.photos/325/100?random=3",
        "https://picsum.photos/325/100?random=4",
        "https://picsum.photos/325/100?random=5",
        "https://picsum.photos/325/100?random=6",
        "https://picsum.photos/325/100?random=7",
        "https://picsum.photos/325/100?random=8",
        "https://picsum.photos/325/100?random=9",
        "https://picsum.photos/325/100?random=10"
      ]
    };
  },
  mounted() {
    this.formEmail.oldEmail = this.$store.getters.userInfo.email || '';
  },
  methods: {
    changePassword(times) {
      this.verify.passwordSuccess = true;
      let time = (times / 1000).toFixed(1);
      this.verify.passwordMsg = 'Total time ' + time + 's';
      setTimeout(() => {
        this.visible.passwordSlideBlock = false;
        this.verify.passwordSuccess = false;
        // 无论后续成不成功，验证码滑动都要刷新
        this.$refs.passwordSlideBlock.reset();
      }, 1000);

      this.$refs['formPassword'].validate((valid) => {
        if (valid) {
          this.loading.btnPassword = true;
          let data = Object.assign({}, this.formPassword);
          delete data.againPassword;
          api.changePassword(data).then(
            (res) => {
              this.loading.btnPassword = false;
              if (res.data.data.code == 200) {
                myMessage.success(this.$t('m.Update_Successfully'));
                this.visible.passwordAlert = {
                  show: true,
                  title: this.$t('m.Update_Successfully'),
                  type: 'success',
                  description: res.data.data.msg,
                };
                setTimeout(() => {
                  this.visible.passwordAlert = false;
                  this.$router.push({ name: 'Logout' });
                }, 5000);
              } else {
                myMessage.error(res.data.data.msg);
                this.visible.passwordAlert = {
                  show: true,
                  title: this.$t('m.Update_Failed'),
                  type: 'warning',
                  description: res.data.data.msg,
                };
                if (res.data.data.code == 403) {
                  this.visible.passwordAlert.type = 'error';
                  this.disabled.btnPassword = true;
                }
              }
            },
            (err) => {
              this.loading.btnPassword = false;
            }
          );
        }
      });
    },
    getChangeEmailCode(){
      if(!this.formEmail.newEmail){
        myMessage.error(this.$t('m.The_new_email_cannot_be_empty'));
        return;
      }
      var emailReg = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
      if (!emailReg.test(this.formEmail.newEmail)) {
        myMessage.error(this.$t('m.Email_Check_Format'));
        return;
      }
      if (this.formEmail.oldEmail === this.formEmail.newEmail) {
        myMessage.error(this.$t('m.The_new_email_does_not_change'));
        return;
      }
      this.loading.btnSendEmail = true;
      api.getChangeEmailCode(this.formEmail.newEmail).then((res)=>{
        myMessage.success(this.$t('m.Change_Send_Email_Msg'));
        this.$notify.success({
          title: this.$t('m.Success'),
          message: this.$t('m.Change_Send_Email_Msg'),
          duration: 5000,
          offset: 50
        });
        this.loading.btnSendEmail = false;
      },(_)=>{
        this.loading.btnSendEmail = false;
      })
    },
    changeEmail(times) {
      this.verify.emailSuccess = true;
      let time = (times / 1000).toFixed(1);
      this.verify.emailMsg = 'Total time ' + time + 's';
      setTimeout(() => {
        this.visible.emailSlideBlock = false;
        this.verify.emailSuccess = false;
        // 无论后续成不成功，验证码滑动都要刷新
        this.$refs.emailSlideBlock.reset();
      }, 1000);
      this.$refs['formEmail'].validate((valid) => {
        if (valid) {
          this.loading.btnEmail = true;
          let data = Object.assign({}, this.formEmail);
          api.changeEmail(data).then(
            (res) => {
              this.loading.btnEmail = false;
              if (res.data.data.code == 200) {
                myMessage.success(this.$t('m.Update_Successfully'));
                this.visible.emailAlert = {
                  show: true,
                  title: this.$t('m.Update_Successfully'),
                  type: 'success',
                  description: res.data.data.msg,
                };
                // 更新本地缓存
                this.$store.dispatch('setUserInfo', res.data.data.userInfo);
                this.$refs['formEmail'].resetFields();
                this.formEmail.oldEmail = res.data.data.userInfo.email;
              } else {
                myMessage.error(res.data.data.msg);
                this.visible.emailAlert = {
                  show: true,
                  title: this.$t('m.Update_Failed'),
                  type: 'warning',
                  description: res.data.data.msg,
                };
                if (res.data.data.code == 403) {
                  this.visible.emailAlert.type = 'error';
                  this.disabled.btnEmail = true;
                }
              }
            },
            (err) => {
              this.loading.btnEmail = false;
            }
          );
        }
      });
    },
    onAgain(type) {
      if (type === 'password') {
        this.$refs.passwordSlideBlock.reset();
      } else {
        this.$refs.emailSlideBlock.reset();
      }
      myMessage.warning(this.$t('m.Guess_robot'));
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
}
.left {
  text-align: center;
}
.right {
  text-align: center;
}
:deep(.el-input__inner) {
  height: 32px;
  line-height: 40px;
}
:deep(.el-form-item) {
  display: block;
  margin-bottom: 22px;
}
:deep(.el-form-item__content) {
  display: block;
  line-height: 40px;
}
:deep(.el-input) {
  display: flex;
  align-items: center;
  width: 100%;
  height: 40px;
}
:deep(.el-input__wrapper) {
  width: 100%;
  min-height: 32px;
  height: 32px;
  padding: 1px 15px;
}
:deep(.email-captcha-input) {
  min-width: 0;
  box-sizing: border-box;
}
:deep(.email-captcha-input .el-input__wrapper) {
  flex: 1 1 0%;
  width: 0;
  min-width: 0;
}
:deep(.email-captcha-input .el-input-group__append) {
  flex: 0 0 auto;
  align-self: center;
  height: 32px;
  min-height: 32px;
  box-sizing: border-box;
  white-space: nowrap;
}
:deep(.el-form-item__label) {
  display: block;
  float: none;
  width: auto;
  height: 20px;
  justify-content: flex-start;
  padding-right: 0;
  text-align: left;
  font-size: 12px;
  line-height: 20px;
}
.separator {
  display: block;
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  border: 1px dashed #eee;
}
</style>
