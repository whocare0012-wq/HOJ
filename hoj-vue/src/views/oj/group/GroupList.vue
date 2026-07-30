<template>
  <el-row :gutter="20">
    <el-col :span="24" style="margin-top: 10px; margin-bottom: 10px;">
      <el-card>
        <section>
          <span class="find-group">{{ $t('m.Search_Group') }}</span>
          <vxe-input
            v-model="query.keyword"
            :placeholder="$t('m.Enter_keyword')"
            type="search"
            size="medium"
            style="width:230px; margin-right: 1.7em"
            @keyup.enter="filterByKeyword"
            @search-click="filterByKeyword"
          ></vxe-input>
          <el-button
            class="hidden-xs-only"
            type="warning"
            :icon="legacyElementIcons['el-icon-search']"
            size="small"
            :disabled="!isAuthenticated"
            @click="handleOnlyMine(!query.onlyMine)"
          >
            {{ query.onlyMine ? $t('m.All_Group') : $t('m.My_Group') }}
          </el-button>
          <el-button
            class="hidden-sm-and-up"
            type="warning"
            :icon="legacyElementIcons['el-icon-search']"
            size="small"
            :disabled="!isAuthenticated"
            @click="handleOnlyMine(!query.onlyMine)"
          ></el-button>
          <el-button
            class="hidden-xs-only"
            type="primary"
            :icon="legacyElementIcons['el-icon-plus']"
            size="small"
            @click="toCreateGroup"
          >
            {{ $t('m.Create_Group') }}
          </el-button>
          <el-button
            class="hidden-sm-and-up"
            type="primary"
            :icon="legacyElementIcons['el-icon-plus']"
            size="small"
            @click="toCreateGroup"
          ></el-button>
        </section>
        <section>
          <b class="group-category">{{ $t('m.Group_Auth') }}</b>
          <div>
            <el-tag
              size="default"
              class="category-item"
              :effect="query.auth ? 'plain' : 'dark'"
              @click="filterByAuth(null)"
            >
              {{ $t('m.All') }}
            </el-tag>
            <el-tag
              size="default"
              class="category-item"
              v-for="(key, index) in GROUP_TYPE_REVERSE"
              :type="key.color"
              :effect="query.auth == index ? 'dark' : 'plain'"
              :key="index"
              @click="filterByAuth(index)"
            >
              {{ $t('m.Group_' + key.name) }}
            </el-tag>
          </div>
        </section>
      </el-card>
    </el-col>
    <el-col :span="24" v-loading="loading">
      <el-row :gutter="20">
        <el-col
          :xxl="4"
          :xl="6"
          :lg="8"
          :md="8"
          :sm="12"
          :xs="24"
          v-if="!groupList.length"
          style="margin-top: 10px; margin-bottom: 10px"
        >
          <el-card
            :body-style="{ padding: '0px' }"
            style="border-radius: 10px;"
          >
            <el-empty :description="$t('m.No_Groups')"></el-empty>
          </el-card>
        </el-col>
        <el-col
          :xxl="4"
          :xl="6"
          :lg="8"
          :md="8"
          :sm="12"
          :xs="24"
          v-for="group in groupList"
          :key="group.id"
          style="margin-top: 10px; margin-bottom: 10px"
        >
          <el-row :gutter="1">
            <el-col :span="7" style="text-align: center;">
              <el-card
                class="group-owner-card"
                :body-style="{ padding: '0px' }"
              >
                <template v-if="group.avatar">
                  <el-image
                    class="group-avatar"
                    :src="group.avatar"
                    @click="toGroup(group.id)"
                    fit="cover"
                  ></el-image>
                </template>
                <template v-else>
                  <el-image
                    class="group-avatar"
                    :src="defaultAvatar"
                    @click="toGroup(group.id)"
                    fit="cover"
                  ></el-image>
                </template>
                <el-link
                  class="group-owner-link"
                  type="primary"
                  underline="never"
                  @click="toUserHome(group.owner)"
                >
                  <el-icon class="group-owner-icon">
                    <component
                      :is="legacyElementIcons['el-icon-user-solid']"
                    />
                  </el-icon>
                  <span>{{ group.owner }}</span>
                </el-link>
              </el-card>
            </el-col>
            <el-col :span="17" :class="GROUP_TYPE_REVERSE[group.auth].name">
              <el-card
                class="group-info-card"
                :body-style="{ padding: '0px' }"
              >
                <template #header>
                  <div class="group-name-header">
                    <a class="group-name" @click="toGroup(group.id)">
                      <Marquee :val="group.name" :id="group.id"></Marquee>
                    </a>
                  </div>
                </template>
                <div class="group-brief">
                  <span>{{ group.brief }}</span>
                </div>
                <el-divider></el-divider>
                <div class="group-meta">
                  <span class="group-meta-main">
                    <span class="group-member-count">
                      <el-icon class="group-member-icon">
                        <component
                          :is="legacyElementIcons['el-icon-user-solid']"
                        />
                      </el-icon>
                      <span>× {{ group.memberCount }}</span>
                    </span>
                    <el-tooltip
                      :content="$t('m.' + GROUP_TYPE_REVERSE[group.auth].tips)"
                    >
                      <el-tag
                        class="group-auth"
                        size="default"
                        :type="GROUP_TYPE_REVERSE[group.auth].color"
                        effect="plain"
                        @click="filterByAuth(group.auth)"
                      >
                        {{
                          $t('m.Group_' + GROUP_TYPE_REVERSE[group.auth].name)
                        }}
                      </el-tag>
                    </el-tooltip>
                    <el-tooltip
                      v-if="!group.visible"
                      :content="$t('m.Group_Hidden_Tips')"
                    >
                      <el-tag
                        class="group-auth"
                        size="default"
                        type="primary"
                        effect="plain"
                      >
                        {{ $t('m.Group_Hidden') }}
                      </el-tag>
                    </el-tooltip>
                  </span>
                  <span class="group-date">
                    <el-icon class="group-date-icon"><Clock /></el-icon>
                    <span>{{
                      $filters.localtime(group.gmtCreate, 'YYYY-MM-DD')
                    }}</span>
                  </span>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </el-col>
      </el-row>
    </el-col>
    <Pagination
      :total="total"
      :page-size="query.limit"
      @on-change="currentChange"
      v-model:current="query.currentPage"
      @on-page-size-change="onPageSizeChange"
      style="margin-top: 10px; margin-bottom: 30px;"
      :layout="'prev, pager, next, sizes'"
      :pageSizes="[9, 15, 30, 60, 120]"
    ></Pagination>
    <el-dialog
      :title="$t('m.Create_Group')"
      v-model="showEditGroupDialog"
      :fullscreen="true"
      @open="onOpenEditDialog"
    >
      <el-form label-position="top" :model="group" :rules="rules" ref="group">
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
              <el-select v-model="group.auth">
                <el-tooltip
                  :content="$t('m.Group_Public_Tips')"
                  placement="right"
                >
                  <el-option
                    :label="$t('m.Group_Public')"
                    :value="1"
                  ></el-option>
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
              :label="$t('m.Group_Description')"
              required
              prop="description"
            >
              <Editor v-model:value="group.description"></Editor>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="danger" @click="showEditGroupDialog = false">{{
            $t('m.Cancel')
          }}</el-button>
          <el-button type="primary" @click="submitGroup">{{
            $t('m.OK')
          }}</el-button>
        </span>
      </template>
    </el-dialog>
  </el-row>
