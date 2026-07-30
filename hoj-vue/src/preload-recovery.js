const reloadKey = 'hoj:vite-preload-reload'
const retryWindowMs = 10000

window.addEventListener('vite:preloadError', (event) => {
  const now = Date.now()
  let lastReloadAt = 0

  try {
    lastReloadAt = Number(sessionStorage.getItem(reloadKey) || 0)
  } catch {
    // Storage can be unavailable in privacy-restricted browser contexts.
  }

  if (now - lastReloadAt < retryWindowMs) {
    return
  }

  event.preventDefault()

  try {
    sessionStorage.setItem(reloadKey, String(now))
  } catch {
    // Reload recovery still works without the loop-prevention marker.
  }

  window.location.reload()
})

window.setTimeout(() => {
  try {
    sessionStorage.removeItem(reloadKey)
  } catch {
    // Nothing to clean up when storage is unavailable.
  }
}, retryWindowMs)
