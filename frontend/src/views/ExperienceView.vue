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

/* 필터는 접을 수 있다. 태그가 21개라 늘 펴 두면 목록이 한 화면 아래로 밀린다.
   고른 게 있으면 접혀 있어도 개수가 머리에 남아, 접어 둔 걸 잊고
   "왜 6건이 아니지" 하는 일이 없다. */
const filtOpen = ref(true)

</script>

<template>
  <header class="hero">
    <div class="hl">
      <h1 class="display">경험 라이브러리</h1>
      <p class="lede">STAR 기법을 사용해 본인의 경험을 적어 두세요.</p>
      <!-- 글자와 설명은 등록 폼의 FIELDS 와 같은 문구다.
           두 화면이 다른 말을 하면 어느 쪽을 믿어야 할지 모른다. -->
      <dl class="star">
        <div><dt>S</dt><dd><b>Situation</b> 어떤 상황이었나</dd></div>
        <div><dt>T</dt><dd><b>Task</b> 무엇을 목표로 삼았나</dd></div>
        <div><dt>A</dt><dd><b>Action</b> 내가 한 행동과 적용한 방식</dd></div>
        <div><dt>R</dt><dd><b>Result</b> 결과 — 숫자로</dd></div>
      </dl>
    </div>
    <!-- 오른쪽은 "얼마나 모았나" 와 "더 모으기" 다. 같은 이야기라 붙여 둔다.
         숫자에는 패널 테두리를 두르지 않는다 — 헤더에 카드가 뜨면 제목보다 무거워지고,
         이 줄에서 채워진 것은 등록 버튼 하나여야 한다. -->
    <div class="hr">
      <div class="stat">
        <div class="num num--lg num--read">{{ store.experiences.length }}</div>
        <p class="sl">등록한 경험</p>
      </div>
      <div class="stat">
        <div class="num num--lg num--read">{{ tagged.size }}<span class="of">/{{ store.competencies.length }}</span></div>
        <p class="sl">태그된 역량</p>
      </div>
      <button class="btn btn--primary hb" @click="dlg.open()">＋ 경험 등록</button>
    </div>
  </header>

  <!-- 숫자가 헤더로 올라가면서 이 구역에는 필터만 남았다.
       aria-label="현황" 을 그대로 두면 스크린리더로 "현황" 구역에 들어와
       태그 목록만 만난다. 패널 하나뿐이라 감싸는 section 자체가 필요 없다. -->
  <section aria-label="역량으로 필터링">
    <div class="panel filt">
    <button class="fh" :aria-expanded="filtOpen" @click="filtOpen = !filtOpen">
      <span class="ct">역량으로 필터링</span>
      <span class="fn">{{ chips.reduce((n, g) => n + g.items.length, 0) }}</span>
      <span v-if="filterName" class="fp">1</span>
      <span class="chev" aria-hidden="true">{{ filtOpen ? '−' : '+' }}</span>
    </button>
    <div v-show="filtOpen" class="grps">
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
/* 제목·STAR 안내가 왼쪽, "얼마나 모았나 + 더 모으기" 가 오른쪽.
   오른쪽 덩어리는 왼쪽 글의 아래끝에 맞춰 앉는다 — 위로 붙이면
   제목 옆에 떠 보이고, 여기 두면 글 덩어리가 그걸 받친다. */
.hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; flex-wrap: wrap; }
.hl { min-width: 0; }
.hr { display: flex; align-items: flex-end; gap: 22px; flex: none; }
.hb { flex: none; }

/* 숫자는 테두리 없이 세로 구분선으로만 나눈다. 헤더에 카드를 세우면
   제목보다 무거워지고, 이 줄에서 채워진 것은 등록 버튼 하나여야 한다. */
.stat { display: flex; flex-direction: column; gap: 2px; padding-left: 22px; border-left: 1px solid var(--line); }
.stat:first-child { padding-left: 0; border-left: none; }
.sl { margin: 0; font-size: 11.5px; color: var(--muted); white-space: nowrap; }

.lede { max-width: 58ch; color: var(--ink); margin: 14px 0 0; font-weight: 600; }

/* STAR 안내 — 읽고 지나가는 글이 아니라 옆에 두고 보는 표에 가깝다.
   글자 열을 고정 폭으로 잡아 네 줄의 설명 시작점이 맞는다. */
.star { margin: 9px 0 0; display: flex; flex-direction: column; gap: 3px; }
.star > div { display: grid; grid-template-columns: 14px 1fr; gap: 8px; align-items: baseline; }
.star dt { color: var(--accent); font-family: var(--mono); font-weight: 700; font-size: 12px; }
.star dd { margin: 0; font-size: 12.5px; color: var(--muted); }
.star dd b { color: var(--ink-2); font-weight: 600; margin-right: 5px; }

.ct { font-size: 13.5px; font-weight: 700; color: var(--ink); letter-spacing: var(--track-tight); }

/* 접기 머리 — 홈의 필터 아코디언(.acch)과 같은 어법이다.
   이름은 왼쪽, 개수와 펼침 표시는 오른쪽. */
.fh {
  display: flex; align-items: center; gap: 8px; width: 100%;
  padding: 0; background: transparent; border: none;
  cursor: pointer; text-align: left; font: inherit; color: inherit;
}
.fn { margin-left: auto; font-family: var(--mono); font-size: 10px; color: var(--faint); font-weight: 500; }
/* 접혀 있어도 고른 게 있다는 건 보여야 한다 */
.fp {
  display: inline-grid; place-items: center; min-width: 16px; height: 16px; padding: 0 4px;
  border-radius: var(--pill); background: var(--accent); color: var(--accent-ink);
  font-size: 9.5px; font-weight: 700;
}
.chev { font-family: var(--mono); font-size: 14px; color: var(--muted); line-height: 1; }
.fh[aria-expanded='true'] .chev { color: var(--ink); }
.of { font-size: 0.5em; color: var(--muted); }

.controls { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin: 20px 0 0; min-height: 22px; }
.count { margin-left: auto; font-size: 12.5px; }

@media (max-width: 760px) {
  /* 좁아지면 오른쪽 덩어리가 제목 아래로 내려가 왼쪽 끝에 맞춰 선다 */
  .hero { align-items: stretch; }
  .hr { flex-wrap: wrap; gap: 16px; }
  .hb { width: 100%; }
}

/* .cell / .readout 은 뺐다. 숫자 칸이 헤더로 가면서 쓰는 곳이 하나씩만
   남았는데, 한 요소에 클래스 셋을 걸어 두면 어느 쪽이 무엇을 하는지
   나중에 못 읽는다 — .row 프리미티브로 사고 난 것과 같은 종류다. */
.filt { margin: 26px 0 0; padding: 13px 16px 15px; min-width: 0; display: flex; flex-direction: column; gap: 11px; }
/* 범주 이름은 왼쪽 홈통에 고정한다 — 위에 얹으면 줄 수가 두 배가 되고,
   필터가 목록보다 길어진다. 좁아지면 홈통을 접는다. */
/* 범주 사이 간격은 태그가 줄바꿈되는 간격(5px)보다 확실히 커야 한다.
   비슷하면 묶음선이 안 보이고 그냥 긴 목록으로 읽힌다. */
.grps { display: flex; flex-direction: column; gap: 16px; }
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
