<script setup>
import { ref, computed } from 'vue'
import { useCareerStore } from '@/stores/careerStore.js'
import PostingCard from '@/components/posting/PostingCard.vue'

const store = useCareerStore()
const q = ref('')
const role = ref('ALL')

const ROLES = [
  { k: 'ALL',       l: '전체' },
  { k: 'BACKEND',   l: '백엔드' },
  { k: 'FRONTEND',  l: '프론트엔드' },
  { k: 'FULLSTACK', l: '풀스택' },
  { k: 'PLATFORM',  l: '플랫폼·인프라' },
]

/* 검색은 기업·직무·역량 이름을 함께 본다.
   사용자가 "쿠버네티스" 로 찾을 때 공고 제목에 그 단어가 없어도
   요구 역량에 있으면 나와야 한다. */
const list = computed(() => {
  const kw = q.value.trim().toLowerCase()
  return store.cards.filter(c => {
    if (role.value !== 'ALL' && c.posting.role !== role.value) return false
    if (!kw) return true
    const hay = [
      c.posting.company, c.posting.position,
      ...c.match.rows.map(r => r.comp.name),
    ].join(' ').toLowerCase()
    return hay.includes(kw)
  })
})

const gap = computed(() => store.topGap)

/* 추천 키워드 — 지금 내가 못 덮는 역량. 검색어로 바로 꽂아 준다. */
const suggest = computed(() => {
  const tagged = store.taggedCompetencyIds
  const need = new Set()
  store.livePostings.forEach(p =>
    store.matchFor(p).rows.filter(r => r.isGap).forEach(r => need.add(r.comp.name)))
  return [...need].slice(0, 5)
    .concat(store.competencies.filter(c => tagged.has(c.id)).slice(0, 2).map(c => c.name))
    .slice(0, 6)
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

  <!-- 추천 키워드 -->
  <section class="sug">
    <span class="sl">이런 역량도 확인해보세요.</span>
    <button v-for="s in suggest" :key="s" class="btn btn--sm"
            :aria-pressed="q === s" @click="q = q === s ? '' : s">{{ s }}</button>
  </section>

  <!-- 개수 + 정렬 -->
  <section class="count">
    <p class="cn">
      <b class="num n">{{ list.length }}</b> 개의 공고가 현재 진행중입니다.
      <span v-if="store.dueSoonCount" class="soon">· 마감 7일 내 {{ store.dueSoonCount }}건</span>
    </p>
    <div class="sorts">
      <button class="btn btn--sm" :aria-pressed="store.sort === 'match'" @click="store.sort = 'match'">매칭순</button>
      <button class="btn btn--sm" :aria-pressed="store.sort === 'deadline'" @click="store.sort = 'deadline'">마감 임박순</button>
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
        <span class="label">Filter</span>
        <button class="btn btn--quiet btn--sm"
                @click="q = ''; role = 'ALL'; store.bookmarkOnly = false">CLEAR</button>
      </div>

      <div class="fg">
        <p class="fgt">직무 계열</p>
        <div class="fgb">
          <button v-for="r in ROLES" :key="r.k" class="btn btn--sm"
                  :aria-pressed="role === r.k" @click="role = r.k">{{ r.l }}</button>
        </div>
      </div>

      <div class="fg">
        <p class="fgt">보기</p>
        <div class="fgb">
          <button class="btn btn--sm" :aria-pressed="store.bookmarkOnly"
                  @click="store.bookmarkOnly = !store.bookmarkOnly">즐겨찾기만</button>
        </div>
      </div>

      <!-- 이 서비스만의 것 — 다음에 뭘 채우면 가장 많이 움직이나 -->
      <div v-if="gap" class="nx">
        <p class="fgt">다음에 채울 것</p>
        <p class="nxn">{{ gap.competency.name }}</p>
        <p class="nxd">
          공고 {{ gap.postingCount }}건이 이걸 요구하는데, 증명할 경험이 아직 없습니다.
        </p>
        <RouterLink to="/experiences" class="btn btn--sm nxb">경험 등록하러 가기</RouterLink>
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

/* 추천 키워드 */
.sug { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 22px 0 0; }
.sl { font-size: 12.5px; color: var(--muted); margin-right: 4px; }

/* 개수 */
.count {
  display: flex; align-items: baseline; justify-content: space-between; gap: 16px; flex-wrap: wrap;
  padding: 30px 0 12px;
}
.cn { margin: 0; font-size: 15px; font-weight: 600; }
.cn .n { font-size: 20px; font-weight: 800; color: var(--accent); margin-right: 3px; }
.soon { color: var(--muted); font-weight: 500; font-size: 13px; margin-left: 4px; }
.sorts { display: flex; gap: 7px; }

/* 좌 리스트 / 우 필터 */
.cols { display: grid; grid-template-columns: minmax(0, 1fr) 232px; gap: 40px; align-items: start; }
.list {
  display: grid; gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
  align-items: stretch;
}
.empty { padding: 50px 0; text-align: center; color: var(--muted); }
.full { grid-column: 1 / -1; }

.side { position: sticky; top: 18px; display: flex; flex-direction: column; gap: 22px; padding-top: 14px; }
.sh { display: flex; align-items: center; justify-content: space-between; padding-bottom: 12px; border-bottom: 1px solid var(--ink); }
.fg { display: flex; flex-direction: column; gap: 9px; }
.fgt { margin: 0; font-size: 13px; font-weight: 700; }
.fgb { display: flex; gap: 6px; flex-wrap: wrap; }

.nx { padding: 15px 16px; background: var(--panel-sunken); border-radius: var(--r); }
.nxn { margin: 7px 0 0; font-size: 17px; font-weight: 800; letter-spacing: var(--track-tight); color: var(--gap); }
.nxd { margin: 5px 0 0; font-size: 11.5px; color: var(--muted); line-height: 1.5; }
.nxb { margin-top: 11px; width: 100%; text-decoration: none; }

@media (max-width: 900px) {
  .cols { grid-template-columns: 1fr; gap: 28px; }
  .side { position: static; order: -1; padding-top: 0; }
}
</style>
