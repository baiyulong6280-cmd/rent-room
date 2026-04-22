<script setup>
import { RouterView } from 'vue-router'
</script>

<template>
  <RouterView />
</template>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  background: #f5f5f5;
  color: #1a1a1a;
}

button {
  cursor: pointer;
  border: none;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
  padding: 12px 24px;
  transition: opacity .2s;
}
button:hover { opacity: .85; }
button:disabled { opacity: .5; cursor: not-allowed; }
</style>
