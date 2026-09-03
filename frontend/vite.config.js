import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import aiDevServer from './vite-plugins/aiDevServer.js'

export default defineConfig({
  // aiDevServer 는 apply:'serve' 라 빌드 산출물에 들어가지 않는다.
  // 백엔드가 AI 엔드포인트를 서빙하기 시작하면 이 줄만 지우면 된다 —
  // 아래 프록시가 /api 를 통째로 :8080 으로 넘기므로 프런트 코드는 안 고친다.
  plugins: [vue(), aiDevServer()],

  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
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
