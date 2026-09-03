<script setup>
import { ref, computed } from 'vue'
import { useCareerStore } from '@/stores/careerStore.js'
import { groupByCategory } from '@/lib/matching.js'
import ExperienceCard from '@/components/experience/ExperienceCard.vue'
import ExperienceDialog from '@/components/experience/ExperienceDialog.vue'

const store = useCareerStore()
const filter = ref(null)          // 선택된 competencyId
const dlg = ref(null)

const tagged = computed(() => store.taggedCompetencyIds)

/* 경험이 덮고 있는 역량만 칩으로 낸다. 안 덮은 20개를 다 늘어놓으면
   "고를 수 있는 것"과 "내가 가진 것"이 섞여 필터가 필터로 안 읽힌다. */
const chips = computed(() =>
  groupByCategory(
    store.competencies
      .filter(c => tagged.value.has(c.id))
      .map(c => ({ ...c, n: store.experiences.filter(e => e.competencyIds.includes(c.id)).length }))))

const shown = computed(() =>
  filter.value ? store.experiences.filter(e => e.competencyIds.includes(filter.value)) : store.experiences)

const filterName = computed(() =>
  filter.value ? store.competencies.find(c => c.id === filter.value)?.name : null)

</script>

<template>
  <header class="hero">
    <div class="hl">
      <p class="label">Career Lab · Library</p>
      <h1 class="display">경험 라이브러리</h1>
      <p class="lede">
        지원할 때마다 새로 쓰는 게 아니라, 한 번 구조화해 두고 계속 꺼내 쓴다.
        <b>AI가 하나도 없어도 이 화면은 그 자체로 도구다.</b>
      </p>
    </div>
    <!-- 이 화면에 하나뿐인 주요 행동. 제목 옆 빈자리가 그 자리다. -->
    <button class="btn btn--primary hb" @click="dlg.open()">＋ 경험 등록</button>
  </header>

  <section class="readout" aria-label="현황">
    <div class="nums">
      <div class="panel cell">
        <div class="num num--lg num--read">{{ store.experiences.length }}</div>
        <p class="label">등록한 경험</p>
      </div>
      <div class="panel cell">
        <div class="num num--lg num--read">{{ tagged.size }}<span class="of">/{{ store.competencies.length }}</span></div>
        <p class="label">태그된 역량</p>
      </div>
    </div>

    <div class="panel cell filt" aria-label="역량으로 필터링">
    <p class="label">역량으로 필터링</p>
    <div class="grps">
      <div v-for="g in chips" :key="g.k" class="grp">
        <p class="label gl">{{ g.label }}</p>
        <div class="tags">
          <button
            v-for="c in g.items" :key="c.id"
            class="tag chip"
            :aria-pressed="filter === c.id"
            @click="filter = filter === c.id ? null : c.id"
          >{{ c.name }}<b class="n">{{ c.n }}</b></button>
        </div>
      </div>
    </div>
    </div>
  </section>

  <section class="controls">
    <button v-if="filter" class="btn btn--sm" @click="filter = null">필터 초기화</button>
    <span class="muted count">{{ filterName ? `${filterName} · ${shown.length}건` : `전체 ${shown.length}건` }}</span>
  </section>


  <section class="grid">
    <ExperienceCard v-for="e in shown" :key="e.id" :exp="e" @edit="dlg.open($event)" />
  </section>

  <ExperienceDialog ref="dlg" />
</template>

<style scoped>
/* 제목 왼쪽, 등록 버튼 오른쪽. 버튼은 리드 문장 아래끝에 맞춰 앉는다 —
   위로 붙이면 제목 옆에 떠 보이고, 여기 두면 글 덩어리가 버튼을 받친다. */
.hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; flex-wrap: wrap; }
.hl { min-width: 0; }
.hb { flex: none; }

.lede { max-width: 58ch; color: var(--muted); margin: 14px 0 0; }
.lede b { color: var(--ink); font-weight: 600; }

.readout { display: flex; gap: 12px; flex-wrap: wrap; margin: 26px 0 0; }
/* 숫자는 가로로 나란히 두지 않고 쌓는다 — 둘 다 "얼마나 모았나" 라
   같은 종류이고, 세로로 두면 필터가 가로를 다 쓴다.
   높이는 필터 패널이 정하고, 두 칸이 그걸 반씩 나눠 갖는다. */
.nums { display: flex; flex-direction: column; gap: 12px; flex: none; width: 152px; }
.nums .cell { flex: 1; }
/* 늘어난 칸에서 숫자가 위에만 붙어 있으면 아래가 텅 빈다. 가운데 앉힌다. */
.cell {
  padding: 14px 20px; display: flex; flex-direction: column;
  justify-content: center; gap: 4px;
}
.filt { justify-content: flex-start; }
.of { font-size: 0.5em; color: var(--muted); }

.controls { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin: 20px 0 0; min-height: 22px; }
.count { margin-left: auto; font-size: 12.5px; }

@media (max-width: 640px) {
  /* 좁아지면 버튼이 제목 아래로 내려가 가로를 다 쓴다 */
  .hero { align-items: stretch; }
  .hb { width: 100%; }
}

.filt { padding: 13px 16px; flex: 1 1 340px; min-width: 0; }
/* 범주 이름은 왼쪽 홈통에 고정한다 — 위에 얹으면 줄 수가 두 배가 되고,
   필터가 목록보다 길어진다. 좁아지면 홈통을 접는다. */
/* 범주 사이 간격은 태그가 줄바꿈되는 간격(5px)보다 확실히 커야 한다.
   비슷하면 묶음선이 안 보이고 그냥 긴 목록으로 읽힌다. */
.grps { display: flex; flex-direction: column; gap: 16px; margin-top: 10px; }
.grp { display: grid; grid-template-columns: 78px minmax(0, 1fr); gap: 10px; align-items: baseline; }
.gl { margin: 0; text-align: right; white-space: nowrap; }
.tags { display: flex; gap: 5px; flex-wrap: wrap; }

@media (max-width: 620px) {
  .grp { grid-template-columns: 1fr; gap: 4px; }
  .gl { text-align: left; }
}

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
  .grid { grid-template-columns: 1fr; }
}
</style>
