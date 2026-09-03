import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/authStore.js'

/* 라우트는 "뒤로 가기가 말이 되는 단위" 로만 나눈다.
   공고 상세의 탭(공고내용·매칭·자소서)은 라우트가 아니라 컴포넌트 상태다 —
   탭을 옮긴 뒤 뒤로 가기를 누르면 목록으로 돌아가야지 이전 탭으로 가면 안 된다. */
const routes = [
  { path: '/',              name: 'home',       component: () => import('@/views/HomeView.vue') },
  { path: '/postings/:id',  name: 'posting',    component: () => import('@/views/PostingView.vue'), props: true },
  { path: '/experiences',   name: 'experiences', component: () => import('@/views/ExperienceView.vue') },
  { path: '/my',            name: 'my',         component: () => import('@/views/MyView.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]


const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, saved) {
    return saved || { top: 0 }
  },
})

/* 로그아웃 상태로 /my 에 남아 있으면 남의 지원 현황처럼 보인다.
   스토어를 함수 안에서 부른다 — 모듈 최상위에서 부르면 pinia 가 붙기 전이라 터진다. */
router.beforeEach(to => {
  if (to.name === 'my' && !useAuthStore().signedIn) return { name: 'home' }
  return true
})

export default router
