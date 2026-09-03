<script setup>
import { RouterView, RouterLink } from 'vue-router'

const nav = [
  { to: '/',            label: '공고',   meta: 'FIND' },
  { to: '/experiences', label: '경험',   meta: 'LIB' },
  { to: '/spec',        label: '개발참고', meta: 'DEV' },
]
</script>

<template>
  <header class="top">
    <RouterLink to="/" class="brand">
      <span class="dot" aria-hidden="true" />커리어맵
    </RouterLink>
    <nav class="nav">
      <RouterLink v-for="n in nav" :key="n.to" :to="n.to" class="press navlink">
        <span class="label">{{ n.meta }}</span>{{ n.label }}
      </RouterLink>
    </nav>
  </header>

  <main class="wrap">
    <RouterView />
  </main>
</template>

<style scoped>
.top {
  display: flex; align-items: center; gap: 20px; flex-wrap: wrap;
  padding: 18px 24px; border-bottom: 1.5px solid var(--line-strong);
}
.brand {
  display: flex; align-items: center; gap: 8px;
  font-size: 18px; font-weight: 800; letter-spacing: var(--track-display);
  color: var(--ink); text-decoration: none;
}
.dot { width: 9px; height: 9px; border-radius: 50%; background: var(--accent); }
.nav { display: flex; gap: 8px; margin-left: auto; flex-wrap: wrap; }
.navlink {
  display: inline-flex; align-items: baseline; gap: 7px;
  padding: 7px 13px; text-decoration: none; color: var(--ink); font-weight: 600;
}
/* 지금 있는 곳은 눌린 채로 둔다 — 상태를 색이 아니라 깊이로 말한다 */
.navlink.router-link-exact-active {
  transform: translate(var(--lift), var(--lift));
  box-shadow: 0 0 0 var(--line-strong);
  background: var(--accent); color: var(--accent-ink);
}
.navlink.router-link-exact-active .label { color: var(--accent-ink); opacity: .7; }
.wrap { max-width: 1180px; margin: 0 auto; padding: 32px 24px 80px; }
</style>
