<template>
  <div id="particles-js">
    <canvas ref="canvas" class="particles-canvas"></canvas>
  </div>
</template>

<script>
export default {
  name: 'VueParticles',
  props: {
    color: { type: String, default: '#dedede' },
    particleOpacity: { type: Number, default: 0.7 },
    particlesNumber: { type: Number, default: 80 },
    shapeType: { type: String, default: 'circle' },
    particleSize: { type: Number, default: 4 },
    linesColor: { type: String, default: '#dedede' },
    linesWidth: { type: Number, default: 1 },
    lineLinked: { type: Boolean, default: true },
    lineOpacity: { type: Number, default: 0.4 },
    linesDistance: { type: Number, default: 150 },
    moveSpeed: { type: Number, default: 3 },
    hoverEffect: { type: Boolean, default: true },
    hoverMode: { type: String, default: 'grab' },
    clickEffect: { type: Boolean, default: true },
    clickMode: { type: String, default: 'push' }
  },
  data() {
    return {
      animationFrame: 0,
      canvasHeight: 0,
      canvasWidth: 0,
      context: null,
      mouse: null,
      particles: []
    }
  },
  mounted() {
    this.$nextTick(() => {
      this.initializeParticles()
      window.addEventListener('resize', this.resizeCanvas)
    })
  },
  beforeUnmount() {
    cancelAnimationFrame(this.animationFrame)
    window.removeEventListener('resize', this.resizeCanvas)
  },
  methods: {
    initializeParticles() {
      const canvas = this.$refs.canvas
      this.context = canvas.getContext('2d')
      canvas.addEventListener('mousemove', this.handleMouseMove)
      canvas.addEventListener('mouseleave', this.handleMouseLeave)
      canvas.addEventListener('click', this.handleClick)
      this.resizeCanvas()
      this.particles = Array.from(
        { length: this.particlesNumber },
        () => this.createParticle()
      )
      this.animate()
    },
    resizeCanvas() {
      const canvas = this.$refs.canvas
      if (!canvas || !this.context) return
      const rect = this.$el.getBoundingClientRect()
      const ratio = Math.min(window.devicePixelRatio || 1, 2)
      this.canvasWidth = rect.width || window.innerWidth
      this.canvasHeight = rect.height || window.innerHeight
      canvas.width = Math.round(this.canvasWidth * ratio)
      canvas.height = Math.round(this.canvasHeight * ratio)
      canvas.style.width = `${this.canvasWidth}px`
      canvas.style.height = `${this.canvasHeight}px`
      this.context.setTransform(ratio, 0, 0, ratio, 0, 0)
    },
    createParticle(x = Math.random() * this.canvasWidth, y = Math.random() * this.canvasHeight) {
      const angle = Math.random() * Math.PI * 2
      const velocity = Math.max(this.moveSpeed, 0) * 0.12
      return {
        x,
        y,
        radius: Math.max(1, Math.random() * this.particleSize),
        vx: Math.cos(angle) * velocity,
        vy: Math.sin(angle) * velocity
      }
    },
    handleMouseMove(event) {
      const rect = this.$refs.canvas.getBoundingClientRect()
      this.mouse = { x: event.clientX - rect.left, y: event.clientY - rect.top }
    },
    handleMouseLeave() {
      this.mouse = null
    },
    handleClick(event) {
      if (!this.clickEffect || this.clickMode !== 'push') return
      const rect = this.$refs.canvas.getBoundingClientRect()
      const x = event.clientX - rect.left
      const y = event.clientY - rect.top
      for (let index = 0; index < 4; index += 1) {
        this.particles.push(this.createParticle(x, y))
      }
    },
    drawParticle(particle) {
      const context = this.context
      context.globalAlpha = this.particleOpacity
      context.fillStyle = this.color
      context.beginPath()
      if (this.shapeType === 'square') {
        context.rect(
          particle.x - particle.radius,
          particle.y - particle.radius,
          particle.radius * 2,
          particle.radius * 2
        )
      } else {
        context.arc(particle.x, particle.y, particle.radius, 0, Math.PI * 2)
      }
      context.fill()
    },
    drawLine(first, second, opacity = this.lineOpacity) {
      const context = this.context
      context.globalAlpha = opacity
      context.strokeStyle = this.linesColor
      context.lineWidth = this.linesWidth
      context.beginPath()
      context.moveTo(first.x, first.y)
      context.lineTo(second.x, second.y)
      context.stroke()
    },
    animate() {
      const context = this.context
      context.clearRect(0, 0, this.canvasWidth, this.canvasHeight)

      this.particles.forEach((particle, index) => {
        particle.x += particle.vx
        particle.y += particle.vy
        if (particle.x < 0) particle.x = this.canvasWidth
        if (particle.x > this.canvasWidth) particle.x = 0
        if (particle.y < 0) particle.y = this.canvasHeight
        if (particle.y > this.canvasHeight) particle.y = 0

        this.drawParticle(particle)
        if (this.lineLinked) {
          for (let nextIndex = index + 1; nextIndex < this.particles.length; nextIndex += 1) {
            const next = this.particles[nextIndex]
            const distance = Math.hypot(particle.x - next.x, particle.y - next.y)
            if (distance < this.linesDistance) {
              this.drawLine(
                particle,
                next,
                this.lineOpacity * (1 - distance / this.linesDistance)
              )
            }
          }
        }
        if (this.hoverEffect && this.hoverMode === 'grab' && this.mouse) {
          const distance = Math.hypot(particle.x - this.mouse.x, particle.y - this.mouse.y)
          if (distance < 140) {
            this.drawLine(particle, this.mouse, 1 - distance / 140)
          }
        }
      })

      context.globalAlpha = 1
      this.animationFrame = requestAnimationFrame(this.animate)
    }
  }
}
</script>

<style scoped>
.particles-canvas {
  display: block;
  height: 100%;
  width: 100%;
}
</style>
