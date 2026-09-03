import { defineStore } from 'pinia'

/**
 * 로그인 상태.
 *
 * 지금은 화면만 있고 실제 인증은 없다. 백엔드에 Slack OAuth 가 붙으면
 * signIn() 이 /oauth/slack 으로 보내는 자리가 되고, signedIn 은 서버 세션에서
 * 온 값이 된다. 컴포넌트는 이 스토어만 보고 있으므로 그때 화면은 안 고쳐도 된다.
 *
 * 기본값이 로그인 상태인 이유는 — 이 앱의 모든 화면이 "내 경험" 위에 서 있어서
 * 로그아웃으로 시작하면 보여 줄 것이 없다.
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    signedIn: true,
    // 워크스페이스 제한 로그인이라 표시할 이름이 생긴다
    name: '김지호',
  }),
  actions: {
    signIn() { this.signedIn = true },
    signOut() { this.signedIn = false },
  },
})
