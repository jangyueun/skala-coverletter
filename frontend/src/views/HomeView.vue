<script setup>
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/auth.js'
import { usePostingsStore } from '@/stores/postings.js'
import { useUiStore } from '@/stores/ui.js'
import { useDerivedStore } from '@/stores/derived.js'
import { groupByCategory } from '@/domain/competency.js'
import Skeleton from '@/components/state/Skeleton.vue'
import ErrorNote from '@/components/state/ErrorNote.vue'
import PostingCard from '@/components/posting/PostingCard.vue'

const auth = useAuthStore()
const P = usePostingsStore()
const ui = useUiStore()
const D = useDerivedStore()
const q = ref('')
const picked = ref(new Set())      // 선택된 competencyId

/* 직무 계열 필터는 뺐다. v6 에서 공고의 role 이 없어져 데이터가 없다 —
   "백엔드" 는 기업마다 부르는 이름이 달라 사람이 붙이던 라벨이었고, 그 자리는 역량 필터가 맡는다.
   "쿠버네티스를 요구하는 공고" 가 "플랫폼 계열" 보다 정확하다. */

/* 사전 전체를 범주별로 묶어 낸다. 접어 두면 있는 줄도 모른다. */
const groups = computed(() => groupByCategory(P.competencies))

/* 한 번에 한 범주만 편다. 다섯을 다 펼치면 39개가 한꺼번에 쏟아져
   사이드바가 목록보다 길어진다. */
const openCat = ref(null)
const toggleCat = k => { openCat.value = openCat.value === k ? null : k }

/* 접힌 범주에서도 몇 개를 골랐는지는 보여야 한다 —
   안 그러면 필터가 걸린 줄 모르고 결과가 적다고 오해한다. */
const pickedIn = k => groups.value.find(g => g.k === k)?.items.filter(c => picked.value.has(c.id)).length ?? 0

function toggle(id) {
  const next = new Set(picked.value)
  next.has(id) ? next.delete(id) : next.add(id)
  picked.value = next
}
function clearAll() {
  q.value = ''; picked.value = new Set()
}
const activeCount = computed(() => picked.value.size + (q.value.trim() ? 1 : 0))

/* 검색은 기업·직무·역량 이름을 함께 본다.
   "쿠버네티스" 로 찾을 때 공고 제목에 그 단어가 없어도 요구 역량에 있으면 나와야 한다.
   실제 GET /api/postings 도 q · competencyId 를 같은 뜻으로 받는다(OR). */
const list = computed(() => {
  const kw = q.value.trim().toLowerCase()
  return D.cards.filter(c => {
    // 고른 역량을 **하나라도** 요구하는 공고. AND 로 걸면 대부분 0건이 된다.
    if (picked.value.size && !c.match.rows.some(r => picked.value.has(r.competencyId))) return false
    if (!kw) return true
    const hay = [c.posting.company, c.posting.position, ...c.match.rows.map(r => r.comp.name)]
      .join(' ').toLowerCase()
    return hay.includes(kw)
  })
})
</script>

