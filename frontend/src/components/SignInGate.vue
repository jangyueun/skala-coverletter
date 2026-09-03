<script setup>
import { useAuthStore } from '@/stores/authStore.js'

/* 로그인해야 볼 수 있는 자리에 대신 놓는 화면.
   공고 내용은 누구에게나 열려 있고, "나에 관한 것"(내 경험 · 나와의 매칭 ·
   내 자소서 · 내 지원 현황)만 막힌다. 그 경계를 화면마다 다르게 말하면
   무엇이 왜 막혔는지 모르므로 한 컴포넌트로 모은다.

   내쫓지 않고 그 자리에 둔다. 라우터로 홈에 돌려보내면 방금 누른 것이
   왜 안 열렸는지 알 수 없고, 로그인한 뒤 다시 찾아 들어와야 한다. */
defineProps({
  desc: { type: String, required: true },   // 여기서 무엇이 막혔는지 한 줄
})
const auth = useAuthStore()
</script>

<template>
  <div class="gate">
    <p class="subhead">로그인이 필요합니다</p>
    <p class="d">{{ desc }}</p>
    <button class="btn btn--primary" @click="auth.signIn()">Slack으로 로그인</button>
  </div>
</template>

<style scoped>
.gate {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  padding: 54px 24px; text-align: center;
  border: 1px solid var(--line); border-radius: var(--r); background: var(--panel-raised);
}
.d { margin: 0 0 6px; max-width: 42ch; font-size: var(--fs-xs); color: var(--muted); line-height: 1.7; }
</style>
