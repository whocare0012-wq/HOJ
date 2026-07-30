<template>
  <el-card class="box-card">
    <el-collapse v-model="activeName" accordion>
      <el-collapse-item name="Account">
        <template #title>
          <span class="setting-heading">
            <i class="fa fa-gear" aria-hidden="true"></i>
            <span>{{ $t('m.Account_Setting') }}</span>
          </span>
        </template>
        <component :is="Account"></component>
      </el-collapse-item>
      <el-collapse-item name="UserInfo">
        <template #title>
          <span class="setting-heading">
            <i class="fa fa-gear" aria-hidden="true"></i>
            <span>{{ $t('m.UserInfo_Setting') }}</span>
          </span>
        </template>
        <component v-if="userInfoMounted" :is="UserInfo"></component>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>
<script>
import { defineAsyncComponent } from 'vue';
const Account = defineAsyncComponent(() => import('@/components/oj/setting/Account'));
const UserInfo = defineAsyncComponent(() => import('@/components/oj/setting/UserInfo'));
export default {
  components: {
    Account,
    UserInfo,
  },
  data() {
    return {
      Account: 'Account',
      UserInfo: 'UserInfo',
      activeName: 'Account',
      userInfoMounted: false,
    };
  },
  watch: {
    activeName(value) {
      if (value === 'UserInfo') {
        this.userInfoMounted = true;
      }
    },
  },
};
</script>
<style scoped>
@media screen and (min-width: 1200px) {
  .box-card {
    margin-left: 10%;
    margin-right: 10%;
  }
}
:deep(.el-collapse-item__header) {
  border-radius: 4px;
  font-size: 18px;
  color: #409eff;
}
.setting-heading {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}
</style>
