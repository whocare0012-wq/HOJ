<template>
  <div>
    <template v-if="!mobileNar">
      <div id="header">
        <el-menu
          :default-active="activeMenuName"
          :ellipsis="false"
          mode="horizontal"
          router
          active-text-color="#2196f3"
          text-color="#495060"
        >
          <div class="logo">
            <el-tooltip
              :content="$t('m.Click_To_Change_Web_Language')"
              placement="bottom"
              effect="dark"
            >
              <el-image
                style="width: 139px; height: 50px"
                :src="imgUrl"
                fit="scale-down"
                @click="changeWebLanguage"
              ></el-image>
            </el-tooltip>
          </div>
          <template v-if="mode == 'defalut'">
            <el-menu-item index="/home"
              ><i class="el-icon-s-home navbar-icon legacy-navbar-icon"></i>{{ $t('m.NavBar_Home') }}</el-menu-item
            >
            <el-menu-item index="/problem"
              ><i class="el-icon-s-grid navbar-icon legacy-navbar-icon"></i
              >{{ $t('m.NavBar_Problem') }}</el-menu-item
            >
            <el-menu-item index="/training"
              ><i class="el-icon-s-claim navbar-icon legacy-navbar-icon"></i
              >{{ $t('m.NavBar_Training') }}</el-menu-item
            >
            <el-menu-item index="/contest"
              ><i class="el-icon-trophy navbar-icon legacy-navbar-icon"></i
              >{{ $t('m.NavBar_Contest') }}</el-menu-item
            >
            <el-menu-item index="/status"
              ><i class="el-icon-s-marketing navbar-icon legacy-navbar-icon"></i
              >{{ $t('m.NavBar_Status') }}</el-menu-item
            >
            <el-menu-item index="/oi-rank">
              <i class="el-icon-s-data navbar-icon legacy-navbar-icon"></i>{{ $t('m.NavBar_Rank') }}
            </el-menu-item>
            <el-menu-item index="/discussion"
              v-if="websiteConfig.openPublicDiscussion"
              ><i class="el-icon-s-comment navbar-icon legacy-navbar-icon"></i
              >{{ $t('m.NavBar_Discussion') }}</el-menu-item
            >
            <el-menu-item index="/group"
              ><i
                class="fa fa-users navbar-icon"
              ></i
              >{{ $t('m.NavBar_Group') }}</el-menu-item
            >
            <el-menu-item index="/resource"
              ><i class="fa fa-folder navbar-icon"></i
              >{{ $t('m.NavBar_Resource') }}</el-menu-item
            >
            <el-sub-menu index="about">
              <template #title
                ><i class="el-icon-info navbar-icon legacy-navbar-icon"></i>{{ $t('m.NavBar_About') }}</template
              >
              <el-menu-item
                class="navbar-centered-submenu-item"
                index="/introduction"
              >{{
                $t('m.NavBar_Introduction')
              }}</el-menu-item>
              <el-menu-item
                class="navbar-centered-submenu-item"
                index="/developer"
              >{{
                $t('m.NavBar_Developer')
              }}</el-menu-item>
            </el-sub-menu>
        </template>
        <template v-else-if="mode == 'training'">
          <el-menu-item index="/home"
              ><i class="el-icon-s-home navbar-icon legacy-navbar-icon"></i>{{ $t('m.NavBar_Back_Home') }}</el-menu-item
            >
            <template v-if="$route.params.groupID">
              <el-menu-item :index="'/group/' + $route.params.groupID"
              ><i
                class="fa fa-users navbar-icon"
              ></i
              >{{ $t('m.NavBar_Group_Home') }}</el-menu-item>
            </template>
            <el-menu-item :index="getTrainingHomePath()"
              ><i class="el-icon-s-claim navbar-icon legacy-navbar-icon"></i>{{ $t('m.NavBar_Training_Home') }}</el-menu-item
            >
            <el-menu-item :index="getTrainingProblemListPath()"
              ><i class="fa fa-list navbar-icon"></i>{{ $t('m.Problem_List') }}</el-menu-item
            >
        </template>
        <template v-else-if="mode == 'contest'">
          <el-menu-item index="/home"
              ><i class="el-icon-s-home navbar-icon legacy-navbar-icon"></i>{{ $t('m.NavBar_Back_Home') }}</el-menu-item
            >
            <el-menu-item :index="'/contest/' + $route.params.contestID"
              ><i class="el-icon-trophy navbar-icon legacy-navbar-icon"></i>{{ $t('m.NavBar_Contest_Home') }}</el-menu-item
            >
            <el-menu-item :index="'/contest/' + $route.params.contestID + '/problems'"
              ><i class="fa fa-list navbar-icon"></i>{{ $t('m.Problem_List') }}</el-menu-item
            >
            <el-menu-item :index="'/contest/' + $route.params.contestID + '/submissions?onlyMine=true'"
              ><i class="el-icon-menu navbar-icon legacy-navbar-icon"></i>{{ $t('m.NavBar_Contest_Own_Submission') }}</el-menu-item
            >
            <el-menu-item :index="'/contest/' + $route.params.contestID + '/rank'"
              ><i class="fa fa-bar-chart navbar-icon"></i>{{ $t('m.NavBar_Contest_Rank') }}</el-menu-item
            >
        </template>
        <template v-else-if="mode == 'group'">
          <el-menu-item index="/home"
              ><i class="el-icon-s-home navbar-icon legacy-navbar-icon"></i>{{ $t('m.NavBar_Back_Home') }}</el-menu-item
            >
            <template v-if="$route.params.groupID">
              <el-menu-item :index="'/group/' + $route.params.groupID"
              ><i
                class="fa fa-users navbar-icon"
              ></i
              >{{ $t('m.NavBar_Group_Home') }}</el-menu-item>
            </template>
            <el-menu-item :index="'/group/' + $route.params.groupID + '/problem'"
              ><i class="fa fa-list navbar-icon"></i>{{ $t('m.Problem_List') }}</el-menu-item
            >
        </template>

          <template v-if="!isAuthenticated">
            <div class="btn-menu">
              <el-button 
                type="primary" 
                size="default"
                round
                @click="handleBtnClick('Login')"
                >{{ $t('m.NavBar_Login') }}
              </el-button>
              <el-button
                v-if="websiteConfig.register"
                size="default"
                round
                @click="handleBtnClick('Register')"
                style="margin-left: 5px"
                >{{ $t('m.NavBar_Register') }}
              </el-button>
            </div>
          </template>
          <template v-else>
            <el-dropdown
              class="drop-menu"
              @command="handleRoute"
              placement="bottom"
              trigger="click"
            >
              <span class="el-dropdown-link">
                {{ userInfo.username }}<i class="el-icon-caret-bottom"></i>
              </span>

              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="/user-home">{{
                    $t('m.NavBar_UserHome')
                  }}</el-dropdown-item>
                  <el-dropdown-item command="/status?onlyMine=true">{{
                    $t('m.NavBar_Submissions')
                  }}</el-dropdown-item>
                  <el-dropdown-item command="/setting">{{
                    $t('m.NavBar_Setting')
                  }}</el-dropdown-item>
                  <el-dropdown-item v-if="isAdminRole" command="/admin">{{
                    $t('m.NavBar_Management')
                  }}</el-dropdown-item>
                  <el-dropdown-item divided command="/logout">{{
                    $t('m.NavBar_Logout')
                  }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <avatar
              :username="userInfo.username"
              :inline="true"
              :size="30"
              color="#FFF"
              :src="avatar"
              class="drop-avatar"
            ></avatar>
            <el-dropdown
              class="drop-msg"
              @command="handleRoute"
              placement="bottom"
              trigger="click"
            >
              <span class="el-dropdown-link">
                <i class="el-icon-message-solid message-icon legacy-navbar-icon"></i>
                <svg
                  v-if="
                    unreadMessage.comment > 0 ||
                      unreadMessage.reply > 0 ||
                      unreadMessage.like > 0 ||
                      unreadMessage.sys > 0 ||
                      unreadMessage.mine > 0
                  "
                  width="10"
                  height="10"
                  style="vertical-align: top;margin-left: -11px;margin-top: 3px;"
                >
                  <circle cx="5" cy="5" r="5" style="fill: red;"></circle>
                </svg>
              </span>

              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="/message/discuss">
                    <span>{{ $t('m.DiscussMsg') }}</span>
                    <span class="drop-msg-count" v-if="unreadMessage.comment > 0">
                      <MsgSvg :total="unreadMessage.comment"></MsgSvg>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item command="/message/reply">
                    <span>{{ $t('m.ReplyMsg') }}</span>
                    <span class="drop-msg-count" v-if="unreadMessage.reply > 0">
                      <MsgSvg :total="unreadMessage.reply"></MsgSvg>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item command="/message/like">
                    <span>{{ $t('m.LikeMsg') }}</span>
                    <span class="drop-msg-count" v-if="unreadMessage.like > 0">
                      <MsgSvg :total="unreadMessage.like"></MsgSvg>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item command="/message/sys">
                    <span>{{ $t('m.SysMsg') }}</span>
                    <span class="drop-msg-count" v-if="unreadMessage.sys > 0">
                      <MsgSvg :total="unreadMessage.sys"></MsgSvg>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item command="/message/mine">
                    <span>{{ $t('m.MineMsg') }}</span>
                    <span class="drop-msg-count" v-if="unreadMessage.mine > 0">
                      <MsgSvg :total="unreadMessage.mine"></MsgSvg>
                    </span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-menu>
      </div>
      <div id="header-hidden">
      </div>
    </template>
    <template v-else>
      <header class="mobile-nav">
        <el-button
          class="mobile-nav-button"
          text
          :aria-label="$t('m.NavBar_Home')"
          @click="opendrawer = true"
        >
          <i class="el-icon-s-unfold"></i>
        </el-button>
        <el-tooltip
          :content="$t('m.Click_To_Change_Web_Language')"
          placement="bottom"
          effect="dark"
        >
          <button type="button" class="mobile-nav-title" @click="changeWebLanguage">
            {{ websiteConfig.shortName ? websiteConfig.shortName : 'OJ' }}
          </button>
        </el-tooltip>
        <div class="mobile-nav-actions">
          <template v-if="!isAuthenticated">
            <el-button text @click="handleBtnClick('Login')">
              {{ $t('m.NavBar_Login') }}
            </el-button>
            <el-button
              v-if="websiteConfig.register"
              text
              @click="handleBtnClick('Register')"
            >
              {{ $t('m.NavBar_Register') }}
            </el-button>
          </template>
          <template v-else>
            <el-dropdown trigger="click" @command="handleCommand">
              <el-button class="mobile-nav-button mobile-message-button" text>
                <i class="el-icon-message-solid message-icon legacy-navbar-icon"></i>
                <span v-if="hasUnreadMessage" class="mobile-unread-dot"></span>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="/message/discuss">
                    {{ $t('m.DiscussMsg') }}
                    <MsgSvg v-if="unreadMessage.comment > 0" :total="unreadMessage.comment"></MsgSvg>
                  </el-dropdown-item>
                  <el-dropdown-item command="/message/reply" divided>
                    {{ $t('m.ReplyMsg') }}
                    <MsgSvg v-if="unreadMessage.reply > 0" :total="unreadMessage.reply"></MsgSvg>
                  </el-dropdown-item>
                  <el-dropdown-item command="/message/like" divided>
                    {{ $t('m.LikeMsg') }}
                    <MsgSvg v-if="unreadMessage.like > 0" :total="unreadMessage.like"></MsgSvg>
                  </el-dropdown-item>
                  <el-dropdown-item command="/message/sys" divided>
                    {{ $t('m.SysMsg') }}
                    <MsgSvg v-if="unreadMessage.sys > 0" :total="unreadMessage.sys"></MsgSvg>
                  </el-dropdown-item>
                  <el-dropdown-item command="/message/mine" divided>
                    {{ $t('m.MineMsg') }}
                    <MsgSvg v-if="unreadMessage.mine > 0" :total="unreadMessage.mine"></MsgSvg>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-dropdown trigger="click" @command="handleCommand">
              <el-button class="mobile-user-button" text>
                <avatar
                  :username="userInfo.username"
                  :inline="true"
                  :size="30"
                  color="#FFF"
                  :src="avatar"
                  :title="userInfo.username"
                ></avatar>
                <i class="el-icon-caret-bottom"></i>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="/user-home">{{ $t('m.NavBar_UserHome') }}</el-dropdown-item>
                  <el-dropdown-item command="/status?onlyMine=true" divided>{{ $t('m.NavBar_Submissions') }}</el-dropdown-item>
                  <el-dropdown-item command="/setting" divided>{{ $t('m.NavBar_Setting') }}</el-dropdown-item>
                  <el-dropdown-item v-if="isAdminRole" command="/admin" divided>{{ $t('m.NavBar_Management') }}</el-dropdown-item>
                  <el-dropdown-item command="/logout" divided>{{ $t('m.NavBar_Logout') }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </div>
      </header>

      <div class="mobile-nav-spacer">
        <!--占位，刚好占领导航栏的高度-->
      </div>

      <el-drawer
        v-model="opendrawer"
        direction="ltr"
        size="280px"
        :with-header="false"
        class="mobile-drawer"
      >
        <el-menu
          :default-active="activeMenuName"
          router
          @select="closeMobileDrawer"
        >
          <el-menu-item index="/home">
            <i class="el-icon-s-home mobile-drawer-icon"></i>
            <span>{{ $t('m.NavBar_Home') }}</span>
          </el-menu-item>
          <el-menu-item index="/problem">
            <i class="el-icon-s-grid mobile-drawer-icon"></i>
            <span>{{ $t('m.NavBar_Problem') }}</span>
          </el-menu-item>
          <el-menu-item index="/training">
            <i class="el-icon-s-claim mobile-drawer-icon"></i>
            <span>{{ $t('m.NavBar_Training') }}</span>
          </el-menu-item>
          <el-menu-item index="/contest">
            <i class="el-icon-trophy mobile-drawer-icon"></i>
            <span>{{ $t('m.NavBar_Contest') }}</span>
          </el-menu-item>
          <el-menu-item index="/status">
            <i class="el-icon-s-marketing mobile-drawer-icon"></i>
            <span>{{ $t('m.NavBar_Status') }}</span>
          </el-menu-item>
          <el-menu-item index="/oi-rank">
            <i class="el-icon-s-data mobile-drawer-icon"></i>
            <span>{{ $t('m.NavBar_Rank') }}</span>
          </el-menu-item>
          <el-menu-item
            v-if="websiteConfig.openPublicDiscussion"
            index="/discussion"
          >
            <i class="fa fa-comments mobile-drawer-icon"></i>
            <span>{{ $t('m.NavBar_Discussion') }}</span>
          </el-menu-item>
          <el-menu-item index="/group">
            <i class="fa fa-users mobile-drawer-icon"></i>
            <span>{{ $t('m.NavBar_Group') }}</span>
          </el-menu-item>
          <el-menu-item index="/resource">
            <i class="fa fa-folder mobile-drawer-icon"></i>
            <span>{{ $t('m.NavBar_Resource') }}</span>
          </el-menu-item>
          <el-sub-menu index="mobile-about">
            <template #title>
              <i class="el-icon-info mobile-drawer-icon"></i>
              <span>{{ $t('m.NavBar_About') }}</span>
            </template>
            <el-menu-item index="/introduction">{{ $t('m.NavBar_Introduction') }}</el-menu-item>
            <el-menu-item index="/developer">{{ $t('m.NavBar_Developer') }}</el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-drawer>
    </template>
    
    <el-dialog
      v-model="modalVisible"
      width="370px"
      :class="[
        'dialog',
        {
          'auth-dialog': ['Login', 'Register', 'ResetPwd'].includes(
            modalStatus.mode
          ),
        },
      ]"
      :title="title"
      :close-on-click-modal="false"
    >
      <component :is="modalStatus.mode" v-if="modalVisible"></component>
      <template #footer>
        <div style="display: none"></div>
      </template>
    </el-dialog>
  </div>
</template>
<script>
import Login from '@/components/oj/common/Login';
import Register from '@/components/oj/common/Register';
import ResetPwd from '@/components/oj/common/ResetPassword';
import MsgSvg from '@/components/oj/msg/msgSvg';
import { mapGetters, mapActions } from 'vuex';
import Avatar from '@/components/common/Avatar.vue';
import api from '@/common/api';
import logoImage from '@/assets/logo.png'
export default {
  components: {
    Login,
    Register,
    ResetPwd,
    Avatar,
    MsgSvg,
  },
  created(){
    this.page_width();
    window.onresize = () => {
      this.page_width();
      this.setHiddenHeaderHeight();
    };
  },
  mounted() {
    this.switchMode();
    this.setHiddenHeaderHeight();
    if (this.isAuthenticated) {
      this.getUnreadMsgCount();
      this.msgTimer = setInterval(() => {
        this.getUnreadMsgCount();
      }, 120 * 1000);
    }
  },
  beforeUnmount() {
    clearInterval(this.msgTimer);
  },
  data() {
    return {
      mode:'defalut',
      centerDialogVisible: false,
      mobileNar: false,
      opendrawer: false,
      imgUrl: logoImage,
      avatarStyle:
        'display: inline-flex;width: 30px;height: 30px;border-radius: 50%;align-items: center;justify-content: center;text-align: center;user-select: none;',
    };
  },
  methods: {
    ...mapActions(['changeModalStatus']),
    page_width() {
      let screenWidth = window.innerWidth;
      if (screenWidth < 992) {
        this.mobileNar = true;
      } else {
        this.mobileNar = false;
      }
    },
    handleBtnClick(mode) {
      this.changeModalStatus({
        mode,
        visible: true,
      });
    },
    handleRoute(route) {
      // 电脑端导航栏路由跳转事件
      if (route && route.split('/')[1] != 'admin') {
        this.$router.push(route);
      } else {
        window.open('/admin/');
      }
    },
    handleCommand(route) {
      // 移动端导航栏路由跳转事件
      if (route && route.split('/')[1] != 'admin') {
        this.$router.push(route);
      } else {
        window.open('/admin/');
      }
    },
    closeMobileDrawer() {
      this.opendrawer = false;
    },
    getUnreadMsgCount() {
      api.getUnreadMsgCount().then((res) => {
        let data = res.data.data;
        this.$store.dispatch('updateUnreadMessageCount', data);
        let sumMsg =
          data.comment + data.reply + data.like + data.mine + data.sys;
        if (sumMsg > 0) {
          if (this.webLanguage == 'zh-CN') {
            this.$notify.info({
              title: '未读消息',
              message:
                '亲爱的【' +
                this.userInfo.username +
                '】，您有最新的' +
                sumMsg +
                '条未读消息，请注意查看！',
              position: 'bottom-right',
              duration: 5000,
            });
          } else {
            this.$notify.info({
              title: 'Unread Message',
              message:
                'Dear【' +
                this.userInfo.username +
                '】, you have the latest ' +
                sumMsg +
                ' unread messages. Please check them!',
              position: 'bottom-right',
              duration: 5000,
            });
          }
        }
      });
    },
    changeWebLanguage() {
      this.$store.commit('changeWebLanguage', { language: this.webLanguage == 'zh-CN' ? 'en-US' : 'zh-CN' });
    },
    setHiddenHeaderHeight(){
      if(!this.mobileNar){
        try {
          let headerHeight = document.getElementById('header').offsetHeight;
          document.getElementById('header-hidden').setAttribute('style','height:'+ headerHeight + 'px')
        } catch (e) {}
      }
    },
    switchMode(){
      if(this.$route.meta.fullScreenSource){
        this.mode = this.$route.meta.fullScreenSource;
      }else{
        this.mode = 'defalut';
      }
    },
    getTrainingHomePath(){
      let tid = this.$route.params.trainingID
      let gid = this.$route.params.groupID
      if(gid){
        return `/group/${gid}/training/${tid}`;
      }else{
        return `/training/${tid}`;
      }
    },
    getTrainingProblemListPath(){
      let tid = this.$route.params.trainingID
      let gid = this.$route.params.groupID
      if(gid){
        return `/group/${gid}/training/${tid}/problems`;
      }else{
        return `/training/${tid}/problems`;
      }
    }
  },
  computed: {
    ...mapGetters([
      'modalStatus',
      'userInfo',
      'isAuthenticated',
      'isAdminRole',
      'token',
      'websiteConfig',
      'unreadMessage',
      'webLanguage',
    ]),
    avatar() {
      return this.$store.getters.userInfo.avatar;
    },
    activeMenuName() {
      if (this.$route.path.split('/')[1] == 'submission-detail') {
        return '/status';
      } else if (this.$route.path.split('/')[1] == 'discussion-detail') {
        return '/discussion';
      }
      return '/' + this.$route.path.split('/')[1];
    },
    hasUnreadMessage() {
      return (
        this.unreadMessage.comment > 0 ||
        this.unreadMessage.reply > 0 ||
        this.unreadMessage.like > 0 ||
        this.unreadMessage.sys > 0 ||
        this.unreadMessage.mine > 0
      );
    },
    modalVisible: {
      get() {
        return this.modalStatus.visible;
      },
      set(value) {
        this.changeModalStatus({ visible: value });
      },
    },
    title: {
      get() {
        let ojName = this.websiteConfig.shortName
          ? this.websiteConfig.shortName
          : 'OJ';
        if (this.modalStatus.mode == 'ResetPwd') {
          return this.$t('m.Dialog_Reset_Password') + ' - ' + ojName;
        } else {
          return (
            this.$t('m.Dialog_' + this.modalStatus.mode) + ' - ' + ojName
          );
        }
      },
    },
  },
  watch: {
    isAuthenticated() {
      if (this.isAuthenticated) {
        if (this.msgTimer) {
          clearInterval(this.msgTimer);
        }
        this.getUnreadMsgCount();
        this.msgTimer = setInterval(() => {
          this.getUnreadMsgCount();
        }, 120 * 1000);
      } else {
        clearInterval(this.msgTimer);
      }
    },
    $route(){
      this.switchMode();
    }
  },
};
</script>
<style scoped>
@font-face {
  font-family: 'hoj-element-icons';
  src: url('../../../assets/fonts/element-icons.woff') format('woff');
  font-style: normal;
  font-weight: 400;
  font-display: block;
}
#header {
  min-width: 300px;
  position: fixed;
  top: 0;
  left: 0;
  height: auto;
  width: 100%;
  z-index: 2000;
  background-color: #fff;
  box-shadow: 0 1px 5px 0 rgba(0, 0, 0, 0.1);
}
#header-hidden {
  height: 61px;
}
.mobile-nav {
  align-items: center;
  background: #2196f3;
  color: #fff;
  display: flex;
  position: fixed;
  left: 0;
  top: 0;
  z-index: 2500;
  height: 56px;
  padding: 0 8px;
  width: 100%;
}
.mobile-nav-spacer {
  height: 56px;
}
.mobile-nav-title {
  background: transparent;
  border: 0;
  color: inherit;
  cursor: pointer;
  flex: 1;
  font-size: 18px;
  font-weight: 500;
  overflow: hidden;
  padding: 0 8px;
  text-align: left;
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
  font-size: 22px;
}
.mobile-message-button {
  position: relative;
}
.mobile-unread-dot {
  background: #f56c6c;
  border: 1px solid #fff;
  border-radius: 50%;
  height: 9px;
  position: absolute;
  right: 7px;
  top: 7px;
  width: 9px;
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
:global(.mobile-drawer .el-drawer__body) {
  padding: 0;
}
:global(.mobile-drawer .el-menu) {
  border-right: 0;
}

.logo {
  cursor: pointer;
  margin-left: 2%;
  margin-right: 2%;
  float: left;
  width: 139px;
  height: 42px;
  margin-top: 5px;
}
.el-dropdown-link {
  cursor: pointer;
  color: #409eff !important;
}
.el-icon-arrow-down {
  font-size: 18px;
}
.drop-menu {
  align-items: center;
  align-self: stretch;
  display: flex;
  float: right;
  height: 61px;
  margin-right: 30px;
  order: 3;
  position: relative;
  font-weight: 500;
  right: 10px;
  font-size: 18px;
}
.drop-avatar {
  float: right;
  margin-right: 20px;
  order: 2;
  position: relative;
  margin-top: 14px;
}
.drop-menu .el-icon-caret-bottom {
  margin-left: 4px;
}
.drop-msg {
  align-items: center;
  align-self: stretch;
  display: flex;
  float: right;
  font-size: 25px;
  height: 61px;
  margin-left: auto;
  margin-right: 10px;
  order: 1;
  position: relative;
}
.drop-menu .el-dropdown-link,
.drop-msg .el-dropdown-link {
  align-items: center;
  display: inline-flex;
  height: 100%;
  line-height: 1;
}
.drop-menu:focus,
.drop-msg:focus,
.drop-menu .el-dropdown-link:focus,
.drop-msg .el-dropdown-link:focus {
  outline: none;
}
.drop-msg-count {
  margin-left: 2px;
}
.btn-menu {
  font-size: 16px;
  float: right;
  margin-left: auto;
  margin-right: 10px;
  margin-top: 12px;
}
:deep(.auth-dialog.el-dialog) {
  --el-dialog-padding-primary: 0px;
  border-radius: 4px !important;
  padding: 0;
  text-align: center;
}
:deep(.auth-dialog .el-dialog__header) {
  box-sizing: border-box;
  height: 53px;
  padding: 17px 20px 11px;
}
:deep(.auth-dialog .el-dialog__body) {
  box-sizing: border-box;
  padding: 27px 18px 41px;
  width: 100%;
}
:deep(.auth-dialog .el-dialog__headerbtn) {
  height: 32px;
  right: 12px;
  top: 12px;
  width: 32px;
}
:deep(.auth-dialog .el-dialog__header .el-dialog__title) {
  font-size: 22px;
  font-weight: 600;
  font-family: Arial, Helvetica, sans-serif;
  line-height: 25px;
  color: #4e4e4e;
}
.el-sub-menu__title i {
  color: #495060 !important;
}
.el-menu-item {
  padding: 0 13px;
}
.navbar-centered-submenu-item {
  justify-content: center;
}
#header :deep(.el-menu--horizontal) {
  height: 61px;
}
#header :deep(.el-menu--horizontal > .el-menu-item),
#header :deep(.el-menu--horizontal > .el-sub-menu > .el-sub-menu__title) {
  height: 60px;
  font-size: 14px;
  font-weight: 400;
  line-height: 60px;
}
#header :deep(.el-menu--horizontal > .el-sub-menu > .el-sub-menu__title) {
  padding-left: 15px;
}
.el-menu-item:hover, .el-menu .el-menu-item:hover{
  border-bottom: 2px solid #2474b5 !important;
}
.el-menu .el-menu-item:hover, 
.el-menu .el-menu-item:hover i,
.el-sub-menu .el-sub-menu__title:hover,
.el-sub-menu .el-sub-menu__title:hover i{
  outline: 0 !important;
  color: #2E95FB !important;
  background: linear-gradient(270deg, #F2F7FC 0%, #FEFEFE 100%)!important;
  transition: all .2s ease;
}
.el-menu .el-menu-item.is-active, 
.el-menu .el-menu-item.is-active i,
.el-sub-menu.is-active,
.el-sub-menu.is-active i
{
  color: #2E95FB !important;
  background: linear-gradient(270deg, #F2F7FC 0%, #FEFEFE 100%)!important;
  transition: all .2s ease;
}
.el-menu--horizontal .el-menu .el-menu-item:hover, 
.el-sub-menu :deep(.el-sub-menu__title:hover) {
  color: #2E95FB !important;
  background: linear-gradient(270deg, #F2F7FC 0%, #FEFEFE 100%)!important;
}
.el-menu-item i {
  color: #495060;
}
.is-active .el-sub-menu__title i,
.is-active {
  color: #2196f3 !important;
}
.el-menu-item.is-active i {
  color: #2196f3 !important;
}
.navbar-icon{
  margin-right: 5px !important;
  width: 24px !important;
  height: 16px !important;
  flex: 0 0 24px;
  font-size: 14px !important;
  line-height: 16px !important;
  text-align: center !important;
}
.navbar-icon.legacy-navbar-icon {
  font-size: 18px !important;
}
.legacy-navbar-icon::before {
  font-family: 'hoj-element-icons' !important;
  font-style: normal;
  font-variant: normal;
  font-weight: 400 !important;
  line-height: 1;
  text-rendering: auto;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.legacy-navbar-icon.el-icon-s-home::before {
  content: '\e7b9' !important;
}
.legacy-navbar-icon.el-icon-s-grid::before {
  content: '\e7a6' !important;
}
.legacy-navbar-icon.el-icon-s-claim::before {
  content: '\e7ad' !important;
}
.legacy-navbar-icon.el-icon-trophy::before {
  content: '\e70d' !important;
}
.legacy-navbar-icon.el-icon-s-marketing::before {
  content: '\e7b1' !important;
}
.legacy-navbar-icon.el-icon-s-data::before {
  content: '\e7a8' !important;
}
.legacy-navbar-icon.el-icon-s-comment::before {
  content: '\e7af' !important;
}
.legacy-navbar-icon.el-icon-info::before {
  content: '\e7a1' !important;
}
.legacy-navbar-icon.el-icon-menu::before {
  content: '\e798' !important;
}
.legacy-navbar-icon.el-icon-message-solid::before {
  content: '\e799' !important;
}
.message-icon {
  font-size: 25px;
  vertical-align: middle;
}
</style>
