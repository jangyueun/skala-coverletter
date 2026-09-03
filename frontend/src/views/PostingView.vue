<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { usePostingsStore } from '@/stores/postings.js'
import { useAnswersStore } from '@/stores/answers.js'
import { useDerivedStore } from '@/stores/derived.js'
import { SCORE } from '@/domain/matching.js'
import { dday, isClosed, deadlineLabel } from '@/domain/deadline.js'
import Skeleton from '@/components/state/Skeleton.vue'
import ErrorNote from '@/components/state/ErrorNote.vue'
import MatchTable from '@/components/posting/MatchTable.vue'
import SignInGate from '@/components/SignInGate.vue'
import { useAuthStore } from '@/stores/auth.js'
import EssayEditor from '@/components/posting/EssayEditor.vue'

const props = defineProps({ id: { type: [String, Number], required: true } })
const P = usePostingsStore()
const A = useAnswersStore()
const D = useDerivedStore()
const auth = useAuthStore()
const router = useRouter()

/* 문항은 공고 단위로 받는다(GET /api/postings/{id}/questions). 공고를 옮기면 그 공고 것을 받는다.
   로그인해야 주는 API 라 로그인이 확인된 뒤에 부른다 — 로그아웃 상태에서 401 을 오류로 그리지 않게. */
watch([() => props.id, () => auth.signedIn], ([id, signedIn]) => {
  if (signedIn) A.loadFor(id)
}, { immediate: true })

/* 탭은 라우트가 아니라 컴포넌트 상태다.
   탭을 옮긴 뒤 뒤로 가기를 누르면 목록으로 돌아가야지 이전 탭으로 가면 안 된다. */
const tab = ref('content')
const TABS = [
  { k: 'content', label: '공고 내용' },
  { k: 'match',   label: '매칭 상세 분석' },
  { k: 'essay',   label: '자소서' },
]

const posting = computed(() => P.byId(props.id))
const match   = computed(() => posting.value && D.ready ? D.matchFor(posting.value) : null)
/* cards 는 마감 안 지난 공고만 담는다. 거기서 찾으면 마감 지난 공고는
   undefined 가 되어 머리글에 글자 없는 빈 알약이 하나 그려졌다.
   상세 화면은 마감 공고도 다루므로 공고에서 바로 계산한다. */
const essay   = computed(() => posting.value && A.loaded ? D.essayFor(posting.value) : null)
const pct     = computed(() => Math.round((match.value?.overall ?? 0) * 100))
const gaps    = computed(() => match.value?.rows.filter(r => r.isGap) ?? [])
const d       = computed(() => posting.value ? dday(posting.value.deadline) : 0)
const closed  = computed(() => !!posting.value && isClosed(posting.value.deadline))

/* 판정 — 숫자만 주면 사용자가 뭘 해야 할지 모른다. 경계는 서버의 match_verdict 와 같다. */
const verdict = computed(() => {
  const o = match.value?.overall ?? 0
  if (o >= SCORE.RECOMMEND) return { k: '지원 권장', tone: 'ok' }
  if (o >= SCORE.CONDITIONAL) return { k: '조건부 지원', tone: 'gap' }
  return { k: '보강 필요', tone: 'gap' }
})

/* 같은 기업 다른 직무 / 다른 기업 비슷한 직무.
   공고가 직무 단위라는 걸 화면에서 보이게 하는 자리다.

   "비슷한" 은 요구 역량 태그가 얼마나 겹치는가로 잰다. v6 에서 직무 계열(role)이 없어졌다 —
   기업마다 직무명이 제각각이라 문자열로는 못 묶고, 계열은 사람이 붙이던 라벨이라 데이터가 없다.
   실제 상세 DTO 는 related.sameCompany·related.similar(겹침 수 상위 3, 다른 기업)를 서버가 계산해 준다 —
   있으면 그걸 쓴다. 목은 related 가 없어 같은 규칙을 여기서 돈다.
   두 경로가 같은 모양({ id, company, position, shared })을 내서 템플릿은 어느 쪽인지 모른다. */
