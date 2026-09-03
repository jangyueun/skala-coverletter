<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCareerStore } from '@/stores/careerStore.js'
import { SCORE, dday } from '@/lib/matching.js'
import MatchTable from '@/components/posting/MatchTable.vue'
import EssayEditor from '@/components/posting/EssayEditor.vue'

const props = defineProps({ id: { type: [String, Number], required: true } })
const store = useCareerStore()
const router = useRouter()

/* 탭은 라우트가 아니라 컴포넌트 상태다.
   탭을 옮긴 뒤 뒤로 가기를 누르면 목록으로 돌아가야지 이전 탭으로 가면 안 된다. */
const tab = ref('content')
const TABS = [
  { k: 'content', label: '공고 내용' },
  { k: 'match',   label: '매칭' },
  { k: 'essay',   label: '자소서' },
]

const posting = computed(() => store.postingById(props.id))
const match   = computed(() => posting.value ? store.matchFor(posting.value) : null)
const essay   = computed(() => posting.value ? store.cards.find(c => c.posting.id === posting.value.id)?.essay : null)
const pct     = computed(() => Math.round((match.value?.overall ?? 0) * 100))
const gaps    = computed(() => match.value?.rows.filter(r => r.isGap) ?? [])
const d       = computed(() => posting.value ? dday(posting.value.deadline) : 0)

/* 판정 — 숫자만 주면 사용자가 뭘 해야 할지 모른다. */
const verdict = computed(() => {
  const o = match.value?.overall ?? 0
  if (o >= SCORE.RECOMMEND) return { k: '지원 권장', tone: 'ok' }
  if (o >= SCORE.CONDITIONAL) return { k: '조건부 지원', tone: 'gap' }
  return { k: '보강 필요', tone: 'gap' }
})

/* 같은 기업 다른 직무 / 다른 기업 비슷한 직무.
   공고가 직무 단위라는 걸 화면에서 보이게 하는 자리다.

   "같은 직무" 가 아니라 "비슷한" 이다. role 은 계열이라 "서버 개발" 과
   "백엔드 엔지니어" 를 한 묶음으로 본다 — 기업마다 직무명이 제각각이라
   문자열로는 못 묶기 때문이다. 같다고 말하면 과장이 된다. */
const related = computed(() => {
  if (!posting.value) return { sameCo: [], sameRole: [] }
  const me = posting.value
  const live = store.livePostings.filter(p => p.id !== me.id)
  return {
    sameCo:   live.filter(p => p.company === me.company),
    sameRole: live.filter(p => p.company !== me.company && p.role === me.role),
  }
})
const pctOf = p => Math.round(store.matchFor(p).overall * 100)

const ROLE = {
  BACKEND: '백엔드', FRONTEND: '프론트엔드', FULLSTACK: '풀스택',
  PLATFORM: '플랫폼·인프라', AI: 'AI',
}
const roleLabel = computed(() => ROLE[posting.value?.role] || posting.value?.role || '')

/* 마지막 글자의 받침 유무로 조사를 고른다.
   "도메인 이해은" 처럼 틀리면 문장 전체가 기계가 쓴 것으로 읽힌다. */
function withJosa(word, withBatchim, without) {
  const c = word.trim().charCodeAt(word.trim().length - 1)
  if (c < 0xAC00 || c > 0xD7A3) return word + without   // 한글이 아니면 받침 없는 쪽
  return word + ((c - 0xAC00) % 28 ? withBatchim : without)
}
const gapPhrase = computed(() =>
  withJosa(gaps.value.map(g => g.comp.name).join(', '), '은', '는'))

const questions = computed(() => {
  const app = store.applications.find(a => a.postingId === posting.value?.id)
  return app ? store.questions.filter(q => q.applicationId === app.id) : []
})
</script>

