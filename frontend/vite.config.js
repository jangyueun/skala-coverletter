import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  // AI(인테이크·초안)도 이제 Spring 이 서빙한다 — 아래 프록시가 /api 를 통째로 :8080 으로 넘긴다.
  // 예전엔 dev 전용 vite 플러그인(aiDevServer)이 그 경로를 대신했는데, Spring 에 폴링 API 와 워커가 생기면서 지웠다.
  // 그래서 real 모드(VITE_API_MOCK=0)로 AI 를 쓰려면 Spring 과 AI 서버(ai/, :8000)가 같이 떠 있어야 한다.
  plugins: [vue()],

  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },

  /* vitest. domain·stores 는 node 로 돈다. 컴포넌트 테스트 파일만
     첫 줄에 `// @vitest-environment jsdom` 을 단다. */
  test: {
    environment: 'node',
    include: ['tests/**/*.test.js'],
  },

  server: {
    port: 5173,
    // 같은 Wi-Fi 의 다른 기기에서 볼 수 있게 LAN 에 연다.
    // 이게 없으면 localhost 에만 묶여 내 컴에서만 보인다.
    // `npm run dev` 가 Network: http://192.168.x.x:5173 을 같이 찍어 준다.
    host: true,
    // 백엔드를 같은 오리진으로 끌어온다.
    //
    // 이걸 안 걸면 5173 → 8080 이 교차 출처가 되어 CORS 설정이 필요해지고,
    // 로그인 세션 쿠키까지 얹히면 SameSite 때문에 반나절이 날아간다.
    // 프록시로 같은 오리진을 만들면 그 문제가 통째로 사라진다.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