</template>

<script>
import { GROUP_TYPE_REVERSE } from '@/common/constants';
import api from '@/common/api';
import mMessage from '@/common/message';
import { mapGetters } from 'vuex';
import Pagination from '@/components/oj/common/Pagination';
import Editor from '@/components/admin/Editor';
import Marquee from '@/components/oj/common/Marquee';
import { Clock } from '@element-plus/icons-vue';
import defaultAvatarImage from '@/assets/default.jpg'
export default {
  name: 'GroupList',
  components: {
    Pagination,
    Editor,
    Marquee,
    Clock,
  },
  data() {
    return {
      showEditGroupDialog: false,
      query: {
        keyword: '',
        auth: 0,
        currentPage:1,
        limit:15,
        onlyMine: false,
      },
      total: 0,
      group: {
        avatar: '',
        name: '',
        shortName: '',
        brief: '',
        description: '',
        owner: '',
        auth: 1,
        visible: true,
      },
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
      backupGroup: null,
      groupList: [],
      loading: false,
      defaultAvatar: defaultAvatarImage,
    };
  },
  mounted() {
    this.init();
  },
  created() {
    this.GROUP_TYPE_REVERSE = Object.assign({}, GROUP_TYPE_REVERSE);
  },
  methods: {
    init() {
      let route = this.$route.query;
      this.query.auth = route.auth;
      this.query.keyword = route.keyword || '';
      this.query.onlyMine = route.onlyMine + '' == 'true' ? true : false;
      this.query.currentPage = route.currentPage || 1;
      this.query.limit = route.limit || 15;
      this.getGroupList();
    },
    onPageSizeChange(pageSize) {
      this.query.limit = pageSize;
      this.handleRouter();
    },
    currentChange(page) {
      this.query.currentPage = page;
      this.handleRouter();
    },
    filterByKeyword() {
      this.query.currentPage = 1;
      this.handleRouter();
    },
    filterByAuth(auth) {
      this.query.auth = auth;
      this.handleRouter();
    },
    handleOnlyMine(onlyMine) {
      this.query.onlyMine = onlyMine;
      this.handleRouter();
    },
    handleAuth(auth) {
      this.query.auth = auth;
      this.handleRouter();
    },
    handleRouter() {
      this.$router.push({
        path: this.$route.path,
        query: this.query,
      });
    },
    getGroupList() {
      this.loading = true;
      api.getGroupList(this.query.currentPage, this.query.limit, this.query).then(
        (res) => {
          this.groupList = res.data.data.records;
          this.total = res.data.data.total;
          this.loading = false;
        },
        (err) => {
          this.loading = false;
        }
      );
    },
    toGroup(groupID) {
      if (!this.isAuthenticated) {
        mMessage.warning(this.$t('m.Please_login_first'));
        this.$store.dispatch('changeModalStatus', { visible: true });
      } else {
        this.$router.push({
          name: 'GroupDetails',
          params: { groupID: groupID },
        });
      }
    },
    toUserHome(username) {
      this.$router.push({
        name: 'UserHome',
        query: { username: username },
      });
    },
    toCreateGroup() {
      if (!this.isAuthenticated) {
        mMessage.warning(this.$t('m.Please_login_first'));
        this.$store.dispatch('changeModalStatus', { visible: true });
      } else {
        if (this.backupGroup) {
          this.group = this.backupGroup;
        } else {
          this.group = {
            id: null,
            avatar: '',
            name: '',
            shortName: '',
            brief: '',
            description: '',
            owner: '',
            auth: 1,
            code: '',
            status: null,
            visible: true,
          };
        }
        this.showEditGroupDialog = true;
      }
    },
    onOpenEditDialog() {
      setTimeout(() => {
        if (document.createEvent) {
          let event = document.createEvent('HTMLEvents');
          event.initEvent('resize', true, true);
          window.dispatchEvent(event);
        } else if (document.createEventObject) {
          window.fireEvent('onresize');
        }
      }, 0);
    },
    submitGroup() {
      this.$refs['group'].validate((valid) => {
        if (valid) {
          let group = Object.assign({}, this.group);
          api.addGroup(group).then((res) => {
            mMessage.success(this.$t('m.Create_Successfully'));
            this.showEditGroupDialog = false;
            this.init();
          });
        }
      });
    },
  },
  computed: {
    ...mapGetters(['isAuthenticated']),
  },
  watch: {
    $route(newVal, oldVal) {
      if (newVal !== oldVal) {
        this.init();
      }
    },
    group(newVal, oldVal) {
      if (newVal != oldVal) {
        this.backupGroup = this.group;
      }
    },
  },
};
</script>

