<script setup>
import { RouterView, RouterLink } from 'vue-router'

const nav = [
  { to: '/',            key: 'FIND', label: '공고' },
  { to: '/experiences', key: 'LIB',  label: '경험' },
  { to: '/spec',        key: 'DEV',  label: '개발참고' },
]
</script>

<template>
  <header class="top">
    <RouterLink to="/" class="brand">
      <span class="mark" aria-hidden="true"></span>커리어맵
    </RouterLink>

    <nav class="nav" aria-label="주요">
      <RouterLink v-for="n in nav" :key="n.to" :to="n.to" class="btn btn--sm navlink">
        <span class="label nk">{{ n.key }}</span>{{ n.label }}
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
  padding: 16px 24px;
  background: var(--panel-raised);
  border-bottom: 3px solid var(--line-strong);
}
.brand {
  display: flex; align-items: center; gap: 9px;
  font-size: 17px; font-weight: 800; letter-spacing: var(--track-display);
  color: var(--ink); text-decoration: none;
}
/* 로고 자리 — 계기의 전원 표시등 */
.mark {
  width: 10px; height: 10px; border-radius: 50%;
  background: var(--accent); flex: none;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 22%, transparent);
}
.nav { display: flex; gap: 7px; margin-left: auto; flex-wrap: wrap; }
.navlink { text-decoration: none; }
.nk { color: var(--faint); font-size: 9px; }
.navlink:hover .nk { color: currentColor; opacity: .65; }

/* 지금 있는 곳은 눌린 채로 둔다 — 상태를 깊이로 말한다 */
.navlink.router-link-exact-active {
  background: var(--line-strong); border-color: var(--line-strong); color: var(--panel);
}
.navlink.router-link-exact-active .nk { color: var(--panel); opacity: .55; }

.wrap { max-width: 1160px; margin: 0 auto; padding: 30px 24px 90px; }
@media (max-width: 560px) {
  .top { padding: 13px 16px; gap: 12px; }
  .wrap { padding: 22px 16px 70px; }
}
</style>
