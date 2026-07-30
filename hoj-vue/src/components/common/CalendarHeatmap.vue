<template>
  <div class="calendar-heatmap" data-calendar-heatmap>
    <svg
      class="vch__wrapper"
      :viewBox="viewBox"
      role="img"
      :aria-label="accessibleLabel"
      preserveAspectRatio="xMinYMin meet"
    >
      <g
        class="vch__months__labels__wrapper"
        :transform="`translate(${LEFT_SECTION_WIDTH}, 0)`"
      >
        <text
          v-for="month in monthLabels"
          :key="`${month.year}-${month.month}-${month.weekIndex}`"
          class="vch__month__label"
          :x="month.weekIndex * SQUARE_STEP"
          :y="SQUARE_SIZE"
        >
          {{ localized.months[month.month] }}
        </text>
      </g>

      <g
        class="vch__days__labels__wrapper"
        :transform="`translate(0, ${TOP_SECTION_HEIGHT})`"
      >
        <text
          v-for="dayIndex in visibleDayLabelIndexes"
          :key="dayIndex"
          class="vch__day__label"
          x="0"
          :y="(dayIndex * SQUARE_STEP) + SQUARE_SIZE"
        >
          {{ localized.days[dayIndex] }}
        </text>
      </g>

      <g
        class="vch__year__wrapper"
        :transform="`translate(${LEFT_SECTION_WIDTH}, ${TOP_SECTION_HEIGHT})`"
      >
        <g
          v-for="(week, weekIndex) in calendar"
          :key="weekIndex"
          class="vch__week__wrapper"
          :transform="`translate(${weekIndex * SQUARE_STEP}, 0)`"
        >
          <rect
            v-for="day in week"
            :key="day.key"
            class="vch__day__square"
            :class="{ 'vch__day__square--outside': !day.visible }"
            :transform="`translate(0, ${day.dayIndex * SQUARE_STEP})`"
            :width="SQUARE_SIZE"
            :height="SQUARE_SIZE"
            :fill="day.visible ? normalizedRangeColor[day.colorIndex] : 'transparent'"
            :aria-label="day.visible ? tooltipText(day) : undefined"
            :data-date="day.visible ? day.key : undefined"
            :data-count="day.visible ? day.count : undefined"
            rx="2"
            ry="2"
            @click="day.visible && $emit('day-click', day)"
          >
            <title v-if="day.visible">{{ tooltipText(day) }}</title>
          </rect>
        </g>
      </g>

      <g
        class="vch__legend__wrapper"
        :transform="`translate(${legendX}, ${viewBoxHeight - BOTTOM_SECTION_HEIGHT})`"
      >
        <text x="-25" :y="SQUARE_SIZE + 1">{{ localized.less }}</text>
        <rect
          v-for="(color, index) in normalizedRangeColor"
          :key="`${color}-${index}`"
          :fill="color"
          :width="SQUARE_SIZE"
          :height="SQUARE_SIZE"
          :x="SQUARE_STEP * index"
          y="5"
          rx="2"
          ry="2"
        />
        <text
          :x="SQUARE_STEP * normalizedRangeColor.length + 1"
          :y="SQUARE_SIZE + 1"
        >
          {{ localized.more }}
        </text>
      </g>
    </svg>
  </div>
</template>

<script>
import {
  DEFAULT_CALENDAR_LOCALE,
  DEFAULT_RANGE_COLOR,
  DAYS_IN_WEEK,
  buildCalendarHeatmap,
  calendarDateKey,
  getCalendarMonthLabels,
} from '@/common/calendar-heatmap.mjs'

export default {
  name: 'CalendarHeatmap',
  emits: ['day-click'],
  props: {
    endDate: {
      required: true,
      type: [String, Number, Date],
    },
    max: {
      default: null,
      type: Number,
    },
    rangeColor: {
      default: () => DEFAULT_RANGE_COLOR,
      type: Array,
    },
    values: {
      required: true,
      type: Array,
    },
    locale: {
      default: null,
      type: Object,
    },
    tooltipUnit: {
      default: 'contributions',
      type: String,
    },
  },
  data() {
    return {
      SQUARE_SIZE: 10,
      SQUARE_STEP: 12,
      TOP_SECTION_HEIGHT: 15,
      BOTTOM_SECTION_HEIGHT: 15,
      LEFT_SECTION_WIDTH: 25,
      visibleDayLabelIndexes: [1, 3, 5],
    }
  },
  computed: {
    normalizedEndDate() {
      return this.heatmap.endDate
    },
    startDate() {
      return this.heatmap.startDate
    },
    normalizedRangeColor() {
      return this.rangeColor.length >= 2 ? this.rangeColor : DEFAULT_RANGE_COLOR
    },
    localized() {
      return {
        months: this.locale?.months?.length === 12 ? this.locale.months : DEFAULT_CALENDAR_LOCALE.months,
        days: this.locale?.days?.length === 7 ? this.locale.days : DEFAULT_CALENDAR_LOCALE.days,
        on: this.locale?.on || DEFAULT_CALENDAR_LOCALE.on,
        less: this.locale?.less || DEFAULT_CALENDAR_LOCALE.less,
        more: this.locale?.more || DEFAULT_CALENDAR_LOCALE.more,
      }
    },
    heatmap() {
      return buildCalendarHeatmap({
        endDate: this.endDate,
        values: this.values,
        max: this.max,
        rangeColorLength: this.normalizedRangeColor.length,
      })
    },
    calendar() {
      return this.heatmap.calendar
    },
    monthLabels() {
      return getCalendarMonthLabels(this.calendar)
    },
    viewBoxWidth() {
      return this.LEFT_SECTION_WIDTH + (this.SQUARE_STEP * this.calendar.length) + 2
    },
    viewBoxHeight() {
      return this.TOP_SECTION_HEIGHT + (this.SQUARE_STEP * DAYS_IN_WEEK) + 2 + this.BOTTOM_SECTION_HEIGHT
    },
    viewBox() {
      return `0 0 ${this.viewBoxWidth} ${this.viewBoxHeight}`
    },
    legendX() {
      return this.viewBoxWidth - (this.SQUARE_STEP * this.normalizedRangeColor.length) - 30
    },
    accessibleLabel() {
      return `${this.localized.less} to ${this.localized.more}: ${calendarDateKey(this.startDate)} to ${calendarDateKey(this.normalizedEndDate)}`
    },
  },
  methods: {
    tooltipText(day) {
      const month = this.localized.months[day.date.getMonth()]
      return `${day.count} ${this.tooltipUnit} ${this.localized.on} ${month} ${day.date.getDate()}, ${day.date.getFullYear()}`
    },
  },
}
</script>

<style scoped>
.calendar-heatmap {
  min-width: 0;
  overflow-x: auto;
}

svg.vch__wrapper {
  display: block;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu,
    Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
  height: auto;
  line-height: 10px;
  min-width: 620px;
  width: 100%;
}

.vch__month__label {
  font-size: 10px;
}

.vch__day__label,
.vch__legend__wrapper text {
  font-size: 9px;
}

.vch__month__label,
.vch__day__label,
.vch__legend__wrapper text {
  fill: #767676;
}

rect.vch__day__square:not(.vch__day__square--outside):hover {
  stroke: #555;
  stroke-width: 1px;
}

.vch__day__square--outside {
  pointer-events: none;
}
</style>
