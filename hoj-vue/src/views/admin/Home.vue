<template>
  <div class="admin-container">
    <div v-if="!mobileNar">
      <el-menu
        class="vertical_menu"
        :router="true"
        :default-active="currentPath"
      >
        <el-tooltip
            :content="$t('m.Click_To_Change_Web_Language')"
            placement="bottom"
            effect="dark"
          >
          <div class="logo" @click="changeWebLanguage(webLanguage == 'zh-CN' ? 'en-US' : 'zh-CN')">
            <img :src="imgUrl" alt="Online Judge Admin" />
          </div>
        </el-tooltip>
        <el-menu-item index="/admin/">
          <i class="fa fa-tachometer fa-size" aria-hidden="true"></i
          >{{ $t('m.Dashboard') }}
        </el-menu-item>
        <!-- <el-sub-menu v-if="isSuperAdmin" index="general"> -->
        <el-sub-menu index="general" v-if="isSuperAdmin">
          <template #title
            ><el-icon class="admin-menu-icon"><MenuIcon /></el-icon>{{ $t('m.General') }}</template
          >
          <el-menu-item index="/admin/user">{{
            $t('m.User_Admin')
          }}</el-menu-item>
          <el-menu-item index="/admin/announcement">{{
            $t('m.Announcement_Admin')
          }}</el-menu-item>
          <el-menu-item index="/admin/notice">{{
            $t('m.SysNotice')
          }}</el-menu-item>
          <el-menu-item index="/admin/conf">{{
            $t('m.System_Config')
          }}</el-menu-item>
          <el-menu-item index="/admin/switch">{{
            $t('m.System_Switch')
          }}</el-menu-item>
        </el-sub-menu>
        <!-- <el-sub-menu index="problem" v-if="hasProblemPermission"> -->
        <el-sub-menu index="problem">
          <template #title
            ><i class="fa fa-bars fa-size" aria-hidden="true"></i
            >{{ $t('m.Problem_Admin') }}</template
          >
          <el-menu-item index="/admin/problems">{{
            $t('m.Problem_List')
          }}</el-menu-item>
          <el-menu-item index="/admin/problem/create">{{
            $t('m.Create_Problem')
          }}</el-menu-item>
          <el-menu-item index="/admin/problem/tag">{{
            $t('m.Admin_Tag')
          }}</el-menu-item>
          <el-menu-item
            index="/admin/problem/difficulty"
            v-if="isSuperAdmin || isProblemAdmin"
          >
            {{ $t('m.Problem_Difficulty_Admin') }}
          </el-menu-item>
           <el-menu-item index="/admin/group-problem/apply"
           v-if="isSuperAdmin || isProblemAdmin"
           >{{$t('m.Admin_Group_Apply_Problem')}}
           </el-menu-item>
          <el-menu-item
            index="/admin/problem/batch-operation"
            v-if="isSuperAdmin || isProblemAdmin"
            >{{ $t('m.Export_Import_Problem') }}</el-menu-item
          >
        </el-sub-menu>

        <el-sub-menu index="training">
          <template #title
            ><el-icon class="admin-menu-icon"><TrainingIcon /></el-icon>{{ $t('m.Training_Admin') }}</template
          >
          <el-menu-item index="/admin/training">{{
            $t('m.Training_List')
          }}</el-menu-item>
          <el-menu-item index="/admin/training/create">{{
            $t('m.Create_Training')
          }}</el-menu-item>
          <el-menu-item index="/admin/training/category">{{
            $t('m.Admin_Category')
          }}</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="contest">
          <template #title
            ><i class="fa fa-trophy fa-size" aria-hidden="true"></i
            >{{ $t('m.Contest_Admin') }}</template
          >
          <el-menu-item index="/admin/contest">{{
            $t('m.Contest_List')
          }}</el-menu-item>
          <el-menu-item index="/admin/contest/create">{{
            $t('m.Create_Contest')
          }}</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="discussion">
          <template #title
            ><i class="fa fa-comments fa-size" aria-hidden="true"></i
            >{{ $t('m.Discussion') }}</template
          >
          <el-menu-item index="/admin/discussion">{{
            $t('m.Discussion_Admin')
          }}</el-menu-item>
        </el-sub-menu>
        <el-menu-item v-if="isSuperAdmin" index="/admin/daily-check-in">
          <i class="fa fa-calendar-check-o fa-size" aria-hidden="true"></i>
          {{ $t('m.Daily_Check_In_Admin') }}
        </el-menu-item>
        <el-menu-item v-if="isSuperAdmin" index="/admin/ai-assistant">
          <i class="fa fa-comments-o fa-size" aria-hidden="true"></i>
          {{ $t('m.AI_Assistant') }}
        </el-menu-item>
      </el-menu>
      <div id="header">
        <el-row>
          <el-col :span="18">
            <div class="breadcrumb-container">
              <el-breadcrumb separator-class="el-icon-arrow-right">
                <el-breadcrumb-item :to="{ path: '/admin/' }">{{
                  $t('m.Home_Page')
                }}</el-breadcrumb-item>
                <el-breadcrumb-item v-for="item in routeList" :key="item.path">
                  {{ $t('m.' + item.meta.title.replaceAll(' ', '_')) }}
                </el-breadcrumb-item>
              </el-breadcrumb>
            </div>
          </el-col>
          <el-col :span="6" v-show="isAuthenticated">
            <i
              class="fa fa-font katex-editor fa-size"
              @click="katexVisible = true"
            ></i>
            <avatar
              :username="userInfo.username"
              :inline="true"
              :size="30"
              color="#FFF"
              :src="userInfo.avatar"
              class="drop-avatar"
            ></avatar>
            <el-dropdown
              @command="handleCommand"
              style="vertical-align: middle;"
            >
              <span
                >{{ userInfo.username
                }}<i class="el-icon-caret-bottom el-icon--right"></i
              ></span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="logout">{{
                    $t('m.Logout')
                  }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-col>
        </el-row>
      </div>
    </div>

    <div v-else>
      <header class="mobile-nav">
        <el-button
          class="mobile-nav-button"
          text
          :aria-label="$t('m.Dashboard')"
          @click="opendrawer = true"
        >
          <i class="el-icon-s-unfold"></i>
        </el-button>
        <span class="mobile-nav-title">
          {{ websiteConfig.shortName ? websiteConfig.shortName + ' ADMIN' : 'ADMIN' }}
        </span>
        <div v-if="isAuthenticated" class="mobile-nav-actions">
          <el-button
            class="mobile-nav-button"
            text
            aria-label="Latex Editor"
            @click="katexVisible = true"
          >
            <i class="fa fa-font katex-editor"></i>
          </el-button>
          <el-dropdown trigger="click" @command="handleCommand">
            <el-button class="mobile-user-button" text>
              {{ userInfo.username }}<i class="el-icon-caret-bottom"></i>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  {{ $t('m.Logout') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <div class="mobile-nav-spacer"></div>

      <el-drawer
        v-model="opendrawer"
        direction="ltr"
        size="280px"
        :with-header="false"
        class="admin-mobile-drawer"
      >
        <el-menu
          :default-active="currentPath"
          router
          @select="closeMobileDrawer"
        >
          <el-menu-item index="/admin/dashboard">
            <i class="fa fa-tachometer mobile-drawer-icon"></i>
            <span>{{ $t('m.Dashboard') }}</span>
          </el-menu-item>
          <el-sub-menu v-if="isSuperAdmin" index="mobile-general">
            <template #title>
              <el-icon class="mobile-drawer-icon"><MenuIcon /></el-icon>
              <span>{{ $t('m.General') }}</span>
            </template>
            <el-menu-item index="/admin/user">{{ $t('m.User_Admin') }}</el-menu-item>
            <el-menu-item index="/admin/announcement">{{ $t('m.Announcement_Admin') }}</el-menu-item>
            <el-menu-item index="/admin/notice">{{ $t('m.SysNotice') }}</el-menu-item>
            <el-menu-item index="/admin/conf">{{ $t('m.System_Config') }}</el-menu-item>
            <el-menu-item index="/admin/switch">{{ $t('m.System_Switch') }}</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="mobile-problem">
            <template #title>
              <i class="fa fa-bars mobile-drawer-icon"></i>
              <span>{{ $t('m.Problem_Admin') }}</span>
            </template>
            <el-menu-item index="/admin/problems">{{ $t('m.Problem_List') }}</el-menu-item>
            <el-menu-item index="/admin/problem/create">{{ $t('m.Create_Problem') }}</el-menu-item>
            <el-menu-item index="/admin/problem/tag">{{ $t('m.Admin_Tag') }}</el-menu-item>
            <el-menu-item
              v-if="isSuperAdmin || isProblemAdmin"
              index="/admin/problem/difficulty"
            >
              {{ $t('m.Problem_Difficulty_Admin') }}
            </el-menu-item>
            <el-menu-item
              v-if="isSuperAdmin || isProblemAdmin"
              index="/admin/group-problem/apply"
            >
              {{ $t('m.Admin_Group_Apply_Problem') }}
            </el-menu-item>
            <el-menu-item
              v-if="isSuperAdmin || isProblemAdmin"
              index="/admin/problem/batch-operation"
            >
              {{ $t('m.Export_Import_Problem') }}
          </el-menu-item>
        </el-sub-menu>
          <el-sub-menu index="mobile-training">
            <template #title>
              <el-icon class="mobile-drawer-icon"><TrainingIcon /></el-icon>
              <span>{{ $t('m.Training_Admin') }}</span>
            </template>
            <el-menu-item index="/admin/training">{{ $t('m.Training_List') }}</el-menu-item>
            <el-menu-item index="/admin/training/create">{{ $t('m.Create_Training') }}</el-menu-item>
            <el-menu-item index="/admin/training/category">{{ $t('m.Admin_Category') }}</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="mobile-contest">
            <template #title>
              <i class="fa fa-trophy mobile-drawer-icon"></i>
              <span>{{ $t('m.Contest_Admin') }}</span>
            </template>
            <el-menu-item index="/admin/contest">{{ $t('m.Contest_List') }}</el-menu-item>
            <el-menu-item index="/admin/contest/create">{{ $t('m.Create_Contest') }}</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="mobile-discussion">
            <template #title>
              <i class="fa fa-comments mobile-drawer-icon"></i>
              <span>{{ $t('m.Discussion') }}</span>
            </template>
            <el-menu-item index="/admin/discussion">{{ $t('m.Discussion_Admin') }}</el-menu-item>
          </el-sub-menu>
          <el-menu-item
            v-if="isSuperAdmin"
            index="/admin/daily-check-in"
            @click="closeMobileDrawer"
          >
            <i class="fa fa-calendar-check-o mobile-drawer-icon"></i>
            {{ $t('m.Daily_Check_In_Admin') }}
          </el-menu-item>
          <el-menu-item
            v-if="isSuperAdmin"
            index="/admin/ai-assistant"
            @click="closeMobileDrawer"
          >
            <i class="fa fa-comments-o mobile-drawer-icon"></i>
            {{ $t('m.AI_Assistant') }}
          </el-menu-item>
        </el-menu>
      </el-drawer>
    </div>
    <div class="content-app">
      <router-view v-slot="{ Component }">
        <transition name="fadeInUp" mode="out-in">
          <component :is="Component"></component>
        </transition>
      </router-view>
      <div class="footer">
        Powered by
        <a
          :href="websiteConfig.projectUrl"
          style="color:#1E9FFF"
          target="_blank"
          >{{ websiteConfig.projectName }}</a
        >
        <span style="margin-left:10px">
          <el-dropdown @command="changeWebLanguage" placement="top">
            <span class="el-dropdown-link" style="font-size:14px">
              <i class="fa fa-globe" aria-hidden="true">
                {{ getLanguageLabelByValue(this.webLanguage) }}</i
              ><i class="el-icon-arrow-up el-icon--right"></i>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                    v-for="(lang, index) in languages"
                    :key="index"
                    :command="lang.value">{{ lang.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </span>
      </div>
    </div>

    <el-dialog title="Latex Editor" v-model="katexVisible" width="350px">
      <KatexEditor></KatexEditor>
    </el-dialog>
  </div>
</template>

<script>
import { defineAsyncComponent } from 'vue';
import { Menu as MenuIcon, Tickets as TrainingIcon } from '@element-plus/icons-vue';
import { mapGetters } from 'vuex';
const KatexEditor = defineAsyncComponent(() => import('@/components/admin/KatexEditor.vue'));
import api from '@/common/api';
import mMessage from '@/common/message';
import Avatar from '@/components/common/Avatar.vue';
import { languages, getLangLabelByValue } from '@/i18n';
import backstageImage from '@/assets/backstage.png'
export default {
  name: 'app',
  mounted() {
    this.languages = languages;
    this.currentPath = this.$route.path;
    this.getBreadcrumb();
    window.onresize = () => {
      this.page_width();
    };
    this.page_width();
  },
  data() {
    return {
      katexVisible: false,
      opendrawer: false,
      mobileNar: false,
      currentPath: '',
      routeList: [],
      imgUrl: backstageImage,
      languages:[]
    };
  },
  components: {
    KatexEditor,
    Avatar,
    MenuIcon,
    TrainingIcon,
  },
  methods: {
    handleCommand(command) {
      if (command === 'logout') {
        api.admin_logout().then((res) => {
          this.$router.push({ path: '/admin/login' });
          mMessage.success(this.$t('m.Log_Out_Successfully'));
          this.$store.commit('clearUserInfoAndToken');
        });
      }
    },
    closeMobileDrawer() {
      this.opendrawer = false;
    },
    page_width() {
      let screenWidth = window.innerWidth;
      if (screenWidth < 992) {
        this.mobileNar = true;
      } else {
        this.mobileNar = false;
      }
    },
    getBreadcrumb() {
      let matched = this.$route.matched.filter((item) => item.meta.title); //获取路由信息，并过滤保留路由标题信息存入数组
      this.routeList = matched;
    },
    changeWebLanguage(language) {
      this.$store.commit('changeWebLanguage', { language: language });
    },
    getLanguageLabelByValue(value){
      return getLangLabelByValue(value);
    }
  },
  computed: {
    ...mapGetters([
      'userInfo',
      'isSuperAdmin',
      'isProblemAdmin',
      'isAuthenticated',
      'websiteConfig',
      'webLanguage',
    ]),
  },
  watch: {
    $route() {
      this.currentPath = this.$route.path;
      this.getBreadcrumb(); //监听路由变化
    },
  },
};
</script>

<style scoped>
.vertical_menu {
  background-color: #fff;
  overflow-x: hidden;
  overflow-y: auto;
  width: 15%;
  height: 100%;
  position: fixed !important;
  z-index: 100;
  top: 0;
  bottom: 0;
  left: 0;
}
.vertical_menu .logo {
  margin: 20px 0;
  text-align: center;
  cursor: pointer;
}
.vertical_menu .logo img {
  background-color: #fff;
  border: 3px solid #fff;
  width: 110px;
  height: 110px;
}
.fa-size {
  text-align: center;
  font-size: 18px;
  vertical-align: middle;
  margin-right: 5px;
  width: 24px;
}
.admin-menu-icon {
  font-size: 18px;
  margin-right: 5px;
  vertical-align: middle;
  width: 24px;
}
a {
  background-color: transparent;
}

a:active,
a:hover {
  outline-width: 0;
}

img {
  border-style: none;
}

.admin-container {
  overflow-x: hidden;
  font-weight: 400;
  height: 100%;
  -webkit-font-smoothing: antialiased;
  background-color: #eff3f5;
  overflow-y: auto;
}
.breadcrumb-container {
  padding: 17px;
  background-color: #fff;
}
* {
  box-sizing: border-box;
}

#header {
  text-align: right;
  margin-left: 15%;
  padding-right: 30px;
  line-height: 50px;
  height: 50px;
  background: #f9fafc;
}
.footer {
  margin: 15px;
  text-align: center;
  font-size: small;
}
@media screen and (max-width: 992px) {
  .content-app {
    padding: 0 5px;
    margin-top: 20px;
  }
}
@media screen and (min-width: 992px) {
  .content-app {
    margin-top: 10px;
    margin-right: 10px;
    margin-left: calc(20% + 10px);;
  }
  .vertical_menu {
    width: 20%;
  }
  #header {
    margin-left: 20%;
  }
}
@media screen and (min-width: 1150px) {
  .content-app {
    margin-top: 10px;
    margin-right: 10px;
    margin-left: 220px;
  }
  .vertical_menu {
    width: 210px;
  }
  #header {
    margin-left: 210px;
  }
}


@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translate(0, 30px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

.fadeInUp-enter-active {
  animation: fadeInUp 0.8s;
}

.katex-editor {
  margin-right: 5px;
  cursor: pointer;
  vertical-align: middle;
  margin-right: 10px;
}
.drop-avatar {
  vertical-align: middle;
  margin-right: 10px;
}
.content-app {
  min-width: 0;
}
.mobile-nav {
  align-items: center;
  background: #2196f3;
  color: #fff;
  display: flex;
  height: 56px;
  left: 0;
  padding: 0 8px;
  position: fixed;
  top: 0;
  width: 100%;
  z-index: 2500;
}
.mobile-nav-spacer {
  height: 56px;
}
.mobile-nav-title {
  flex: 1;
  font-size: 18px;
  font-weight: 500;
  overflow: hidden;
  padding: 0 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mobile-nav-actions {
  align-items: center;
  display: flex;
  flex-shrink: 0;
}
.mobile-nav :deep(.el-button) {
  color: #fff;
  margin-left: 0;
}
.mobile-nav-button {
  font-size: 20px;
}
.mobile-user-button {
  padding-left: 6px;
  padding-right: 6px;
}
.mobile-drawer-icon {
  font-size: 18px;
  margin-right: 10px;
  text-align: center;
  width: 24px;
}
:global(.admin-mobile-drawer .el-drawer__body) {
  padding: 0;
}
:global(.admin-mobile-drawer .el-menu) {
  border-right: 0;
}
</style>
