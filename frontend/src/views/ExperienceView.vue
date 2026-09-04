<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useExperiencesStore } from '@/stores/experiences.js'
import { usePostingsStore } from '@/stores/postings.js'
import { useAiTasksStore } from '@/stores/aiTasks.js'
import { groupByCategory } from '@/domain/competency.js'
import Skeleton from '@/components/state/Skeleton.vue'
import ErrorNote from '@/components/state/ErrorNote.vue'
import ExperienceCard from '@/components/experience/ExperienceCard.vue'
import ExperienceDialog from '@/components/experience/ExperienceDialog.vue'
import SignInGate from '@/components/SignInGate.vue'
import { useAuthStore } from '@/stores/auth.js'

const E = useExperiencesStore()
const P = usePostingsStore()
const auth = useAuthStore()
const aiTasks = useAiTasksStore()
const filter = ref(null)          // 선택된 competencyId
const dlg = ref(null)

/* 인테이크 "결과 보기" 를 누르면 스토어의 신호가 오른다 — 다이얼로그를 포폴 탭에서 다시 연다.
   다이얼로그가 뜨면 IntakePanel 이 스토어의 후보를 그대로 보여 준다(로컬 편집분도 살아 있다). */
watch(() => aiTasks.intakeReopenSeq, () => dlg.value?.open(null, 'intake'))

const tagged = computed(() => E.taggedCompetencyIds)

/* 경험이 덮고 있는 역량만 칩으로 낸다. 안 덮은 20개를 다 늘어놓으면
   "고를 수 있는 것"과 "내가 가진 것"이 섞여 필터가 필터로 안 읽힌다. */
const chips = computed(() =>
  groupByCategory(
    P.competencies
      .filter(c => tagged.value.has(c.id))
      .map(c => ({ ...c, n: E.list.filter(e => E.has(e, c.id)).length }))))

const shown = computed(() =>
  filter.value ? E.list.filter(e => E.has(e, filter.value)) : E.list)

const filterName = computed(() =>
  filter.value ? P.competencies.find(c => c.id === filter.value)?.name : null)

/* 필터는 접을 수 있다. 태그가 21개라 늘 펴 두면 목록이 한 화면 아래로 밀린다.
   고른 게 있으면 접혀 있어도 개수가 머리에 남아, 접어 둔 걸 잊고
   "왜 6건이 아니지" 하는 일이 없다. */
const filtOpen = ref(true)

/* STAR 설명은 물음표 뒤에 접어 둔다. 네 줄을 늘 펴 두면 머리글이
   공고 찾기(제목 + 한 줄)보다 두 배로 길어져 두 화면이 다른 리듬이 된다.

   펼침은 흐름을 밀지 않고 떠오른다 — 안으로 밀어 넣으면 열 때마다
   오른쪽 숫자·버튼이 아래로 뛴다. */
const starOpen = ref(false)
const starEl = ref(null)

function onDocClick(e) { if (starEl.value && !starEl.value.contains(e.target)) starOpen.value = false }
function onEsc(e) { if (e.key === 'Escape') starOpen.value = false }
onMounted(() => { document.addEventListener('click', onDocClick); document.addEventListener('keydown', onEsc) })
onBeforeUnmount(() => { document.removeEventListener('click', onDocClick); document.removeEventListener('keydown', onEsc) })

</script>

<template>
  <header class="pagehead">
    <div class="pagehead-l">
      <h1 class="display">경험 관리</h1>
      <p class="pagehead-lede">
        <span ref="starEl" class="anchor">
          STAR<button type="button" class="q" :aria-expanded="starOpen"
                      aria-label="STAR 기법 설명 보기"
                      @click="starOpen = !starOpen">?</button>
          <!-- 글자와 설명은 등록 폼의 FIELDS 와 같은 문구다.
               두 화면이 다른 말을 하면 어느 쪽을 믿어야 할지 모른다. -->
          <dl v-if="starOpen" class="star">
            <div><dt>S</dt><dd><b>Situation</b> 어떤 상황이었나</dd></div>
            <div><dt>T</dt><dd><b>Task</b> 무엇을 목표로 삼았나</dd></div>
            <div><dt>A</dt><dd><b>Action</b> 내가 한 행동과 적용한 방식</dd></div>
            <div><dt>R</dt><dd><b>Result</b> 결과 — 숫자로</dd></div>
          </dl>
        </span>
        기법을 사용해 본인의 경험을 적어 두세요.
      </p>
    </div>
    <!-- 오른쪽은 "얼마나 모았나" 와 "더 모으기" 다. 같은 이야기라 붙여 둔다.
         숫자에는 패널 테두리를 두르지 않는다 — 헤더에 카드가 뜨면 제목보다 무거워지고,
         이 줄에서 채워진 것은 등록 버튼 하나여야 한다. -->
    <div class="hr">
      <div v-if="auth.signedIn" class="stat">
        <div class="num num--lg num--read">{{ E.list.length }}</div>
        <p class="sl">등록한 경험</p>
      </div>
      <div v-if="auth.signedIn" class="stat">
        <div class="num num--lg num--read">{{ tagged.size }}<span class="of">/{{ P.competencies.length }}</span></div>
        <p class="sl">태그된 역량</p>
      </div>
      <button v-if="auth.signedIn" class="btn btn--primary hb" @click="dlg.open()">＋ 경험 등록</button>
    </div>
  </header>

  <Skeleton v-if="!auth.loaded || !E.loaded || !P.loaded" :rows="6" />
  <!-- 로그인 안내가 오류보다 먼저다. 실제 서버는 로그아웃 상태에 401 을 주는데,
       그걸 "경험 불러오기에 실패했습니다" 로 그리면 고장으로 읽힌다. -->
  <SignInGate v-else-if="!auth.signedIn"
              desc="등록한 경험은 계정에 저장됩니다. 로그인하면 쌓아 둔 경험을 이어서 쓸 수 있습니다." />
  <ErrorNote v-else-if="E.error" :error="E.error" what="경험 불러오기" @retry="E.load()" />

  <template v-else>

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
</template>