<template>
  <!-- 히어로 — 제목 하나와 한 줄 설명. 경험 관리와 같은 어법이다.
       영문 타이틀 + 한글 부제 + 카피 두 문단은 같은 말을 네 번 하는 것이었다. -->
  <section class="pagehead">
    <div class="pagehead-l">
      <h1 class="display">공고 찾기</h1>
      <p class="pagehead-lede">내가 저장한 경험과 매칭되는 공고를 찾아보세요!</p>
    </div>
  </section>

  <!-- 좌 목록 / 우 필터. 검색·정렬 줄을 목록 열 안에 넣어야
       필터와 목록이 같은 높이에서 시작한다. 밖으로 빼면 필터만 한 줄 밑에서
       시작해 두 열의 머리가 어긋난다. -->
  <div class="cols">
    <section class="listcol">
      <!-- 검색은 목록 바로 위에 둔다. 자기가 거르는 것 위에 있어야
           "이 목록이 검색 결과다" 가 보인다. 개수는 화면이 이미 보여 준다. -->
      <div class="count-in">
    <div class="srch">
          <span class="mag" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2">
            <circle cx="10.5" cy="10.5" r="6.5" /><path d="M15.5 15.5 L21 21" stroke-linecap="round" />
            </svg>
          </span>
          <label for="q" class="vh">공고 검색</label>
          <input id="q" v-model="q" class="q" placeholder="기업, 직무, 역량으로 찾아보세요">
          <button v-if="q" type="button" class="clr" aria-label="검색어 지우기" @click="q = ''">×</button>
        </div>
        <!-- 매칭순은 내 경험과 맞춰 본 결과로 정렬하는 것이라 로그인해야 뜻이 있다 -->
        <div class="sorts">
          <button v-if="auth.signedIn" class="btn btn--sm" :aria-pressed="ui.sort === 'match'" @click="ui.sort = 'match'">매칭순</button>
          <button class="btn btn--sm" :aria-pressed="ui.sort === 'deadline'" @click="ui.sort = 'deadline'">마감 임박순</button>
        </div>
      </div>

    <section class="list" aria-label="공고 목록">
      <Skeleton v-if="!D.ready || !auth.loaded" :rows="8" class="full" />
      <ErrorNote v-else-if="P.error" :error="P.error" what="공고 불러오기" class="full" @retry="P.load()" />
      <template v-else>
        <PostingCard v-for="c in list" :key="c.posting.id" :card="c" @bookmark="ui.toggleBookmark" />
        <p v-if="!list.length" class="empty full">
          조건에 맞는 공고가 없습니다. 검색어나 필터를 지워 보세요.
        </p>
      </template>
      </section>
    </section>

    <aside class="side" aria-label="필터">
      <div class="sh">
        <p class="sht">필터<span v-if="activeCount" class="badge">{{ activeCount }}</span></p>
        <button class="btn btn--sm" :disabled="!activeCount" @click="clearAll">초기화</button>
      </div>

      <!-- 역량 사전. 범주별로 접어 두고 한 번에 하나만 편다. -->
      <div v-for="g in groups" :key="g.k" class="acc">
        <button class="acch" :aria-expanded="openCat === g.k" @click="toggleCat(g.k)">
          <span class="accn">{{ g.label }}</span>
          <span class="fgn">{{ g.items.length }}</span>
          <span v-if="pickedIn(g.k)" class="accp">{{ pickedIn(g.k) }}</span>
          <span class="chev" aria-hidden="true">{{ openCat === g.k ? '−' : '+' }}</span>
        </button>
        <div v-show="openCat === g.k" class="fgb">
          <button v-for="c in g.items" :key="c.id" class="btn btn--sm"
                  :aria-pressed="picked.has(c.id)" @click="toggle(c.id)">{{ c.name }}</button>
        </div>
      </div>
    </aside>
  </div>
</template>

<style scoped>
/* 히어로 */
/* 히어로는 진입 장식이지 화면의 본론이 아니다. 본론은 바로 아래 검색과 목록이라
   위아래 여백과 글자 크기를 줄여 첫 화면에 공고가 같이 들어오게 한다. */
/* 검색은 한 덩어리다 — 회색 면 하나에 돋보기·입력·지우기가 같이 앉는다.
   밴드일 때는 배경이 화면 끝까지 갔지만, 이제 제목 옆 상자라 pill 로 닫는다. */
.srch {
  display: flex; align-items: center; gap: 10px; flex: 1 1 300px; max-width: 480px; min-width: 0;
  padding: 11px 16px; background: var(--panel-sunken); border-radius: var(--pill);
  border: 1px solid transparent;
  transition: border-color var(--release) linear, background var(--release) linear;
}
.srch:focus-within { background: var(--panel); border-color: var(--accent); }
.mag { color: var(--ink); display: grid; place-items: center; flex: none; }
.q {
  flex: 1; min-width: 0; border: none; background: transparent; outline: none;
  font-size: var(--fs-md); font-weight: 500; color: var(--ink);
}
.q::placeholder { color: var(--muted); font-weight: 400; }
.clr {
  flex: none; width: 20px; height: 20px; padding: 0; border: none; border-radius: 50%;
  background: var(--line); color: var(--ink-2); cursor: pointer;
  font-size: var(--fs-sm); line-height: 1;
  transition: background var(--release) linear, color var(--release) linear;
}
.clr:hover { background: var(--ink); color: var(--panel); }

