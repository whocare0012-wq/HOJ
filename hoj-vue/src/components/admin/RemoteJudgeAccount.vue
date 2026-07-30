<template>
  <el-card>
      <template #header>
        <div>
          <span class="title">{{OJ}} {{$t('m.Account_Config')}}</span>
        </div>
      </template>
      <el-row 
        v-for="(value,index) in usernameListTmp" 
        :key="index" 
        :gutter="15"
        class="mg-top">
        <el-col :xs="24" :md="10">
          <el-input
            v-model="usernameListTmp[index]"
            size="small"
            clearable>
            <template #prepend>{{$t('m.Account')}}{{index+1}}</template>
          </el-input>
        </el-col>
        <el-col :xs="24" :md="10">
          <el-input
            v-model="passwordListTmp[index]"
            size="small"
            show-password>
            <template #prepend>{{$t('m.Password')}}{{index+1}}</template>
          </el-input>
        </el-col>
        <el-col :xs="24" :md="4" class="t-center">
          <el-button 
            type="danger" 
            :icon="legacyElementIcons['el-icon-delete']"
            circle 
            size="small"
            @click="deleteAccount(index)">
          </el-button>
        </el-col>

      </el-row>
      <el-button 
        type="warning" 
        round 
        size="small"
        class="mg-top remote-account-action-button"
        @click="addAccount"
        :icon="legacyElementIcons['el-icon-plus']">{{ $t('m.Add_Account') }}
      </el-button>
      <el-button
        type="primary"
        :loading="loading"
        style="margin-top:15px"
        @click="saveSwitchConfig"
        size="small"
        class="remote-account-action-button"
        >
        <span class="remote-account-action-button__content">
          <i class="fa fa-save"></i>
          <span>{{ $t('m.Save') }}</span>
        </span>
      </el-button>
  </el-card>
</template>

<script>
export default {
  props: {
    usernameList:{
      default:[],
      type: Array
    },
    passwordList:{
      default:[],
      type: Array
    },
    OJ:{
      type: String
    },
    loading:{
      type: Boolean,
      default: false
    }
  },
  data() {
      return {
        usernameListTmp: [],
        passwordListTmp: [],
      }
  },
  mounted(){
    this.usernameListTmp = this.usernameList;
    this.passwordListTmp = this.passwordList;
  },
  methods:{
      deleteAccount(index){
        this.usernameListTmp.splice(index, 1);
        this.$emit('update:usernameList', this.usernameListTmp);
        this.passwordListTmp.splice(index, 1);
        this.$emit('update:passwordList', this.passwordListTmp);
      },
      addAccount(){
        this.usernameListTmp.push("");
        this.$emit('update:usernameList', this.usernameListTmp);
        this.passwordListTmp.push("");
        this.$emit('update:passwordList', this.passwordListTmp);
      },
      saveSwitchConfig(){
        this.$emit('saveSwitchConfig');
      }
  },
  watch: {
    usernameList(val) {
      if (this.usernameListTmp !== val) {
        this.usernameListTmp = val;
      }
    },
    passwordList(val) {
      if (this.passwordListTmp !== val) {
        this.passwordListTmp = val;
      }
    },
  }
}
</script>

<style scoped>
.title{
  font-size: 18px;
  font-weight: bolder;
}
.mg-top{
  margin-top: 15px;
}
.remote-account-action-button {
  min-height: 32px;
  padding: 7px 14px;
}
.remote-account-action-button :deep(.el-icon) {
  margin-right: 6px;
}
.remote-account-action-button__content {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
@media screen and (max-width: 992px) {
  .t-center{
    text-align: center;
    margin-top: 10px;
  }
}
</style>
