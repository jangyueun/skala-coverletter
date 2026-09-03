<script setup>
/* 로딩 자리. 화면마다 따로 그리면 다섯 가지 로딩이 생긴다 — 이걸 쓴다.
   rows 는 목록이 몇 줄쯤 올지. 실제 높이와 비슷해야 채워질 때 화면이 덜 뛴다. */
defineProps({ rows: { type: Number, default: 3 }, label: { type: String, default: '불러오는 중' } })
</script>

<template>
  <div class="sk" role="status" :aria-label="label">
    <div v-for="i in rows" :key="i" class="row" :style="{ width: (100 - (i % 3) * 12) + '%' }" />
  </div>
</template>

<style scoped>
.sk { display: flex; flex-direction: column; gap: 10px; padding: 4px 0; }
.row {
  height: 14px; border-radius: var(--r);
  background: linear-gradient(90deg, var(--panel-sunken) 0%, var(--line-soft) 50%, var(--panel-sunken) 100%);
  background-size: 200% 100%;
  animation: sk 1.2s linear infinite;
}
@keyframes sk { from { background-position: 200% 0 } to { background-position: -200% 0 } }
@media (prefers-reduced-motion: reduce) { .row { animation: none; } }
</style>