/* 라벨은 화면에서 뺀다 — 돋보기와 플레이스홀더가 이미 말한다.
   지우지는 않는다. 스크린리더에는 입력의 이름이 있어야 한다. */
.vh {
  position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
  overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
}

.sorts { display: flex; gap: 7px; margin-left: auto; flex: none; }

/* 좌 리스트 / 우 필터 */
/* 두 열 사이에 옅은 선. 선은 목록 열이 긋는다 — 필터는 sticky 라
   테두리를 걸면 스크롤을 따라다니는 짧은 토막이 된다. */
.cols { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 28px; align-items: start; }
.listcol {
  min-width: 0; display: flex; flex-direction: column; gap: 14px;
  padding-right: 28px; border-right: 1px solid var(--line-soft);
}
.count-in {
  display: flex; align-items: center; justify-content: space-between; gap: 14px; flex-wrap: wrap;
}
.list {
  display: grid; gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
  align-items: stretch;
}
.empty { padding: 50px 0; text-align: center; color: var(--muted); }
.full { grid-column: 1 / -1; }

/* 필터가 길어지므로 자체 스크롤을 준다 — 페이지가 필터 길이에 끌려가지 않게 */
/* padding-top 을 두지 않는다. 카드 그리드와 위쪽 선이 맞아야
   두 열이 같은 줄에서 시작하는 것으로 읽힌다. */
.side {
  position: sticky; top: 18px;
  display: flex; flex-direction: column; gap: 20px;
}
.sh {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding-bottom: 11px; border-bottom: 2px solid var(--ink);
  position: sticky; top: 0; background: var(--panel); z-index: 1;
}
/* 아래 범주 제목(직무 역량·기술·언어)과 같은 목소리. 모노 대문자는 여기 안 어울린다. */
.sht {
  margin: 0; display: flex; align-items: center;
  font-size: var(--fs-md); font-weight: 800; letter-spacing: var(--track-tight);
}
.badge {
  display: inline-grid; place-items: center; min-width: 16px; height: 16px; padding: 0 4px;
  margin-left: 6px; border-radius: var(--pill);
  background: var(--accent); color: var(--accent-ink); font-size: var(--fs-3xs); font-weight: 700;
}
.fgn { font-family: var(--mono); font-size: var(--fs-3xs); color: var(--muted); font-weight: 500; }
/* 전체 개수는 제목에 딸린 수라 이름 바로 옆에 붙인다.
   오른쪽 끝에는 "내가 고른 수"(주황)와 펼침 표시만 남는다 —
   접힌 줄이 다섯이라 그 둘의 오른쪽 끝이 맞아야 훑힌다.
   margin-right:auto 를 여기 두면 배지가 없는 줄에서도 펼침 표시가 오른쪽에 선다. */
.acch .fgn { margin-right: auto; }
.fgb { display: flex; gap: 6px; flex-wrap: wrap; }

/* 아코디언 — 접힌 상태가 기본이다 */
.acc { display: flex; flex-direction: column; gap: 9px; }
.acch {
  display: flex; align-items: center; gap: 7px; width: 100%;
  padding: 9px 2px; background: transparent; border: none;
  border-bottom: 1px solid var(--line-soft);
  cursor: pointer; text-align: left; font: inherit; color: inherit;
  transition: border-color var(--release) linear;
}
.acch:hover { border-bottom-color: var(--ink); }
.acch[aria-expanded='true'] { border-bottom-color: var(--ink); }
.accn { font-size: var(--fs-sm); font-weight: 700; }
/* 접혀 있어도 몇 개 골랐는지는 보인다 */
.accp {
  display: inline-grid; place-items: center; min-width: 16px; height: 16px; padding: 0 4px;
  border-radius: var(--pill); background: var(--accent); color: var(--accent-ink);
  font-size: var(--fs-3xs); font-weight: 700;
}
.chev { font-family: var(--mono); font-size: var(--fs-md); color: var(--muted); line-height: 1; }
.acch[aria-expanded='true'] .chev { color: var(--ink); }

@media (max-width: 900px) {
  .cols { grid-template-columns: 1fr; gap: 28px; }
  /* 한 열이 되면 세로선은 뜻이 없다 */
  .listcol { padding-right: 0; border-right: none; }
  .side { position: static; order: -1; padding-top: 0; }
}
</style>