const related = computed(() => {
  if (!posting.value) return { sameCompany: [], similar: [] }
  const me = posting.value
  if (me.related) {
    return {
      sameCompany: me.related.sameCompany.map(r => ({ id: r.id, company: me.company, position: r.position })),
      similar: me.related.similar.map(r => ({ id: r.id, company: r.company, position: r.position, shared: r.sharedCompetencyCount })),
    }
  }
  const mine = new Set(me.requiredCompetencies.map(r => r.competencyId))
  const live = P.live.filter(p => p.id !== me.id)
  const row = p => ({ id: p.id, company: p.company, position: p.position })
  const similar = live
    .filter(p => p.company !== me.company)
    .map(p => ({ ...row(p), shared: p.requiredCompetencies.filter(r => mine.has(r.competencyId)).length }))
    .filter(x => x.shared > 0)
    .sort((a, b) => b.shared - a.shared)
    .slice(0, 3)
  return { sameCompany: live.filter(p => p.company === me.company).map(row), similar }
})
/* 관련 공고의 매칭률은 브라우저가 센다(서버 related 의 score 는 MATCH 워커가 없어 null 이다).
   목록에 없는 공고(있을 수 없지만)면 null — 템플릿이 숨긴다. */
const pctOf = id => {
  const p = P.byId(id)
  return p && D.ready ? Math.round(D.matchFor(p).overall * 100) : null
}

/* 마지막 글자의 받침 유무로 조사를 고른다.
   "도메인 이해은" 처럼 틀리면 문장 전체가 기계가 쓴 것으로 읽힌다. */
function withJosa(word, withBatchim, without) {
  const c = word.trim().charCodeAt(word.trim().length - 1)
  if (c < 0xAC00 || c > 0xD7A3) return word + without   // 한글이 아니면 받침 없는 쪽
  return word + ((c - 0xAC00) % 28 ? withBatchim : without)
}
const gapPhrase = computed(() =>
  withJosa(gaps.value.map(g => g.comp.name).join(', '), '은', '는'))

const questions = computed(() => posting.value ? A.questionsFor(posting.value.id) : [])
</script>

