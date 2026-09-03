<script setup>
import { ref, computed } from 'vue'
import { useCareerStore } from '@/stores/careerStore.js'
import PostingCard from '@/components/posting/PostingCard.vue'

const store = useCareerStore()
const q = ref('')
const role = ref('ALL')
const picked = ref(new Set())      // 선택된 competencyId

const ROLES = [
  { k: 'ALL',       l: '전체' },
  { k: 'BACKEND',   l: '백엔드' },
  { k: 'FRONTEND',  l: '프론트엔드' },
  { k: 'FULLSTACK', l: '풀스택' },
  { k: 'PLATFORM',  l: '플랫폼·인프라' },
]

const CAT_LABEL = {
  ROLE: '직무 역량', TECH: '기술·언어', SOFT: '일하는 방식',
  DOMAIN: '산업', VALUE: '인재상',
}

/* 사전 전체를 범주별로 묶어 낸다. 접어 두면 있는 줄도 모른다. */
const groups = computed(() =>
  ['ROLE', 'TECH', 'SOFT', 'DOMAIN', 'VALUE'].map(k => ({
    k, label: CAT_LABEL[k],
    items: store.competencies.filter(c => c.category === k),
  })))

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
  q.value = ''; role.value = 'ALL'; picked.value = new Set(); store.bookmarkOnly = false
}
const activeCount = computed(() =>
  picked.value.size + (role.value !== 'ALL' ? 1 : 0)
  + (store.bookmarkOnly ? 1 : 0) + (q.value.trim() ? 1 : 0))

/* 검색은 기업·직무·역량 이름을 함께 본다.
   "쿠버네티스" 로 찾을 때 공고 제목에 그 단어가 없어도 요구 역량에 있으면 나와야 한다. */
