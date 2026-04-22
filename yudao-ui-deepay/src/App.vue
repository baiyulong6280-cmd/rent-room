<script setup>
import { RouterView } from 'vue-router'
</script>

<template>
  <RouterView />
</template>

<style>
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

:root {
  --bg:        #0B0B0B;
  --surface:   #111111;
  --surface2:  #1A1A1A;
  --border:    #2A2A2A;
  --text:      #FFFFFF;
  --text2:     #888888;
  --accent:    #00FF88;
  --accent-fg: #000000;
  --danger:    #FF4444;
  --warn:      #FFD600;
}

html { background: var(--bg); }

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: var(--bg);
  color: var(--text);
  -webkit-font-smoothing: antialiased;
  /* safe-area for notched phones */
  padding-bottom: env(safe-area-inset-bottom);
}

button {
  cursor: pointer;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  padding: 12px 24px;
  transition: opacity .18s, transform .12s;
}
button:hover:not(:disabled)  { opacity: .88; }
button:active:not(:disabled) { transform: scale(.97); }
button:disabled { opacity: .4; cursor: not-allowed; }

/* Scrollbar — dark */
::-webkit-scrollbar { width: 4px; height: 4px; }
::-webkit-scrollbar-track  { background: transparent; }
::-webkit-scrollbar-thumb  { background: var(--border); border-radius: 4px; }
</style>
