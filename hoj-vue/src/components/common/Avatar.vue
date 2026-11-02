<template>
  <span
    class="hoj-avatar"
    :class="{ 'is-inline': inline, 'is-square': shape === 'square' }"
    :style="avatarStyle"
    role="img"
    :aria-label="username || 'avatar'"
  >
    <img
      v-if="src && !imageFailed"
      class="hoj-avatar__image"
      :src="displaySrc"
      :alt="username || 'avatar'"
      loading="lazy"
      decoding="async"
      @error="onImageError"
    />
    <span v-else class="hoj-avatar__initials">{{ initials }}</span>
  </span>
</template>

<script>
// 站内上传的图片（UUID 文件名）支持 ?w= 缩略图，见后端 PublicImageThumbnailFilter
const THUMBNAIL_SOURCE = /^\/api\/public\/img\/[A-Za-z0-9_-]+\.(jpe?g|png)$/i
const THUMBNAIL_WIDTHS = [64, 128, 256]

const backgroundPalette = [
  '#F44336', '#FF4081', '#9C27B0', '#673AB7',
  '#3F51B5', '#2196F3', '#03A9F4', '#00BCD4',
  '#009688', '#4CAF50', '#8BC34A', '#CDDC39',
  '#FFC107', '#FF9800', '#FF5722', '#795548',
  '#9E9E9E', '#607D8B'
]

export default {
  name: 'Avatar',
  props: {
    backgroundColor: { type: String, default: '' },
    color: { type: String, default: '#fff' },
    inline: { type: Boolean, default: false },
    shape: { type: String, default: 'circle' },
    size: { type: [Number, String], default: 50 },
    src: { type: String, default: '' },
    username: { type: String, default: '' }
  },
  data() {
    return {
      imageFailed: false,
      thumbnailFailed: false
    }
  },
  computed: {
    displaySrc() {
      if (this.thumbnailFailed || !THUMBNAIL_SOURCE.test(this.src || '')) {
        return this.src
      }
      const ratio = typeof window !== 'undefined' && window.devicePixelRatio > 1 ? 2 : 1
      const needed = (Number.parseFloat(this.size) || 50) * ratio
      const width = THUMBNAIL_WIDTHS.find((w) => w >= needed) || THUMBNAIL_WIDTHS[THUMBNAIL_WIDTHS.length - 1]
      return `${this.src}?w=${width}`
    },
    avatarStyle() {
      const size = typeof this.size === 'number' ? `${this.size}px` : this.size
      return {
        backgroundColor: this.backgroundColor || this.fallbackColor,
        color: this.color,
        fontSize: `${Math.max(11, Number.parseFloat(this.size) * 0.4 || 20)}px`,
        height: size,
        width: size
      }
    },
    fallbackColor() {
      return backgroundPalette[(this.username || '').length % backgroundPalette.length]
    },
    initials() {
      const parts = (this.username || '?').split(/[ -]/).filter(Boolean)
      let value = parts.map((part) => part.charAt(0)).join('')
      if (value.length > 3 && /[A-Z]/.test(value)) {
        value = value.replace(/[a-z]+/g, '')
      }
      return value.slice(0, 3).toUpperCase()
    }
  },
  methods: {
    onImageError() {
      // 缩略图失败（如后端尚未升级）先退回原图，原图也失败才显示首字母
      if (this.displaySrc !== this.src) {
        this.thumbnailFailed = true
      } else {
        this.imageFailed = true
      }
    }
  },
  watch: {
    src() {
      this.imageFailed = false
      this.thumbnailFailed = false
    }
  }
}
</script>

<style scoped>
.hoj-avatar {
  align-items: center;
  border-radius: 50%;
  box-sizing: border-box;
  display: inline-flex;
  flex: 0 0 auto;
  justify-content: center;
  line-height: 1;
  overflow: hidden;
  vertical-align: middle;
}

.hoj-avatar.is-inline {
  display: inline-flex;
}

.hoj-avatar.is-square {
  border-radius: 4px;
}

.hoj-avatar__image {
  height: 100%;
  object-fit: cover;
  width: 100%;
}

.hoj-avatar__initials {
  font-size: 1em;
  font-weight: bold;
  user-select: none;
}
</style>
