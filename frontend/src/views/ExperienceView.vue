<script setup>
import { ref, computed } from 'vue'
import { useCareerStore } from '@/stores/careerStore.js'
import ExperienceCard from '@/components/experience/ExperienceCard.vue'
import ExperienceDialog from '@/components/experience/ExperienceDialog.vue'

const store = useCareerStore()
const filter = ref(null)          // 선택된 competencyId
const dlg = ref(null)

const tagged = computed(() => store.taggedCompetencyIds)

/* 경험이 덮고 있는 역량만 칩으로 낸다. 안 덮은 20개를 다 늘어놓으면
   "고를 수 있는 것"과 "내가 가진 것"이 섞여 필터가 필터로 안 읽힌다. */
const chips = computed(() =>
  store.competencies
    .filter(c => tagged.value.has(c.id))
    .map(c => ({ ...c, n: store.experiences.filter(e => e.competencyIds.includes(c.id)).length })))

const shown = computed(() =>
  filter.value ? store.experiences.filter(e => e.competencyIds.includes(filter.value)) : store.experiences)

const filterName = computed(() =>
  filter.value ? store.competencies.find(c => c.id === filter.value)?.name : null)

/* 경험이 못 덮는 역량 — 다음에 뭘 만들어야 하나 */
const uncovered = computed(() => store.competencies.filter(c => !tagged.value.has(c.id)))
</script>

<template>
  <p class="label">CareerFit · Library</p>
  <h1 class="display">경험 라이브러리</h1>
  <p class="lede">
    지원할 때마다 새로 쓰는 게 아니라, 한 번 구조화해 두고 계속 꺼내 쓴다.
    <b>AI가 하나도 없어도 이 화면은 그 자체로 도구다.</b>
  </p>

  <section class="readout" aria-label="현황">
    <div class="panel cell">
      <div class="num num--lg num--read">{{ store.experiences.length }}</div>
      <p class="label">등록한 경험</p>
    </div>
    <div class="panel cell">
      <div class="num num--lg num--read">{{ tagged.size }}<span class="of">/{{ store.competencies.length }}</span></div>
      <p class="label">태그된 역량</p>
    </div>
    <div class="panel cell cell--wide">
      <p class="label">경험이 못 덮는 역량 {{ uncovered.length }}개</p>
      <div class="tags">
        <span v-for="c in uncovered" :key="c.id" class="tag tag--gap">{{ c.name }}</span>
      </div>
    </div>
  </section>

  <section class="controls">
    <button class="btn btn--primary" @click="dlg.open()">＋ 경험 등록</button>
    <button v-if="filter" class="btn btn--sm" @click="filter = null">필터 초기화</button>
    <span class="muted count">{{ filterName ? `${filterName} · ${shown.length}건` : `전체 ${shown.length}건` }}</span>
  </section>

  <section class="panel filt" aria-label="역량으로 좁히기">
    <p class="label">역량으로 좁히기</p>
    <div class="tags">
      <button
        v-for="c in chips" :key="c.id"
        class="tag chip"
        :aria-pressed="filter === c.id"
        @click="filter = filter === c.id ? null : c.id"
      >{{ c.name }}<b class="n">{{ c.n }}</b></button>
    </div>
  </section>

  <section class="grid">
    <ExperienceCard v-for="e in shown" :key="e.id" :exp="e" @edit="dlg.open($event)" />
  </section>

  <ExperienceDialog ref="dlg" />
</template>

<style scoped>
.lede { max-width: 58ch; color: var(--muted); margin: 14px 0 0; }
.lede b { color: var(--ink); font-weight: 600; }

.readout { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin: 26px 0 0; }
.cell { padding: 14px 16px 12px; display: flex; flex-direction: column; gap: 4px; }
.cell--wide { grid-column: span 2; }
.of { font-size: 0.5em; color: var(--muted); }

.controls { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin: 22px 0 0; }
.count { margin-left: auto; font-size: 12.5px; }

.filt { padding: 13px 16px; margin: 14px 0 0; }
.tags { display: flex; gap: 5px; flex-wrap: wrap; margin-top: 9px; }

/* 필터 칩 — 눌린 채로 두는 것이 "지금 이걸로 좁혔다" 표시다 */
.chip { cursor: pointer; font: inherit; font-size: 11px; font-weight: 600; }
.chip:hover { border-color: var(--line-strong); color: var(--ink); }
.chip[aria-pressed='true'] {
  background: var(--accent); border-color: var(--accent); color: var(--accent-ink);
}
.chip[aria-pressed='true'] .n { color: var(--accent-ink); opacity: .7; }
.n { margin-left: 6px; color: var(--faint); font-weight: 700; }

.grid { display: grid; gap: 12px; margin: 18px 0 0; grid-template-columns: repeat(auto-fill, minmax(370px, 1fr)); }

@media (max-width: 760px) {
  .readout { grid-template-columns: repeat(2, 1fr); }
  .grid { grid-template-columns: 1fr; }
}
</style>
