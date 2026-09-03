import { defineStore } from 'pinia'
import { api } from '@/api/index.js'
import { useUiStore } from './ui.js'

/**
 * 로그인 상태. 앱을 켤 때 load() 한 번으로 /api/auth/me 를 부른다.
 *
 * 백엔드는 로그아웃 상태를 401 이 아니라 200 + null 로 준다 — 로그인 안 된 게
 * 정상인 화면(홈)에서 콘솔에 빨간 401 이 찍히는 걸 피한다. 그래서 여기서도
 * user === null 이 정상이고 error 가 아니다.
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,        // { id, displayName, email, avatarUrl } | null
    loading: false,
    error: null,
    loaded: false,     // load() 가 한 번은 끝났나 — 끝나기 전엔 로그인 여부를 모른다
  }),

  getters: {
    signedIn: s => !!s.user,
    name: s => s.user?.displayName ?? '',
  },

  actions: {
    async load() {
      if (this.loading) return
      this.loading = true; this.error = null
      try { this.user = await api.auth.me() }
      catch (e) { this.error = e }
      finally { this.loading = false; this.loaded = true }
    },

    /* real 은 Slack 으로 페이지를 옮기고 돌아온 뒤 load() 가 사용자를 준다.
       mock 은 즉시 사용자를 돌려준다. 둘 다 이 한 줄로 끝난다. */
    async signIn() {
      this.error = null
      try { const u = await api.auth.signIn(); if (u) this.user = u }
      catch (e) { this.error = e }
    },

    async signOut() {
      this.error = null
      try { await api.auth.signOut() }
      catch (e) { this.error = e; return }
      this.user = null
      /* 매칭순은 로그인 상태에서만 뜻이 있다. 그대로 두면 로그아웃 뒤
         정렬 버튼이 하나도 안 눌린 채 알 수 없는 순서로 목록이 남는다. */
      useUiStore().sort = 'deadline'
    },
  },
})
