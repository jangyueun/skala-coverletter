<script setup>
import { computed } from 'vue'
import { useCareerStore } from '@/stores/careerStore.js'
import PostingCard from '@/components/posting/PostingCard.vue'

const store = useCareerStore()

const sorts = [
  { k: 'match',    label: '매칭순' },
  { k: 'deadline', label: '마감 임박순' },
]

const gap = computed(() => store.topGap)
</script>

<template>
  <p class="label">CareerFit · Vol(01)</p>
  <h1 class="display">공고 찾기</h1>
  <p class="lede">
    지원 가능한 공고를 <b>나와의 매칭도</b>와 <b>자소서 진행 상태</b>로 한 판에 놓는다.
    오늘 무엇부터 손대야 하는지가 이 화면에서 끝나야 한다.
  </p>

  <!-- 계기판 — 읽는 값 3개 -->
  <section class="readout" aria-label="현황">
    <div class="panel cell">
      <div class="num num--lg">{{ store.livePostings.length }}</div>
      <p class="label">활성 공고</p>
    </div>

    <div class="panel cell">
      <div class="num num--lg" :class="{ warn: store.dueSoonCount }">{{ store.dueSoonCount }}</div>
      <p class="label">마감 7일 내</p>
    </div>

    <!-- 평균 매칭 대신 "다음에 뭘 채우나". 평균은 행동으로 이어지지 않는다. -->
    <div class="panel cell cell--wide">
      <template v-if="gap">
        <div class="gapname">{{ gap.competency.name }}</div>
        <p class="label">이 역량 하나가 공고 {{ gap.postingCount }}건의 갭</p>
      </template>
      <template v-else>
        <div class="num num--lg ok">0</div>
        <p class="label">비어 있는 요구 역량</p>
      </template>
    </div>
  </section>

  <!-- 조작부 -->
  <section class="controls" aria-label="정렬과 필터">
    <div class="grp">
      <span class="label ck">Sort</span>
      <button
        v-for="s in sorts" :key="s.k"
        class="btn btn--sm"
        :aria-pressed="store.sort === s.k"
        @click="store.sort = s.k"
      >{{ s.label }}</button>
    </div>
    <button
      class="btn btn--sm"
      :aria-pressed="store.bookmarkOnly"
      @click="store.bookmarkOnly = !store.bookmarkOnly"
    >즐겨찾기만</button>
  </section>

  <section class="grid" aria-label="공고 목록">
    <PostingCard
      v-for="c in store.cards"
      :key="c.posting.id"
      :card="c"
      @bookmark="store.toggleBookmark"
    />
  </section>

  <p v-if="!store.cards.length" class="empty panel">
    조건에 맞는 공고가 없습니다. 즐겨찾기 필터를 꺼 보세요.
  </p>
</template>

<style scoped>
.lede { max-width: 56ch; color: var(--muted); margin: 14px 0 0; }
.lede b { color: var(--ink); font-weight: 600; }

.readout {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px;
  margin: 26px 0 0;
}
.cell { padding: 14px 16px 12px; display: flex; flex-direction: column; gap: 3px; }
.cell--wide { grid-column: span 2; }
.num--lg.warn { color: var(--gap); }
.num--lg.ok { color: var(--ok); }
.gapname {
  font-size: 19px; font-weight: 800; letter-spacing: var(--track-display);
  line-height: 1.1; color: var(--gap);
}

.controls {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  margin: 22px 0 0; padding: 12px 0;
  border-top: 1px solid var(--line); border-bottom: 1px solid var(--line);
}
.grp { display: flex; align-items: center; gap: 7px; }
.ck { margin-right: 2px; }
.controls > :last-child { margin-left: auto; }

.grid {
  display: grid; gap: 12px; margin: 20px 0 0;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
}
.empty { padding: 30px; text-align: center; color: var(--muted); margin-top: 20px; }

@media (max-width: 720px) {
  .readout { grid-template-columns: repeat(2, 1fr); }
  .cell--wide { grid-column: span 2; }
  .grid { grid-template-columns: 1fr; }
}
</style>