<style scoped>
.hr { display: flex; align-items: flex-end; gap: 22px; flex: none; }
.hb { flex: none; }

/* 숫자는 테두리 없이 세로 구분선으로만 나눈다. 헤더에 카드를 세우면
   제목보다 무거워지고, 이 줄에서 채워진 것은 등록 버튼 하나여야 한다. */
.stat { display: flex; flex-direction: column; gap: 2px; padding-left: 22px; border-left: 1px solid var(--line); }
.stat:first-child { padding-left: 0; border-left: none; }
.sl { margin: 0; font-size: var(--fs-2xs); color: var(--muted); white-space: nowrap; }


/* 물음표 — 글 안에 박히는 버튼이라 원형으로 작게. */
.anchor { position: relative; white-space: nowrap; }
.q {
  width: 15px; height: 15px; margin-left: 2px; padding: 0;
  border: 1px solid var(--line-strong); border-radius: 50%; background: var(--panel);
  color: var(--ink-2); font-family: var(--mono); font-size: var(--fs-3xs); font-weight: 700;
  line-height: 1; cursor: pointer; vertical-align: 2px;
  transition: background var(--release) linear, color var(--release) linear;
}
.q:hover { background: var(--panel-sunken); }
.q[aria-expanded='true'] { background: var(--ink); border-color: var(--ink); color: var(--panel); }

/* STAR 안내 — 읽고 지나가는 글이 아니라 옆에 두고 보는 표에 가깝다.
   글자 열을 고정 폭으로 잡아 네 줄의 설명 시작점이 맞는다.
   흐름 위에 떠오른다 — 안으로 밀면 열 때마다 오른쪽 숫자·버튼이 아래로 뛴다.
   그림자 대신 진한 테두리로 띄운다. 이 스타일에 그림자는 없다. */
.star {
  position: absolute; z-index: 5; top: calc(100% + 8px); left: 0;
  padding: 13px 16px; white-space: nowrap;
  background: var(--panel-raised); border: 1px solid var(--line-strong); border-radius: var(--r);
  margin: 0; display: flex; flex-direction: column; gap: 3px;
}
.star > div { display: grid; grid-template-columns: 14px 1fr; gap: 8px; align-items: baseline; }
.star dt { color: var(--accent); font-family: var(--mono); font-weight: 700; font-size: var(--fs-xs); }
.star dd { margin: 0; font-size: var(--fs-xs); color: var(--muted); }
.star dd b { color: var(--ink-2); font-weight: 600; margin-right: 5px; }

.ct { font-size: var(--fs-sm); font-weight: 700; color: var(--ink); letter-spacing: var(--track-tight); }

/* 접기 머리 — 홈의 필터 아코디언(.acch)과 같은 어법이다.
   이름은 왼쪽, 개수와 펼침 표시는 오른쪽. */
.fh {
  display: flex; align-items: center; gap: 8px; width: 100%;
  padding: 0; background: transparent; border: none;
  cursor: pointer; text-align: left; font: inherit; color: inherit;
}
.fn { margin-right: auto; font-family: var(--mono); font-size: var(--fs-3xs); color: var(--muted); font-weight: 500; }
/* 접혀 있어도 고른 게 있다는 건 보여야 한다 */
.fp {
  display: inline-grid; place-items: center; min-width: 16px; height: 16px; padding: 0 4px;
  border-radius: var(--pill); background: var(--accent); color: var(--accent-ink);
  font-size: var(--fs-3xs); font-weight: 700;
}
.chev { font-family: var(--mono); font-size: var(--fs-md); color: var(--muted); line-height: 1; }
.fh[aria-expanded='true'] .chev { color: var(--ink); }
.of { font-size: 0.5em; color: var(--muted); }

.controls { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin: 20px 0 0; min-height: 22px; }
.count { margin-left: auto; font-size: var(--fs-xs); }

@media (max-width: 760px) {
  /* 좁아지면 오른쪽 덩어리가 제목 아래로 내려가 왼쪽 끝에 맞춰 선다 */
  .pagehead { align-items: stretch; }
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
.chip { cursor: pointer; font: inherit; font-size: var(--fs-2xs); font-weight: 600; }
.chip:hover { border-color: var(--line-strong); color: var(--ink); }
/* 고른 칩은 옅은 주황 위에 진한 주황 글자. 꽉 채운 주황은 D-day 의 몫이다. */
.chip[aria-pressed='true'] {
  background: var(--accent-soft); border-color: var(--accent); color: var(--accent-deep);
}
.chip[aria-pressed='true'] .n { color: var(--accent-deep); opacity: .75; }
.n { margin-left: 6px; color: var(--muted); font-weight: 700; }

.grid { display: grid; gap: 12px; margin: 18px 0 0; grid-template-columns: repeat(auto-fill, minmax(370px, 1fr)); }

@media (max-width: 760px) {
  .grid { grid-template-columns: 1fr; }
}
</style>
