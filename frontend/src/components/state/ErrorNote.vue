<script setup>
/* 실패 자리. 무엇이 안 됐는지와 다시 시도하는 버튼 — 그 둘뿐이다.
   error 는 api/client.js 의 ApiError 거나 일반 Error 다. */
const props = defineProps({
  error: { type: Object, required: true },
  what:  { type: String, default: '불러오기' },
})
defineEmits(['retry'])

const text = () => {
  const e = props.error
  if (e?.status === 503 && e?.body?.message) return e.body.message   // dev AI 서버가 이유를 준다
  if (e?.status) return `서버가 ${e.status} 을 돌려줬습니다`
  if (e?.message?.includes('fetch')) return '서버에 닿지 않습니다. 백엔드가 떠 있는지 확인하세요'
  return e?.message || '알 수 없는 오류'
}
</script>

<template>
  <div class="en" role="alert">
    <p class="t">{{ what }}에 실패했습니다</p>
    <p class="m">{{ text() }}</p>
    <button class="btn btn--sm" @click="$emit('retry')">다시 시도</button>
  </div>
</template>

<style scoped>
.en {
  display: flex; flex-direction: column; align-items: flex-start; gap: 6px;
  padding: 16px 18px; border: 1px solid var(--gap); border-radius: var(--r);
  background: var(--panel-raised);
}
.t { margin: 0; font-size: var(--fs-sm); font-weight: 700; color: var(--gap); }
.m { margin: 0 0 6px; font-size: var(--fs-xs); color: var(--muted); font-family: var(--mono); }
</style>
