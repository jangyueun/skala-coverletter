<script setup>
import { RouterView, RouterLink } from 'vue-router'

const nav = [
  { to: '/',            key: 'FIND', label: '공고' },
  { to: '/experiences', key: 'LIB',  label: '경험' },
  // 개발 참고는 npm run dev 에서만 뜬다. 빌드하면 라우트째 사라진다.
  ...(import.meta.env.DEV ? [{ to: '/spec', key: 'DEV', label: '개발참고' }] : []),
]
</script>

<template>
  <header class="top">
    <RouterLink to="/" class="brand">
      <span class="mark" aria-hidden="true"></span>Career Lab
    </RouterLink>

    <nav class="nav" aria-label="주요">
      <RouterLink v-for="n in nav" :key="n.to" :to="n.to" class="navlink">{{ n.label }}</RouterLink>
      <RouterLink to="/my" class="btn btn--sm my">MY</RouterLink>
    </nav>
  </header>

  <main class="wrap">
    <RouterView />
  </main>
</template>

<style scoped>
.top {
  display: flex; align-items: center; gap: 20px; flex-wrap: wrap;
  padding: 18px 24px;
  background: var(--panel);
  border-bottom: 1px solid var(--line);
}
.brand {
  display: flex; align-items: center; gap: 9px;
  font-size: 17px; font-weight: 800; letter-spacing: var(--track-display);
  color: var(--ink); text-decoration: none;
}
.mark { width: 9px; height: 9px; border-radius: 50%; background: var(--accent); flex: none; }
.nav { display: flex; align-items: center; gap: 22px; margin-left: auto; flex-wrap: wrap; }

/* 링크는 밑줄도 배경도 없다. 지금 있는 곳만 빨강 — 채용 사이트의 관례다. */
.navlink {
  text-decoration: none; color: var(--ink-2);
  font-size: 14px; font-weight: 600;
  transition: color var(--release) linear;
}
.navlink:hover { color: var(--ink); }
.navlink.router-link-exact-active { color: var(--accent); }
.my { margin-left: 4px; }

.wrap { max-width: 1160px; margin: 0 auto; padding: 30px 24px 90px; }
@media (max-width: 560px) {
  .top { padding: 13px 16px; gap: 12px; }
  .wrap { padding: 22px 16px 70px; }
}
</style>