<template>
  <template v-if="posting">
    <button class="btn btn--quiet back" @click="router.push('/')">← 공고 목록</button>

    <!-- 머리 — 판독값과 판정을 먼저 -->
    <header class="hd">
      <div class="hd-l">
        <!-- 회사는 눈썹 문구가 아니라 읽는 줄이다. 카드에서 회사가 직무의
             70% 크기로 또렷하게 읽히는 것과 같은 비중으로 올린다. -->
        <p class="co">{{ posting.company }}</p>
        <h1 class="display pos">{{ posting.position }}</h1>
        <div class="meta">
          <!-- 끝난 공고라는 사실이 제일 먼저 읽혀야 한다. 이걸 놓치면
               아래의 매칭·자소서를 아직 지원할 수 있는 것으로 읽는다. -->
          <span v-if="closed" class="tag tag--closed">마감</span>
          <span class="tag tag--ink">
            <b v-if="!closed" class="num">D-{{ d }}</b>{{ !closed ? ' ' : '' }}{{ deadlineLabel(posting.deadline) }} 마감
          </span>
          <span v-if="auth.signedIn && essay?.label" class="tag" :class="essay.state === 'DONE' ? 'tag--ok' : ''">{{ essay.label }}</span>
        </div>
      </div>

      <!-- 매칭과 즐겨찾기는 탭이 아니라 이 공고 자체에 붙는 것이라 머리에 둔다.
           상자로 감싸지 않는다 — 이 화면에서 테두리는 탭 아래 내용의 몫이다. -->
      <div v-if="auth.signedIn" class="hd-r">
        <button class="btn btn--sm bm" :aria-pressed="!!posting.bookmarked"
                @click="P.toggleBookmark(posting.id)">
          {{ posting.bookmarked ? '★ 즐겨찾기' : '☆ 즐겨찾기' }}
        </button>
        <!-- 막대 게이지는 뺐다. .gauge 정의가 어디에도 없어 10개 <i> 가
             보이지 않는 채로 18px 만 먹고 있었고, 그게 판정을 퍼센트에서
             떼어 놓고 있었다. 숫자가 이미 같은 값을 말한다. -->
        <div class="rd">
          <p class="pctline">
            <span class="rdl">매칭률 :</span>
            <span class="num num--read pctn">{{ pct }}</span><span class="pc">%</span>
          </p>
          <p class="verdict" :class="verdict.tone">{{ verdict.k }}</p>
        </div>
      </div>
    </header>

    <!-- 탭은 행을 다 쓴다. 작은 pill 세 개면 그 옆의 빈자리가 더 커 보여
         "누를 것" 이 아니라 "붙어 있는 라벨" 로 읽힌다. -->
    <nav class="tabs" aria-label="공고 상세">
      <button v-for="t in TABS" :key="t.k" class="tab"
              :aria-pressed="tab === t.k" @click="tab = t.k">{{ t.label }}</button>
    </nav>

    <!-- ── 공고 내용 ─────────────────────────────────────── -->
    <section v-show="tab === 'content'" class="pane">
      <div class="panel body">
        <p class="subhead">직무 내용 · 원문</p>
        <pre class="raw">{{ posting.content }}</pre>
      </div>

      <div class="panel body">
        <p class="subhead">연관 태그</p>
        <!-- match 는 세 스토어가 다 와야 생긴다. 공고가 먼저 오는 창(목 지연 300~800ms 라
             매번 열린다)에 이 줄이 null.rows 를 읽어 렌더가 통째로 터지고 있었다.
             아래 매칭 탭이 이미 쓰는 가드와 같은 것을 여기도 둔다. -->
        <Skeleton v-if="!D.ready" :rows="2" />
        <div v-else class="tags">
          <span v-for="r in match.rows" :key="r.competencyId" class="tag">{{ r.comp.name }}</span>
        </div>
      </div>

      <div class="panel body">
        <p class="subhead">관련 공고</p>

        <div v-if="related.sameCompany.length" class="relgrp">
          <p class="rl">같은 기업 · 다른 직무</p>
          <button v-for="p in related.sameCompany" :key="p.id" class="rel panel panel--press"
                  @click="router.push(`/postings/${p.id}`)">
            <span class="rn">{{ p.position }}</span>
            <span v-if="auth.signedIn && pctOf(p.id) != null" class="num rp">{{ pctOf(p.id) }}%</span>
          </button>
        </div>

        <!-- 계열 라벨 대신 겹치는 역량 수를 적는다. "비슷하다" 의 근거가 그 숫자다. -->
        <div v-if="related.similar.length" class="relgrp">
          <p class="rl">다른 기업 · 비슷한 직무 <span class="rlk">요구 역량이 겹치는 순</span></p>
          <button v-for="x in related.similar" :key="x.id" class="rel panel panel--press"
                  @click="router.push(`/postings/${x.id}`)">
            <span class="rn">{{ x.company }} · {{ x.position }}</span>
            <span class="rs">역량 <b class="num">{{ x.shared }}</b>개 겹침</span>
            <span v-if="auth.signedIn && pctOf(x.id) != null" class="num rp">{{ pctOf(x.id) }}%</span>
          </button>
        </div>
      </div>
    </section>

    <!-- ── 매칭 ──────────────────────────────────────────── -->
    <section v-show="tab === 'match'" class="pane">
      <Skeleton v-if="!auth.loaded || !D.ready" :rows="6" />
      <SignInGate v-else-if="!auth.signedIn"
                  desc="이 공고가 요구하는 역량을 내 경험과 맞춰 봅니다. 내 경험을 읽어야 하는 화면이라 로그인이 필요합니다." />

      <template v-else>
      <div class="panel body">
        <p class="subhead">평가</p>
        <!-- 매칭률은 여기서 말하지 않는다. "덮었다" 는 0.45 를 넘었는지의 이진 판정이고
             매칭률은 점수의 가중 평균이라 기준이 다르다. 한 문장에 섞어 놓으면
             "다 덮었는데 왜 100%가 아니지" 가 된다. 퍼센트는 머리글이 이미 말한다. -->
        <p class="assess">
          <template v-if="gaps.length">
            요구 역량 {{ match.rows.length }}개 중 <b>{{ match.rows.length - gaps.length }}개</b>를
            증명할 경험이 있습니다. 반면 <b class="gaptext">{{ gapPhrase }}</b>
            증명할 경험이 없거나 너무 약합니다 — <b>보강이 필요한 역량</b>입니다.
          </template>
          <template v-else>
            요구 역량 <b>{{ match.rows.length }}개를 모두</b> 증명할 경험이 있습니다.
          </template>
        </p>
      </div>

      <div class="panel body">
        <div class="chead">
          <p class="subhead">요구 역량별 커버리지 · 가중치순</p>
          <p class="chint">역량을 누르면 근거 경험이 펼쳐집니다</p>
        </div>
        <MatchTable :rows="[...match.rows].sort((a,b) => b.weight - a.weight)" />
      </div>
      </template>
    </section>

    <!-- ── 자소서 ────────────────────────────────────────── -->
    <section v-show="tab === 'essay'" class="pane">
      <Skeleton v-if="!auth.loaded || !A.loaded" :rows="6" />
      <SignInGate v-else-if="!auth.signedIn"
                  desc="자소서 초안은 계정에 저장됩니다. 로그인하면 쓰던 곳부터 이어서 쓸 수 있습니다." />
      <!-- 이 공고의 문항은 따로 받는다(loadFor). 받기 전엔 뼈대, 못 받았으면 다시 시도. -->
      <ErrorNote v-else-if="A.error && !A.loadedFor[posting.id]" :error="A.error" what="문항 불러오기"
                 @retry="A.loadFor(posting.id)" />
      <Skeleton v-else-if="!A.loadedFor[posting.id]" :rows="6" />

      <!-- 문항은 관리자가 공고에 붙인다(v6). 사용자 등록 경로가 없으므로 버튼도 없다 —
           눌리지 않는 버튼은 "곧 된다" 는 거짓 약속이었다. -->
      <div v-else-if="!questions.length" class="panel body empty">
        <p class="subhead">문항 없음</p>
        <p class="hint">
          이 공고에는 아직 자소서 문항이 없습니다.
          문항이 등록되면 여기서 바로 쓸 수 있습니다.
        </p>
      </div>

      <div v-else class="panel body">
        <EssayEditor :questions="questions" />
      </div>
    </section>
  </template>

  <Skeleton v-else-if="!P.loaded" :rows="6" />
  <p v-else class="panel body">공고를 찾을 수 없습니다.</p>