const list = computed(() => {
  const kw = q.value.trim().toLowerCase()
  return store.cards.filter(c => {
    if (role.value !== 'ALL' && c.posting.role !== role.value) return false
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
  <!-- 히어로 — 좌 거대 영문 타이틀 / 우 카피 -->
  <section class="hero">
    <div>
      <h1 class="display">JOBS</h1>
      <p class="sub">공고 찾기</p>
    </div>
    <div class="copy">
      <p class="ch">경험을 쌓는 순서가<br>지원하는 순서가 되도록</p>
      <p class="cb">
        등록한 경험이 어떤 공고와 맞는지, 무엇이 비어 있는지<br>
        한 화면에서 봅니다. 오늘 무엇부터 손대야 하는지가 여기서 끝나야 합니다.
      </p>
    </div>
  </section>

  <!-- 전폭 회색 검색 밴드 -->
  <section class="band">
    <div class="band-in">
      <span class="mag" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2.2">
          <circle cx="10.5" cy="10.5" r="6.5" /><path d="M15.5 15.5 L21 21" stroke-linecap="round" />
        </svg>
      </span>
      <label for="q" class="blabel">공고 검색</label>
      <input id="q" v-model="q" class="q" placeholder="기업, 직무, 역량으로 찾아보세요">
      <button v-if="q" class="btn btn--sm clr" @click="q = ''">지우기</button>
    </div>
  </section>

  <!-- 개수 + 정렬.
       아래 .cols 와 같은 그리드를 써서 목록 열 안에 넣는다 —
       전폭으로 두면 정렬 버튼이 사이드바 위까지 나가 목록과 안 맞는다. -->
  <section class="count">
    <div class="count-in">
      <p class="cn">
        <b class="num n">{{ list.length }}</b> 개의 공고가 현재 진행중입니다.
        <span v-if="store.dueSoonCount" class="soon">· 마감 7일 내 {{ store.dueSoonCount }}건</span>
      </p>
      <div class="sorts">
        <button class="btn btn--sm" :aria-pressed="store.sort === 'match'" @click="store.sort = 'match'">매칭순</button>
        <button class="btn btn--sm" :aria-pressed="store.sort === 'deadline'" @click="store.sort = 'deadline'">마감 임박순</button>
      </div>
    </div>
  </section>

  <!-- 좌 리스트 / 우 필터 -->
  <div class="cols">
    <section class="list" aria-label="공고 목록">
      <PostingCard v-for="c in list" :key="c.posting.id" :card="c" @bookmark="store.toggleBookmark" />
      <p v-if="!list.length" class="empty full">
        조건에 맞는 공고가 없습니다. 검색어나 필터를 지워 보세요.
      </p>
    </section>

    <aside class="side" aria-label="필터">
      <div class="sh">
        <p class="sht">필터<span v-if="activeCount" class="badge">{{ activeCount }}</span></p>
        <button class="btn btn--sm" :disabled="!activeCount" @click="clearAll">초기화</button>
      </div>

      <!-- 즐겨찾기가 맨 위 — 가장 자주 쓰는 필터다 -->
      <div class="fg">
        <button class="btn btn--sm w" :aria-pressed="store.bookmarkOnly"
                @click="store.bookmarkOnly = !store.bookmarkOnly">
          {{ store.bookmarkOnly ? '★ 즐겨찾기만 보는 중' : '☆ 즐겨찾기만' }}
        </button>
      </div>

      <div class="fg">
        <p class="fgt">직무 계열</p>
        <div class="fgb">
          <button v-for="r in ROLES" :key="r.k" class="btn btn--sm"
                  :aria-pressed="role === r.k" @click="role = r.k">{{ r.l }}</button>
        </div>
      </div>

      <!-- 역량 사전. 범주별로 접어 두고 한 번에 하나만 편다. -->
      <div v-for="g in groups" :key="g.k" class="acc">
        <button class="acch" :aria-expanded="openCat === g.k" @click="toggleCat(g.k)">
          <span class="accn">{{ g.label }}</span>
          <span v-if="pickedIn(g.k)" class="accp">{{ pickedIn(g.k) }}</span>
          <span class="fgn">{{ g.items.length }}</span>
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
.hero {
  display: flex; justify-content: space-between; align-items: flex-end; gap: 40px; flex-wrap: wrap;
  padding: 46px 0 40px;
}
.sub { margin: 10px 0 0; font-size: 15px; font-weight: 600; color: var(--ink-2); }
.copy { max-width: 44ch; }
.ch { margin: 0; font-size: clamp(1.2rem, 2.6vw, 1.75rem); font-weight: 700; line-height: 1.45; letter-spacing: var(--track-tight); }
.cb { margin: 14px 0 0; font-size: 13px; color: var(--muted); line-height: 1.75; }

/* 전폭 밴드 — wrap 의 좌우 여백을 넘어 화면 끝까지 */
.band {
  background: var(--panel-sunken);
  margin: 0 calc(50% - 50vw); padding: 0 calc(50vw - 50%);
}
.band-in { display: flex; align-items: center; gap: 14px; max-width: 1160px; margin: 0 auto; padding: 22px 0; }
.mag {
  width: 40px; height: 40px; flex: none; border-radius: 50%;
  background: var(--ink); color: var(--panel); display: grid; place-items: center;
}
.blabel { font-size: 13px; font-weight: 700; flex: none; }
.q {
  flex: 1; min-width: 0; border: none; background: transparent; outline: none;
  font-size: clamp(15px, 2.2vw, 20px); font-weight: 500; color: var(--ink);
}
.q::placeholder { color: var(--faint); font-weight: 400; }
.clr { flex: none; }

/* 개수 — .cols 와 같은 그리드. 둘째 열(사이드바 자리)은 비워 둔다. */
.count {
  display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 40px;
  padding: 30px 0 12px;
}
.count-in {
  display: flex; align-items: baseline; justify-content: space-between; gap: 16px; flex-wrap: wrap;
}
.cn { margin: 0; font-size: 15px; font-weight: 600; }
.cn .n { font-size: 20px; font-weight: 800; color: var(--accent); margin-right: 3px; }
.soon { color: var(--muted); font-weight: 500; font-size: 13px; margin-left: 4px; }
.sorts { display: flex; gap: 7px; margin-left: auto; }

/* 좌 리스트 / 우 필터 */
.cols { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 40px; align-items: start; }
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
/* 아래 그룹 제목(직무 계열·기술·언어)과 같은 목소리. 모노 대문자는 여기 안 어울린다. */
.sht {
  margin: 0; display: flex; align-items: center;
  font-size: 15px; font-weight: 800; letter-spacing: var(--track-tight);
}
.badge {
  display: inline-grid; place-items: center; min-width: 16px; height: 16px; padding: 0 4px;
  margin-left: 6px; border-radius: var(--pill);
  background: var(--accent); color: var(--accent-ink); font-size: 9.5px; font-weight: 700;
}
.fg { display: flex; flex-direction: column; gap: 9px; }
.fgt { margin: 0; font-size: 13px; font-weight: 700; }
.fgn { font-family: var(--mono); font-size: 10px; color: var(--faint); font-weight: 500; }
.fgb { display: flex; gap: 6px; flex-wrap: wrap; }
.w { width: 100%; }

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
.accn { font-size: 13px; font-weight: 700; }
/* 접혀 있어도 몇 개 골랐는지는 보인다 */
.accp {
  display: inline-grid; place-items: center; min-width: 16px; height: 16px; padding: 0 4px;
  border-radius: var(--pill); background: var(--accent); color: var(--accent-ink);
  font-size: 9.5px; font-weight: 700;
}
.chev { margin-left: auto; font-family: var(--mono); font-size: 14px; color: var(--muted); line-height: 1; }
.acch[aria-expanded='true'] .chev { color: var(--ink); }

@media (max-width: 900px) {
  .cols, .count { grid-template-columns: 1fr; gap: 28px; }
  .side { position: static; order: -1; padding-top: 0; }
}
</style>
