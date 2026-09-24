<template>
  <main class="blockly-page">
    <h1>{{ $t('m.Blockly_Editor') }}</h1>
    <p>{{ $t('m.Blockly_Standalone_Hint') }}</p>
    <BlocklyWorkspace v-model:state="state" :height="620" @generated="code = $event" />
  </main>
</template>

<script>
import storage from '@/common/storage'
import BlocklyWorkspace from '@/components/oj/common/BlocklyWorkspace.vue'

export default {
  name: 'BlocklyStandalone',
  components: { BlocklyWorkspace },
  data() {
    return { state: null, code: '', draftKey: '' }
  },
  created() {
    const draft = this.$route.query.draft
    if (typeof draft !== 'string' || !draft.startsWith('hojBlocklyWindowDraft:')) return
    this.draftKey = draft
    try {
      this.state = storage.get(draft)?.state || null
    } catch (error) {
      this.state = null
    }
  },
  watch: {
    state(newState) {
      if (this.draftKey) storage.set(this.draftKey, { state: newState })
    },
  },
}
</script>

<style scoped>
.blockly-page { max-width: 1500px; margin: 24px auto; padding: 0 24px 30px; }
.blockly-page h1 { margin-bottom: 6px; font-size: 24px; }
.blockly-page p { color: #606266; }
@media (max-width: 768px) { .blockly-page { padding: 0 12px 24px; } }
</style>
