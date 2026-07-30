<template>
  <div>
    <el-alert
      type="success"
      :closable="false"
      center
      class="msg-title"
      effect="dark"
    >
      <template #title>
        <span
          ><i class="el-icon-s-promotion">
            {{ $t('m.Message_Center') }}</i
          ></span
        >
      </template>
    </el-alert>
    <el-tabs
      tab-position="left"
      type="border-card"
      style="min-height: 500px;"
      v-model="route_name"
      @tab-click="handleRouter"
    >
      <el-tab-pane name="DiscussMsg">
        <template #label>
          <span>
            <span>{{ $t('m.DiscussMsg') }}</span>
            <span style=" margin-left: 2px;" v-if="unreadMessage.comment > 0">
              <MsgSvg :total="unreadMessage.comment"></MsgSvg>
            </span>
          </span>
        </template>
        <router-view v-if="route_name === 'DiscussMsg'" v-slot="{ Component }">
          <transition name="fadeInUp" mode="out-in">
            <component :is="Component" :key="$route.name"></component>
          </transition>
        </router-view>
      </el-tab-pane>
      <el-tab-pane name="ReplyMsg">
        <template #label>
          <span>
            <span>{{ $t('m.ReplyMsg') }}</span>
            <span style=" margin-left: 2px;" v-if="unreadMessage.reply > 0">
              <MsgSvg :total="unreadMessage.reply"></MsgSvg>
            </span>
          </span>
        </template>
        <router-view v-if="route_name === 'ReplyMsg'" v-slot="{ Component }">
          <transition name="fadeInUp" mode="out-in">
            <component :is="Component" :key="$route.name"></component>
          </transition>
        </router-view>
      </el-tab-pane>
      <el-tab-pane name="LikeMsg">
        <template #label>
          <span>
            <span>{{ $t('m.LikeMsg') }}</span>
            <span style=" margin-left: 2px;" v-if="unreadMessage.like > 0">
              <MsgSvg :total="unreadMessage.like"></MsgSvg>
            </span>
          </span>
        </template>
        <router-view v-if="route_name === 'LikeMsg'" v-slot="{ Component }">
          <transition name="fadeInUp" mode="out-in">
            <component :is="Component" :key="$route.name"></component>
          </transition>
        </router-view>
      </el-tab-pane>
      <el-tab-pane name="SysMsg">
        <template #label>
          <span>
            <span>{{ $t('m.SysMsg') }}</span>
            <span style=" margin-left: 2px;" v-if="unreadMessage.sys > 0">
              <MsgSvg :total="unreadMessage.sys"></MsgSvg>
            </span>
          </span>
        </template>
        <router-view v-if="route_name === 'SysMsg'" v-slot="{ Component }">
          <transition name="fadeInUp" mode="out-in">
            <component :is="Component" :key="$route.name"></component>
          </transition>
        </router-view>
      </el-tab-pane>
      <el-tab-pane name="MineMsg">
        <template #label>
          <span>
            <span>{{ $t('m.MineMsg') }}</span>
            <span style=" margin-left: 2px;" v-if="unreadMessage.mine > 0">
              <MsgSvg :total="unreadMessage.mine"></MsgSvg>
            </span>
          </span>
        </template>
        <router-view v-if="route_name === 'MineMsg'" v-slot="{ Component }">
          <transition name="fadeInUp" mode="out-in">
            <component :is="Component" :key="$route.name"></component>
          </transition>
        </router-view>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import { mapGetters } from 'vuex';
import MsgSvg from '@/components/oj/msg/msgSvg';
export default {
  components: {
    MsgSvg,
  },
  data() {
    return {
      route_name: 'DiscussMsg',
    };
  },
  mounted() {
    this.route_name = this.$route.name;
    if (this.route_name === 'Message') {
      this.route_name = 'DiscussMsg';
    }
    this.$router.push({ name: this.route_name });
  },
  methods: {
    handleRouter(tab) {
      const name = tab?.paneName || tab?.props?.name || tab?.name;
      if (name && name !== this.$route.name) {
        this.$router.push({ name: name });
      }
    },
  },
  computed: {
    ...mapGetters(['unreadMessage']),
  },
};
</script>

<style scoped>
.msg-title {
  background-image: linear-gradient(135deg, #2afadf 10%, #4c83ff 100%);
}
:deep(.el-alert__title) {
  font-size: 18px !important;
  line-height: 18px !important;
}
:deep(.el-tabs__item) {
  text-align: center !important;
}
:deep(.el-tabs__item) {
  padding: 0 40px;
  line-height: 53px;
  height: 53px;
  font-weight: 700;
}
:deep(.el-card__body) {
  padding: 15px;
  padding-bottom: 10px;
}
@media only screen and (max-width: 767px) {
  :deep(.el-tabs__item) {
    padding: 0 10px;
  }
  :deep(.el-tabs__content) {
    padding: 12px;
    padding-left: 0px !important;
  }
}
</style>
