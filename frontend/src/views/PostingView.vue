<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCareerStore } from '@/stores/careerStore.js'
import { SCORE, dday, isClosed } from '@/lib/matching.js'
import MatchTable from '@/components/posting/MatchTable.vue'
import SignInGate from '@/components/SignInGate.vue'
import { useAuthStore } from '@/stores/authStore.js'
import EssayEditor from '@/components/posting/EssayEditor.vue'

const props = defineProps({ id: { type: [String, Number], required: true } })
const store = useCareerStore()
const auth = useAuthStore()
const router = useRouter()

/* 탭은 라우트가 아니라 컴포넌트 상태다.
   탭을 옮긴 뒤 뒤로 가기를 누르면 목록으로 돌아가야지 이전 탭으로 가면 안 된다. */
const tab = ref('content')
const TABS = [
  { k: 'content', label: '공고 내용' },
  { k: 'match',   label: '매칭 상세 분석' },
  { k: 'essay',   label: '자소서' },
]

const posting = computed(() => store.postingById(props.id))
const match   = computed(() => posting.value ? store.matchFor(posting.value) : null)
const essay   = computed(() => posting.value ? store.cards.find(c => c.posting.id === posting.value.id)?.essay : null)
const pct     = computed(() => Math.round((match.value?.overall ?? 0) * 100))
const gaps    = computed(() => match.value?.rows.filter(r => r.isGap) ?? [])
const d       = computed(() => posting.value ? dday(posting.value.deadline) : 0)
const closed  = computed(() => !!posting.value && isClosed(posting.value.deadline))

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
        <!-- 회사는 눈썹 문구가 아니라 읽는 줄이다. 카드에서 회사가 직무의
             70% 크기로 또렷하게 읽히는 것과 같은 비중으로 올린다. -->
        <p class="co"><b>{{ posting.company }}</b><span class="rolek"> · {{ roleLabel }}</span></p>
        <h1 class="display pos">{{ posting.position }}</h1>
        <div class="meta">
          <!-- 끝난 공고라는 사실이 제일 먼저 읽혀야 한다. 이걸 놓치면
               아래의 매칭·자소서를 아직 지원할 수 있는 것으로 읽는다. -->
          <span v-if="closed" class="tag tag--closed">마감</span>
          <span class="tag tag--ink">
            <b v-if="!closed" class="num">D-{{ d }}</b>{{ !closed ? '\u00a0' : '' }}{{ posting.deadline }} 마감
          </span>
          <span class="tag" :class="essay?.state === 'DONE' ? 'tag--ok' : ''">{{ essay?.label }}</span>
        </div>
      </div>

      <!-- 매칭과 즐겨찾기는 탭이 아니라 이 공고 자체에 붙는 것이라 머리에 둔다.
           상자로 감싸지 않는다 — 이 화면에서 테두리는 탭 아래 내용의 몫이다. -->
      <div v-if="auth.signedIn" class="hd-r">
        <button class="btn btn--sm bm" :aria-pressed="store.bookmarks.has(posting.id)"
                @click="store.toggleBookmark(posting.id)">
          {{ store.bookmarks.has(posting.id) ? '★ 즐겨찾기' : '☆ 즐겨찾기' }}
        </button>
        <!-- 막대 게이지는 뺐다. .gauge 정의가 어디에도 없어 10개 <i> 가
             보이지 않는 채로 18px 만 먹고 있었고, 그게 판정을 퍼센트에서
             떼어 놓고 있었다. 숫자가 이미 같은 값을 말한다. -->
        <div class="rd">
          <p class="pctline">
            <span class="rl">매칭률 :</span>
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
        <pre class="raw">{{ posting.rawText }}</pre>
      </div>

      <div class="panel body">
        <p class="subhead">연관 태그</p>
        <div class="tags">
          <span v-for="r in match.rows" :key="r.competencyId" class="tag">{{ r.comp.name }}</span>
        </div>
      </div>

      <div class="panel body">
        <p class="subhead">관련 공고</p>

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
      <SignInGate v-if="!auth.signedIn"
                  desc="이 공고가 요구하는 역량을 내 경험과 맞춰 봅니다. 내 경험을 읽어야 하는 화면이라 로그인이 필요합니다." />

      <template v-else>
      <div class="panel body">
        <p class="subhead">평가</p>
        <p class="assess">
          요구 역량 {{ match.rows.length }}개 중 <b>{{ match.rows.length - gaps.length }}개</b>를 덮어
          매칭 <b class="num">{{ pct }}%</b>입니다.
          <template v-if="gaps.length">
            반면 <b class="gaptext">{{ gapPhrase }}</b>
            증명할 경험이 없거나 너무 약합니다 — <b>보강이 필요한 역량</b>입니다.
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
      <SignInGate v-if="!auth.signedIn"
                  desc="자소서 초안은 계정에 저장됩니다. 로그인하면 쓰던 곳부터 이어서 쓸 수 있습니다." />

      <div v-else-if="!questions.length" class="panel body empty">
        <p class="subhead">문항 없음</p>
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
/* 카드의 회사:직무 비율(12.5 : 18 = 0.69)을 상세에도 그대로 준다.
   41.6 × 0.69 ≈ 26px. 직무 계열은 회사를 한정하는 말이라 같이 키우지 않는다 —
   둘 다 26px 이면 회사 이름이 어디서 끝나는지가 안 보인다. */
.co { margin: 0; display: flex; align-items: baseline; gap: 0; letter-spacing: var(--track-tight); }
.co b { font-size: var(--fs-2xl); font-weight: 700; color: var(--ink-2); line-height: 1.25; }
.rolek { font-size: var(--fs-md); font-weight: 500; color: var(--muted); }
.pos { margin-top: 4px; font-size: clamp(1.7rem, 4.4vw, 2.6rem); }
.meta { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 12px; }

.hd-r { flex: none; display: flex; flex-direction: column; align-items: flex-end; gap: 14px; }
.bm { flex: none; }
.rd { display: flex; flex-direction: column; align-items: flex-end; gap: 1px; }
/* 숫자·% ·라벨이 한 줄이다. 라벨을 위에 얹으면 그만큼 판정이 아래로 밀린다. */
.pctline { margin: 0; display: flex; align-items: baseline; gap: 1px; }
.pctn { font-size: var(--fs-2xl); font-weight: 800; line-height: 1.1; }
.pc { font-size: var(--fs-md); font-weight: 700; color: var(--muted); }
.rl { margin-right: 7px; font-size: var(--fs-xs); font-weight: 600; color: var(--muted); }
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
.rlk { color: var(--ink); }
.rel {
  display: flex; justify-content: space-between; align-items: center; gap: 12px; width: 100%;
  padding: 9px 12px; margin-bottom: 6px; text-align: left; font: inherit; color: inherit;
  border-top-width: 2px;
}
.rn { font-weight: 600; font-size: var(--fs-sm); min-width: 0; }
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
.empty .btn { margin-top: 12px; }

@media (max-width: 620px) {
  /* 좁아지면 매칭 판독값이 제목 아래로 내려와 왼쪽 끝에 선다 */
  .hd-r { width: 100%; align-items: flex-start; }
  .rd { align-items: flex-start; }
}
</style>