</template>

<style scoped>
.back { margin-bottom: 18px; }

.hd { display: flex; justify-content: space-between; align-items: flex-start; gap: 22px; flex-wrap: wrap; }
.hd-l { min-width: 0; flex: 1 1 340px; }
/* 카드의 회사:직무 비율(12.5 : 18 = 0.69)을 상세에도 준다. 41.6 × 0.69 ≈ 26px */
.co {
  margin: 0; font-size: var(--fs-2xl); font-weight: 700;
  color: var(--ink-2); line-height: 1.25; letter-spacing: var(--track-tight);
}
.pos { margin-top: 4px; font-size: clamp(1.7rem, 4.4vw, 2.6rem); }
.meta { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 12px; }

.hd-r { flex: none; display: flex; flex-direction: column; align-items: flex-end; gap: 14px; }
.bm { flex: none; }
.rd { display: flex; flex-direction: column; align-items: flex-end; gap: 1px; }
/* 숫자·% ·라벨이 한 줄이다. 라벨을 위에 얹으면 그만큼 판정이 아래로 밀린다. */
.pctline { margin: 0; display: flex; align-items: baseline; gap: 1px; }
.pctn { font-size: var(--fs-2xl); font-weight: 800; line-height: 1.1; }
.pc { font-size: var(--fs-md); font-weight: 700; color: var(--muted); }
/* 판독값 라벨. 아래 관련 공고의 .rl 과 이름이 같아 덮이고 있었다 —
   숫자 옆이 아니라 위에 얹혀 있었다. .rd 계열로 이름을 옮긴다. */
