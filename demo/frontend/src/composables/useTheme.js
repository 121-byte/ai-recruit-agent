import { ref } from 'vue'

const STORAGE_KEY = 'od-clay-theme'
const isDark = ref(false)
let initialized = false

function readStored() {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'dark'
  } catch {
    return false
  }
}

function applyDark(dark) {
  const html = document.documentElement
  html.classList.add('theme-transitioning')
  if (dark) html.setAttribute('data-theme', 'dark')
  else html.removeAttribute('data-theme')
  try {
    localStorage.setItem(STORAGE_KEY, dark ? 'dark' : 'light')
  } catch {
    /* ignore */
  }
  // remove transition class after the transition settles
  window.setTimeout(() => html.classList.remove('theme-transitioning'), 420)
}

export function useTheme() {
  if (!initialized) {
    isDark.value = readStored()
    initialized = true
  }

  function apply() {
    applyDark(isDark.value)
  }

  function toggle() {
    isDark.value = !isDark.value
    applyDark(isDark.value)
  }

  return { isDark, apply, toggle }
}
