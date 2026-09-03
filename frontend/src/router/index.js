import { createRouter, createWebHistory } from 'vue-router'

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


export default createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, saved) {
    return saved || { top: 0 }
  },
})
