<script setup>
import { computed } from 'vue'
import { strLabel } from '@/lib/matching.js'

const props = defineProps({ row: { type: Object, required: true } })

/* 가중치를 눈금으로도 말한다 — "이 공고가 이걸 얼마나 무겁게 요구하나". */
const wTicks = computed(() => Math.round(props.row.weight * 5))
const pct = computed(() => Math.round(props.row.score * 100))
</script>

<template>
  <div class="row" :class="{ gap: row.isGap }">
    <div class="top">
      <div class="name">
        <b>{{ row.comp.name }}</b>
        <span class="tag w" :title="`가중치 ${row.weight}`">
          <i v-for="i in 5" :key="i" :class="{ on: i <= wTicks }" />
        </span>
      </div>
      <span class="num sc" :class="{ gap: row.isGap }">{{ pct }}%</span>
    </div>

    <!-- 이 역량을 뒷받침하는 경험. 없으면 그게 곧 갭이다. -->
    <div v-if="row.evid.length" class="ev">
      <span v-for="e in row.evid" :key="e.id" class="tag ev1">
        {{ e.title }}
        <b class="str">{{ strLabel(e.strength?.[row.competencyId] ?? 0.6) }}</b>
      </span>
    </div>
    <p v-else class="none">보강 필요 — 이 역량을 증명할 경험이 아직 없습니다.</p>

    <p class="evd">{{ row.evidence }}</p>
  </div>
</template>

<style scoped>
.row { padding: 12px 0; border-bottom: 1px solid var(--line-soft); }
.row:last-child { border-bottom: none; padding-bottom: 2px; }

.top { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.name { display: flex; align-items: center; gap: 8px; min-width: 0; flex-wrap: wrap; }
.name b { font-size: 13.5px; font-weight: 700; }

/* 가중치 — 숫자 대신 눈금 5칸 */
.w { display: inline-flex; gap: 2px; padding: 3px 6px; align-items: center; }
.w i { width: 3px; height: 8px; background: var(--line); display: block; }
.w i.on { background: var(--ink-2); }

.sc { font-size: 15px; font-weight: 600; flex: none; }
.sc.gap { color: var(--gap); }

.ev { display: flex; gap: 5px; flex-wrap: wrap; margin-top: 8px; }
.ev1 { border-color: var(--line-soft); color: var(--ink-2); }
.str { margin-left: 5px; color: var(--muted); font-weight: 700; }

.none { margin: 8px 0 0; font-size: 12.5px; color: var(--gap); font-weight: 600; }
.evd { margin: 7px 0 0; font-size: 12px; color: var(--muted); line-height: 1.6; }
</style>
