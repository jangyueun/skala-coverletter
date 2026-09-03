import { createRouter, createWebHistory } from 'vue-router'

/* 라우트는 "뒤로 가기가 말이 되는 단위" 로만 나눈다.
   공고 상세의 탭(공고내용·매칭·자소서)은 라우트가 아니라 컴포넌트 상태다 —
   탭을 옮긴 뒤 뒤로 가기를 누르면 목록으로 돌아가야지 이전 탭으로 가면 안 된다. */
const routes = [
  { path: '/',              name: 'home',       component: () => import('@/views/HomeView.vue') },
  { path: '/postings/:id',  name: 'posting',    component: () => import('@/views/PostingView.vue'), props: true },
  { path: '/experiences',   name: 'experiences', component: () => import('@/views/ExperienceView.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

/* 개발 참고는 dev 에서만 존재한다.
   import.meta.env.DEV 는 vite build 에서 false 로 치환되고, 그러면 이 블록이
   통째로 죽은 코드가 되어 번들에서 빠진다 — 라우트도, SpecView 청크도.
   운영에 올렸을 때 /spec 을 주소창에 쳐도 홈으로 리다이렉트된다. */
if (import.meta.env.DEV) {
  routes.splice(routes.length - 1, 0, {
    path: '/spec', name: 'spec',
    component: () => import('@/views/SpecView.vue'),
  })
}

export default createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, saved) {
    return saved || { top: 0 }
  },
})
