export const DAYS_IN_YEAR = 365
export const DAYS_IN_WEEK = 7
export const DEFAULT_RANGE_COLOR = ['#ebedf0', '#c0ddf9', '#73b3f3', '#3886e1', '#17459e']
export const DEFAULT_CALENDAR_LOCALE = {
  months: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  days: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  on: 'on',
  less: 'Less',
  more: 'More',
}

export function parseCalendarDate(value) {
  if (value instanceof Date) {
    return new Date(value.getFullYear(), value.getMonth(), value.getDate())
  }

  if (typeof value === 'string') {
    const dateOnly = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(value.trim())
    if (dateOnly) {
      return new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3]))
    }
  }

  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime())
    ? null
    : new Date(parsed.getFullYear(), parsed.getMonth(), parsed.getDate())
}

export function shiftCalendarDate(date, days) {
  const shifted = new Date(date)
  shifted.setDate(shifted.getDate() + days)
  return shifted
}

export function calendarDateKey(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function buildCalendarHeatmap({
  endDate,
  values = [],
  max = null,
  rangeColorLength = DEFAULT_RANGE_COLOR.length,
}) {
  const normalizedEndDate = parseCalendarDate(endDate) || parseCalendarDate(new Date())
  const startDate = shiftCalendarDate(normalizedEndDate, -DAYS_IN_YEAR)
  const activityByDate = new Map()

  for (const value of values) {
    const date = parseCalendarDate(value?.date)
    const count = Number(value?.count)
    if (date && Number.isFinite(count)) {
      activityByDate.set(calendarDateKey(date), Math.max(0, count))
    }
  }

  const observedMaximum = Math.max(0, ...activityByDate.values())
  const maximumCount = Number.isFinite(max) && max > 0
    ? max
    : (observedMaximum > 0 ? Math.ceil((observedMaximum / 5) * 4) : 0)
  const colorCount = Math.max(2, rangeColorLength)

  function colorIndex(count) {
    if (count <= 0 || maximumCount <= 0) {
      return 0
    }
    const lastIndex = colorCount - 1
    if (count >= maximumCount) {
      return lastIndex
    }
    return Math.min(
      lastIndex - 1,
      Math.ceil((count / maximumCount) * Math.max(1, lastIndex - 1)),
    )
  }

  const displayStart = shiftCalendarDate(startDate, -startDate.getDay())
  const displayEnd = shiftCalendarDate(
    normalizedEndDate,
    (DAYS_IN_WEEK - 1) - normalizedEndDate.getDay(),
  )
  const calendar = []
  let cursor = displayStart

  while (cursor <= displayEnd) {
    const week = []
    for (let dayIndex = 0; dayIndex < DAYS_IN_WEEK; dayIndex += 1) {
      const current = new Date(cursor)
      const key = calendarDateKey(current)
      const count = activityByDate.get(key) || 0
      week.push({
        date: current,
        key,
        count,
        colorIndex: colorIndex(count),
        dayIndex,
        inRange: current >= startDate && current <= normalizedEndDate,
        visible: current <= normalizedEndDate,
      })
      cursor = shiftCalendarDate(cursor, 1)
    }
    calendar.push(week)
  }

  return {
    activityByDate,
    calendar,
    displayStart,
    endDate: normalizedEndDate,
    maximumCount,
    startDate,
  }
}

export function getCalendarMonthLabels(calendar) {
  const labels = []
  for (let weekIndex = 1; weekIndex < calendar.length; weekIndex += 1) {
    const previous = calendar[weekIndex - 1][0].date
    const current = calendar[weekIndex][0].date
    if (
      previous.getFullYear() !== current.getFullYear() ||
      previous.getMonth() !== current.getMonth()
    ) {
      labels.push({
        month: current.getMonth(),
        year: current.getFullYear(),
        weekIndex,
      })
    }
  }
  return labels
}
