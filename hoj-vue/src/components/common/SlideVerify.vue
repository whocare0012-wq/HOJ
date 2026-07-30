<template>
  <div class="slide-verify" :style="{ width: `${w}px` }">
    <div class="slide-verify__panel" :style="{ height: `${h}px` }">
      <span class="slide-verify__hint">{{ sliderText }}</span>
    </div>
    <div class="slide-verify__track">
      <div
        class="slide-verify__progress"
        :class="{ 'is-complete': complete }"
        :style="{ width: `${position + handleWidth}px` }"
      ></div>
      <span class="slide-verify__label">{{ complete ? '✓' : sliderText }}</span>
      <button
        class="slide-verify__handle"
        :class="{ 'is-complete': complete }"
        :style="{ transform: `translateX(${position}px)` }"
        type="button"
        role="slider"
        :aria-label="sliderText"
        aria-valuemin="0"
        :aria-valuemax="maxPosition"
        :aria-valuenow="position"
        @pointerdown="startDrag"
        @keydown="handleKeydown"
      >
        {{ complete ? '✓' : '›' }}
      </button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'SlideVerify',
  emits: ['again', 'success'],
  props: {
    accuracy: { type: Number, default: 3 },
    h: { type: Number, default: 100 },
    imgs: { type: Array, default: () => [] },
    l: { type: Number, default: 42 },
    r: { type: Number, default: 10 },
    sliderText: { type: String, default: 'Slide to verify' },
    w: { type: Number, default: 325 }
  },
  data() {
    return {
      complete: false,
      dragging: false,
      dragStart: 0,
      handleWidth: 42,
      position: 0,
      startedAt: 0
    }
  },
  computed: {
    maxPosition() {
      return Math.max(0, this.w - this.handleWidth)
    }
  },
  beforeUnmount() {
    this.removePointerListeners()
  },
  methods: {
    startDrag(event) {
      if (this.complete) return
      event.preventDefault()
      this.dragging = true
      this.dragStart = event.clientX - this.position
      this.startedAt = performance.now()
      window.addEventListener('pointermove', this.moveDrag)
      window.addEventListener('pointerup', this.finishDrag)
      window.addEventListener('pointercancel', this.cancelDrag)
    },
    moveDrag(event) {
      if (!this.dragging) return
      this.position = Math.min(
        this.maxPosition,
        Math.max(0, event.clientX - this.dragStart)
      )
    },
    finishDrag() {
      if (!this.dragging) return
      this.dragging = false
      this.removePointerListeners()
      if (this.position >= this.maxPosition - this.accuracy) {
        this.completeVerification()
      } else {
        this.position = 0
        this.$emit('again')
      }
    },
    cancelDrag() {
      this.dragging = false
      this.removePointerListeners()
      this.position = 0
    },
    handleKeydown(event) {
      if (this.complete) return
      if (event.key === 'ArrowRight') {
        event.preventDefault()
        if (!this.startedAt) this.startedAt = performance.now()
        this.position = Math.min(this.maxPosition, this.position + 20)
        if (this.position >= this.maxPosition - this.accuracy) {
          this.completeVerification()
        }
      } else if (event.key === 'Home' || event.key === 'Escape') {
        event.preventDefault()
        this.reset()
      } else if (event.key === 'End') {
        event.preventDefault()
        if (!this.startedAt) this.startedAt = performance.now()
        this.position = this.maxPosition
        this.completeVerification()
      }
    },
    completeVerification() {
      this.complete = true
      this.position = this.maxPosition
      const elapsed = Math.max(0, performance.now() - (this.startedAt || performance.now()))
      this.$emit('success', elapsed)
    },
    removePointerListeners() {
      window.removeEventListener('pointermove', this.moveDrag)
      window.removeEventListener('pointerup', this.finishDrag)
      window.removeEventListener('pointercancel', this.cancelDrag)
    },
    reset() {
      this.complete = false
      this.dragging = false
      this.position = 0
      this.startedAt = 0
      this.removePointerListeners()
    }
  }
}
</script>

<style scoped>
.slide-verify {
  max-width: 100%;
  user-select: none;
}

.slide-verify__panel {
  align-items: center;
  background:
    radial-gradient(circle at 20% 30%, rgba(64, 158, 255, 0.24), transparent 28%),
    radial-gradient(circle at 75% 65%, rgba(103, 194, 58, 0.2), transparent 30%),
    linear-gradient(135deg, #f5f7fa, #e9eef5);
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  color: #909399;
  display: flex;
  justify-content: center;
  margin-bottom: 10px;
  overflow: hidden;
}

.slide-verify__hint {
  font-size: 13px;
  letter-spacing: 0.04em;
}

.slide-verify__track {
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  box-sizing: border-box;
  height: 42px;
  overflow: hidden;
  position: relative;
}

.slide-verify__progress {
  background: rgba(64, 158, 255, 0.2);
  height: 100%;
  left: 0;
  position: absolute;
  top: 0;
}

.slide-verify__progress.is-complete {
  background: rgba(103, 194, 58, 0.24);
}

.slide-verify__label {
  color: #909399;
  font-size: 13px;
  left: 42px;
  line-height: 40px;
  pointer-events: none;
  position: absolute;
  right: 0;
  text-align: center;
}

.slide-verify__handle {
  align-items: center;
  background: #fff;
  border: 0;
  border-right: 1px solid #dcdfe6;
  box-shadow: 1px 0 3px rgba(0, 0, 0, 0.08);
  color: #409eff;
  cursor: grab;
  display: flex;
  font-size: 28px;
  height: 40px;
  justify-content: center;
  left: 0;
  padding: 0;
  position: absolute;
  top: 0;
  touch-action: none;
  transition: background-color 0.15s ease;
  width: 42px;
}

.slide-verify__handle:focus-visible {
  outline: 2px solid #409eff;
  outline-offset: -2px;
}

.slide-verify__handle.is-complete {
  background: #67c23a;
  color: #fff;
  cursor: default;
}
</style>
