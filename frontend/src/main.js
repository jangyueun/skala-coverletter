import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'
import './assets/tokens.css'
import './assets/base.css'

import { useAuthStore } from './stores/auth.js'
import { usePostingsStore } from './stores/postings.js'
import { useExperiencesStore } from './stores/experiences.js'
import { useAnswersStore } from './stores/answers.js'

const app = createApp(App).use(createPinia()).use(router)

/* 앱을 켜면 넷을 동시에 부른다. 화면은 각자 loaded 를 보고 Skeleton 을 그린다.
   await 하지 않는다 — 첫 화면이 서버 응답을 기다리며 하얗게 멈춰 있으면 안 된다. */
useAuthStore().load()
usePostingsStore().load()
useExperiencesStore().load()
useAnswersStore().load()

app.mount('#app')