<template>
  <template v-if="posting">
    <button class="btn btn--quiet back" @click="router.push('/')">← 공고 목록</button>

    <!-- 머리 — 판독값과 판정을 먼저 -->
    <header class="hd">
      <div class="hd-l">
        <p class="label">{{ posting.company }} · {{ roleLabel }}</p>
        <h1 class="display pos">{{ posting.position }}</h1>
        <div class="meta">
          <span class="tag tag--ink">
            <b v-if="d >= 0" class="num">D-{{ d }}</b>{{ d >= 0 ? '\u00a0' : '' }}{{ posting.deadline }} {{ d >= 0 ? '마감' : '마감됨' }}
          </span>
          <span class="tag" :class="essay?.state === 'DONE' ? 'tag--ok' : ''">{{ essay?.label }}</span>
          <span class="tag mono">{{ posting.source }}</span>
        </div>
      </div>

      <div class="panel readout">
        <div class="num num--lg num--read">{{ pct }}<span class="pc">%</span></div>
        <div class="gauge big" aria-hidden="true">
          <i v-for="i in 10" :key="i"
             :class="i <= Math.round(pct / 10) ? (gaps.length && i === Math.round(pct/10) ? 'gap' : 'on') : ''"
             :style="{ height: 4 + i * 1.3 + 'px' }" />
        </div>
        <p class="label">Match</p>
        <p class="verdict" :class="verdict.tone">{{ verdict.k }}</p>
      </div>
    </header>

    <!-- 조작부 — 스위스 버튼이 탭 역할을 한다 -->
    <nav class="tabs" aria-label="공고 상세">
      <button v-for="t in TABS" :key="t.k" class="btn btn--sm"
              :aria-pressed="tab === t.k" @click="tab = t.k">{{ t.label }}</button>
      <button class="btn btn--sm bm" :aria-pressed="store.bookmarks.has(posting.id)"
              @click="store.toggleBookmark(posting.id)">
        {{ store.bookmarks.has(posting.id) ? '★ 즐겨찾기' : '☆ 즐겨찾기' }}
      </button>
    </nav>

    <!-- ── 공고 내용 ─────────────────────────────────────── -->
    <section v-show="tab === 'content'" class="pane">
      <div class="panel body">
        <p class="label">직무 내용 · 원문</p>
        <pre class="raw">{{ posting.rawText }}</pre>
      </div>

      <div class="panel body">
        <p class="label">추출된 요구 역량 {{ posting.required.length }}개</p>
        <p class="hint">가중치와 커버리지는 <b>매칭</b> 탭에서 봅니다. 여기서는 무엇을 요구하는지만.</p>
        <div class="tags">
          <span v-for="r in match.rows" :key="r.competencyId" class="tag">{{ r.comp.name }}</span>
        </div>
        <p v-if="posting.newCompetencies?.length" class="hint nc">
          사전에 없어 매길 수 없었던 요구 —
          <b>{{ posting.newCompetencies.join(', ') }}</b>
        </p>
      </div>

      <div class="panel body">
        <p class="label">관련 공고</p>
        <p class="hint">공고는 기업이 아니라 <b>직무 단위</b>입니다. 같은 회사라도 직무가 다르면 매칭이 다릅니다.</p>

        <div v-if="related.sameCo.length" class="relgrp">
          <p class="rl">같은 기업 · 다른 직무</p>
          <button v-for="p in related.sameCo" :key="p.id" class="rel panel panel--press"
                  @click="router.push(`/postings/${p.id}`)">
            <span class="rn">{{ p.position }}</span>
            <span class="num rp">{{ pctOf(p) }}%</span>
          </button>
        </div>

        <div v-if="related.sameRole.length" class="relgrp">
          <p class="rl">다른 기업 · 비슷한 직무 <span class="rlk">{{ roleLabel }}</span></p>
          <button v-for="p in related.sameRole" :key="p.id" class="rel panel panel--press"
                  @click="router.push(`/postings/${p.id}`)">
            <span class="rn">{{ p.company }} · {{ p.position }}</span>
            <span class="num rp">{{ pctOf(p) }}%</span>
          </button>
        </div>
      </div>
    </section>

    <!-- ── 매칭 ──────────────────────────────────────────── -->
    <section v-show="tab === 'match'" class="pane">
      <div class="panel body">
        <p class="label">평가</p>
        <p class="assess">
          요구 역량 {{ match.rows.length }}개 중 <b>{{ match.rows.length - gaps.length }}개</b>를 덮어
          매칭 <b class="num">{{ pct }}%</b>입니다.
          <template v-if="gaps.length">
            반면 <b class="gaptext">{{ gapPhrase }}</b>
            증명할 경험이 없거나 너무 약합니다 — <b>보강이 필요한 역량</b>입니다.
          </template>
        </p>
        <p class="hint">
          이 문장은 하드코딩이 아닙니다. 경험을 등록하면 그대로 바뀝니다 —
          평가는 사용자가 돌리는 게 아니라 <b>경험 변경 이벤트로 서버가 갱신</b>합니다.
        </p>
      </div>

      <div class="panel body">
        <div class="chead">
          <p class="label">요구 역량별 커버리지 · 가중치순</p>
          <p class="chint">역량을 누르면 근거 경험이 펼쳐집니다</p>
        </div>
        <MatchTable :rows="[...match.rows].sort((a,b) => b.weight - a.weight)" />
      </div>
    </section>

    <!-- ── 자소서 ────────────────────────────────────────── -->
    <section v-show="tab === 'essay'" class="pane">
      <div v-if="!questions.length" class="panel body empty">
        <p class="label">문항 없음</p>
        <p class="hint">
          이 공고는 서버가 자소서 문항을 주지 않았습니다.
          문항을 직접 등록하면 여기서 바로 작성할 수 있습니다.
        </p>
        <button class="btn" disabled>문항 등록</button>
      </div>

      <div v-else class="panel body">
        <EssayEditor :questions="questions" />
      </div>
    </section>
  </template>

  <p v-else class="panel body">공고를 찾을 수 없습니다.</p>
