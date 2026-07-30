<template>
  <div
    class="rank-chart-actions"
    role="toolbar"
    :aria-label="$t('m.Rank_Chart_Tools')"
  >
    <button
      type="button"
      class="rank-chart-action"
      :title="$t('m.Rank_Data_View')"
      :aria-label="$t('m.Rank_Data_View')"
      @click="$emit('data-view')"
    ></button>
    <button
      type="button"
      class="rank-chart-action"
      :title="$t('m.Rank_Line_Chart')"
      :aria-label="$t('m.Rank_Line_Chart')"
      :aria-pressed="mode === 'line'"
      @click="$emit('change-mode', 'line')"
    ></button>
    <button
      type="button"
      class="rank-chart-action"
      :title="$t('m.Rank_Bar_Chart')"
      :aria-label="$t('m.Rank_Bar_Chart')"
      :aria-pressed="mode === 'bar'"
      @click="$emit('change-mode', 'bar')"
    ></button>
    <button
      v-if="supportsStack"
      type="button"
      class="rank-chart-action"
      :title="$t('m.Rank_Stack_Chart')"
      :aria-label="$t('m.Rank_Stack_Chart')"
      :aria-pressed="mode === 'stack'"
      @click="$emit('change-mode', 'stack')"
    ></button>
    <button
      type="button"
      class="rank-chart-action"
      :title="$t('m.save_as_image')"
      :aria-label="$t('m.save_as_image')"
      @click="$emit('save-image')"
    ></button>
  </div>
</template>

<script>
export default {
  name: 'rank-chart-actions',
  props: {
    mode: {
      type: String,
      default: 'bar',
    },
    supportsStack: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['data-view', 'change-mode', 'save-image'],
};
</script>

<style scoped>
.rank-chart-actions {
  display: flex;
  gap: 10px;
  padding: 15px;
  pointer-events: none;
  position: absolute;
  right: 8%;
  top: 5%;
  z-index: 7;
}

.rank-chart-action {
  appearance: none;
  background: transparent;
  border: 0;
  cursor: pointer;
  flex: 0 0 15px;
  height: 15px;
  margin: 0;
  outline: none;
  padding: 0;
  pointer-events: auto;
  width: 15px;
}

.rank-chart-action:focus-visible {
  border-radius: 2px;
  outline: 2px solid #409eff;
  outline-offset: 2px;
}
</style>