<style scoped>
section {
  display: flex;
  min-height: 3em;
  margin-bottom: 1em;
  align-items: center;
}
.find-group {
  margin-right: 1em;
  white-space: nowrap;
  font-size: 1.7em;
  margin-top: 0;
  font-family: inherit;
  font-weight: bold;
  line-height: 1.2;
  color: inherit;
}
.group-category {
  margin-right: 1.5em;
  font-weight: bolder;
  white-space: nowrap;
  font-size: 16px;
  margin-top: 8px;
}
.category-item {
  margin-right: 1em;
  margin-top: 0.5em;
  font-size: 14px;
}
.category-item:hover {
  cursor: pointer;
}
.group-name {
  font-size: 1.15rem;
  font-weight: 600;
}
.group-owner-card,
.group-info-card {
  height: 170px;
  overflow: hidden;
  border-radius: 10px;
}
.group-avatar {
  display: block;
  width: 100%;
  height: 134px;
  cursor: pointer;
}
.group-owner-link {
  width: 100%;
  height: 34px;
  font-size: 16px;
}
:deep(.group-owner-link .el-link__inner) {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.group-owner-icon {
  font-size: 15px;
}
.group-name-header {
  height: 24px;
}

:deep(.Public .el-card) {
  border-color: rgba(103, 194, 58, 0.5);
}

:deep(.Public .el-card__header) {
  background-color: rgba(103, 194, 58, 0.2);
}

.Public .group-name {
  color: rgb(103, 194, 58);
}

:deep(.Protected .el-card) {
  border-color: rgba(230, 162, 60, 0.5);
}

:deep(.Protected .el-card__header) {
  background-color: rgba(230, 162, 60, 0.2);
}

.Protected .group-name {
  color: rgb(230, 162, 60);
}

:deep(.Private .el-card) {
  border-color: rgba(245, 108, 108, 0.5);
}

:deep(.Private .el-card__header) {
  background-color: rgba(245, 108, 108, 0.2);
}

.Private .group-name {
  color: rgb(245, 108, 108);
}

.group-brief {
  height: 86px;
  font-size: 14px;
  padding: 0 10px;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  overflow: hidden;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.group-meta {
  align-items: center;
  display: flex;
  font-size: 14px;
  gap: 4px;
  justify-content: space-between;
  min-width: 0;
  padding: 3.5px;
}
.group-meta-main {
  align-items: center;
  display: flex;
  min-width: 0;
}
.group-member-count {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  white-space: nowrap;
}
.group-member-icon {
  font-size: 14px;
}
.group-date {
  align-items: center;
  display: inline-flex;
  flex: none;
  gap: 3px;
  font-size: 16px;
  line-height: 20px;
  white-space: nowrap;
}
.group-date-icon {
  font-size: 16px;
}
.group-auth {
  margin-left: 3px;
  margin-top: -1px;
  font-size: 14px;
}
.group-auth:hover {
  cursor: pointer;
}
:deep(.el-card__header) {
  padding: 10px;
  margin-bottom: 4px;
}
:deep(.el-divider--horizontal) {
  margin: 0;
}
</style>