</template>

<style scoped>
.back { margin-bottom: 18px; }

.hd { display: flex; justify-content: space-between; align-items: flex-start; gap: 22px; flex-wrap: wrap; }
.hd-l { min-width: 0; flex: 1 1 340px; }
.pos { margin-top: 6px; font-size: clamp(1.7rem, 4.4vw, 2.6rem); }
.meta { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 12px; }

.readout {
  flex: none; padding: 14px 18px 13px; text-align: right;
  display: flex; flex-direction: column; align-items: flex-end; gap: 4px; min-width: 148px;
}
.pc { font-size: 0.55em; opacity: .6; margin-left: 1px; }
.gauge.big { height: 18px; }
.verdict { margin: 5px 0 0; font-size: 13px; font-weight: 700; }
.verdict.ok { color: var(--ok); }
.verdict.gap { color: var(--gap); }

.tabs {
  display: flex; gap: 7px; flex-wrap: wrap; margin: 24px 0 0; padding: 12px 0;
  border-top: 1px solid var(--line); border-bottom: 1px solid var(--line);
}
.bm { margin-left: auto; }

.pane { display: flex; flex-direction: column; gap: 12px; margin-top: 18px; }
.body { padding: 16px 18px; }
.hint { color: var(--muted); font-size: 12.5px; margin: 6px 0 0; }
.hint b { color: var(--ink); }
.nc { color: var(--gap); }
.nc b { color: var(--gap); }
.tags { display: flex; gap: 5px; flex-wrap: wrap; margin-top: 11px; }

.raw {
  margin: 9px 0 0; padding: 13px 14px;
  background: var(--panel-sunken); border: 1px solid var(--line-soft); border-radius: var(--r);
  font-family: var(--mono); font-size: 12px; line-height: 1.75; white-space: pre-wrap;
  color: var(--ink-2); max-height: 320px; overflow-y: auto;
}

.relgrp { margin-top: 14px; }
.rl { margin: 0 0 7px; font-size: 11.5px; font-weight: 700; color: var(--muted); }
.rlk { color: var(--ink); }
.rel {
  display: flex; justify-content: space-between; align-items: center; gap: 12px; width: 100%;
  padding: 9px 12px; margin-bottom: 6px; text-align: left; font: inherit; color: inherit;
  border-top-width: 2px;
}
.rn { font-weight: 600; font-size: 13px; min-width: 0; }
.rp { font-size: 15px; font-weight: 600; flex: none; }

/* 표 머리 — 라벨 왼쪽, 사용법 힌트 오른쪽. 목업의 cardhead 와 같은 역할이다. */
.chead {
  display: flex; align-items: baseline; justify-content: space-between;
  gap: 12px; flex-wrap: wrap; margin-bottom: 13px;
}
.chint { margin: 0; font-size: 11.5px; color: var(--faint); }

.assess { margin: 8px 0 0; font-size: 14.5px; line-height: 1.7; }
.assess b { font-weight: 700; }
.gaptext { color: var(--gap); }

.qh { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.qt { margin: 8px 0 0; font-size: 14.5px; font-weight: 600; line-height: 1.55; }
.draft {
  margin: 11px 0 0; padding: 12px 13px;
  background: var(--panel-sunken); border-left: 3px solid var(--line-strong);
  font-size: 13px; line-height: 1.8; color: var(--ink-2);
}
.empty { text-align: center; }
.empty .btn { margin-top: 12px; }

@media (max-width: 620px) {
  .readout { width: 100%; align-items: flex-start; text-align: left; }
  .gauge.big { justify-content: flex-start; }
}
</style>