.rdl { margin-right: 7px; font-size: var(--fs-xs); font-weight: 600; color: var(--muted); }
.verdict { margin: 0; font-size: var(--fs-sm); font-weight: 700; }
.verdict.ok { color: var(--ok); }
.verdict.gap { color: var(--gap); }

/* 탭 — 세 칸이 행을 똑같이 나눠 갖는다. 밑줄로만 지금 위치를 말한다.
   pill 로 채우면 아래 내용보다 조작부가 무거워진다. */
.tabs {
  display: grid; grid-template-columns: repeat(3, 1fr);
  margin: 26px 0 0; border-bottom: 1px solid var(--line);
}
.tab {
  padding: 13px 0; background: none; border: none;
  border-bottom: 2px solid transparent; margin-bottom: -1px;
  font-size: var(--fs-md); font-weight: 700; color: var(--muted);
  cursor: pointer; letter-spacing: var(--track-tight);
  transition: color var(--release) linear, border-color var(--release) linear;
}
.tab:hover { color: var(--ink); }
.tab[aria-pressed='true'] { color: var(--ink); border-bottom-color: var(--ink); }

.pane { display: flex; flex-direction: column; gap: 12px; margin-top: 18px; }
.body { padding: 16px 18px; }
.hint { color: var(--muted); font-size: var(--fs-xs); margin: 6px 0 0; }
.hint b { color: var(--ink); }
.tags { display: flex; gap: 5px; flex-wrap: wrap; margin-top: 11px; }

/* 원문에 따로 스크롤을 주지 않는다. 화면 안에 또 스크롤이 있으면
   페이지를 내리다 말고 그 안에서 멈추고, 어디까지 읽었는지도 흐려진다.
   회색 상자와 모노도 뺐다 — 다른 화면의 본문과 같은 글로 읽히면 된다. */
.raw {
  margin: 9px 0 0; padding: 0;
  font: inherit; font-size: var(--fs-sm); line-height: 1.85; white-space: pre-wrap;
  color: var(--ink-2);
}

.relgrp { margin-top: 14px; }
.rl { margin: 0 0 7px; font-size: var(--fs-2xs); font-weight: 700; color: var(--muted); }
.rlk { font-weight: 500; }
.rel {
  display: flex; justify-content: space-between; align-items: center; gap: 12px; width: 100%;
  padding: 9px 12px; margin-bottom: 6px; text-align: left; font: inherit; color: inherit;
  border-top-width: 2px;
}
.rn { font-weight: 600; font-size: var(--fs-sm); min-width: 0; }
/* 겹침 수는 이름과 퍼센트 사이에 작게 — "왜 여기 있나" 의 답이지 판독값이 아니다 */
.rs { margin-left: auto; font-size: var(--fs-2xs); color: var(--muted); flex: none; white-space: nowrap; }
.rs b { color: var(--ink-2); }
.rp { font-size: var(--fs-md); font-weight: 600; flex: none; }

/* 표 머리 — 라벨 왼쪽, 사용법 힌트 오른쪽. 목업의 cardhead 와 같은 역할이다. */
.chead {
  display: flex; align-items: baseline; justify-content: space-between;
  gap: 12px; flex-wrap: wrap; margin-bottom: 13px;
}
.chint { margin: 0; font-size: var(--fs-2xs); color: var(--muted); }

.assess { margin: 8px 0 0; font-size: var(--fs-md); line-height: 1.7; }
.assess b { font-weight: 700; }
.gaptext { color: var(--gap); }

.empty { text-align: center; }

@media (max-width: 620px) {
  /* 좁아지면 매칭 판독값이 제목 아래로 내려와 왼쪽 끝에 선다 */
  .hd-r { width: 100%; align-items: flex-start; }
  .rd { align-items: flex-start; }
}
</style>
