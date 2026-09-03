<script setup>
import { RouterView, RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.js'

const auth = useAuthStore()
const router = useRouter()

/* real 은 Slack 으로 페이지를 옮기므로 이 뒤가 실행되지 않는다.
   mock 은 즉시 돌아오므로 MY 로 보낸다. 둘 다 이 한 줄이면 된다. */
async function signIn() {
  await auth.signIn()
  if (auth.signedIn) router.push('/my')
}

const nav = [
  { to: '/',            key: 'FIND', label: '공고' },
  { to: '/experiences', key: 'LIB',  label: '경험' },
]
</script>

<template>
  <header class="top">
    <RouterLink to="/" class="brand">
      <span class="mark" aria-hidden="true"></span>Career Lab
    </RouterLink>

    <nav class="nav" aria-label="주요">
      <RouterLink v-for="n in nav" :key="n.to" :to="n.to" class="navlink">{{ n.label }}</RouterLink>
      <!-- 로그아웃 상태에서는 들어갈 MY 가 없다. 자리를 비우지 않고
           그 자리에서 바로 들어올 수 있게 로그인으로 바꾼다. -->
      <RouterLink v-if="auth.signedIn" to="/my" class="btn btn--sm my">MY</RouterLink>
      <button v-else class="btn btn--sm my" @click="signIn">로그인</button>
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
  font-size: var(--fs-lg); font-weight: 800; letter-spacing: var(--track-display);
  color: var(--ink); text-decoration: none;
}
.mark { width: 9px; height: 9px; border-radius: 50%; background: var(--accent); flex: none; }
.nav { display: flex; align-items: center; gap: 22px; margin-left: auto; flex-wrap: wrap; }

/* 링크는 밑줄도 배경도 없다. 지금 있는 곳만 빨강 — 채용 사이트의 관례다. */
.navlink {
  text-decoration: none; color: var(--ink-2);
  font-size: var(--fs-md); font-weight: 600;
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
