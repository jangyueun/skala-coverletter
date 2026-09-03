<script setup>
import { computed } from 'vue'
import { useCareerStore } from '@/stores/careerStore.js'
import PostingCard from '@/components/posting/PostingCard.vue'

const store = useCareerStore()
const lists = computed(() => store.myLists)
const total = computed(() => lists.value.reduce((a, l) => a + l.items.length, 0))
</script>

<template>
  <section class="hero">
    <div>
      <h1 class="display">MY</h1>
      <p class="sub">내 지원 현황</p>
    </div>
    <div class="copy">
      <p class="ch">담아 둔 것과<br>쓰다 만 것</p>
      <p class="cb">
        담아 둔 공고, 쓰다 만 자소서, 다 쓴 자소서를 한 자리에 놓습니다.<br>
        마감이 지난 것도 지우지 않습니다 — 다음 지원에 다시 쓸 문장이 거기 있습니다.
      </p>
    </div>
  </section>

  <p class="count"><b class="num n">{{ total }}</b> 개의 공고가 내 목록에 있습니다.</p>

  <section v-for="l in lists" :key="l.k" class="grp" :class="{ closed: l.k === 'closed' }">
    <header class="gh">
      <h2 class="gt">{{ l.title }}<span class="gn">{{ l.items.length }}</span></h2>
      <p class="gd">{{ l.desc }}</p>
    </header>

    <div v-if="l.items.length" class="grid">
      <PostingCard v-for="c in l.items" :key="c.posting.id" :card="c" @bookmark="store.toggleBookmark" />
    </div>

    <!-- 비어 있으면 그 이유와 다음 행동을 말한다. 빈 상자만 두면 고장으로 읽힌다. -->
    <p v-else class="empty">
      <template v-if="l.k === 'bookmark'">
        아직 담아 둔 공고가 없습니다. 공고 카드의 <b>☆ 즐겨찾기</b>를 누르면 여기 모입니다.
      </template>
      <template v-else-if="l.k === 'writing'">쓰다 만 자소서가 없습니다.</template>
      <template v-else-if="l.k === 'done'">아직 끝까지 쓴 자소서가 없습니다.</template>
      <template v-else>마감까지 자소서를 완성한 공고가 아직 없습니다.</template>
    </p>
  </section>
</template>

<style scoped>
.hero {
  display: flex; justify-content: space-between; align-items: flex-end; gap: 40px; flex-wrap: wrap;
  padding: 46px 0 34px;
}
.sub { margin: 10px 0 0; font-size: 15px; font-weight: 600; color: var(--ink-2); }
.copy { max-width: 46ch; }
.ch { margin: 0; font-size: clamp(1.2rem, 2.6vw, 1.75rem); font-weight: 700; line-height: 1.45; letter-spacing: var(--track-tight); }
.cb { margin: 14px 0 0; font-size: 13px; color: var(--muted); line-height: 1.75; }

.count { margin: 0 0 6px; font-size: 15px; font-weight: 600; padding-bottom: 14px; border-bottom: 2px solid var(--ink); }
.count .n { font-size: 20px; font-weight: 800; color: var(--accent); margin-right: 3px; }

.grp { padding: 28px 0 4px; }
.gh { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.gt { margin: 0; font-size: 18px; font-weight: 800; letter-spacing: var(--track-tight); }
.gn {
  display: inline-block; margin-left: 8px; padding: 1px 8px;
  border-radius: var(--pill); background: var(--ink); color: var(--panel);
  font-family: var(--mono); font-size: 11px; font-weight: 600;
}
.gd { margin: 0; font-size: 12.5px; color: var(--muted); }

.grid { display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(310px, 1fr)); align-items: stretch; }
.empty {
  margin: 0; padding: 26px; text-align: center; font-size: 13px; color: var(--muted);
  background: var(--panel-sunken); border-radius: var(--r);
}
.empty b { color: var(--ink); }

/* 마감 지난 것은 한 단계 물러나 보이게. 지우지는 않되 지금 할 일과 구분한다. */
.closed { border-top: 1px solid var(--line); margin-top: 14px; }
.closed .grid { opacity: 0.72; }
.closed .grid:hover { opacity: 1; }
.closed .gn { background: var(--muted); }
</style>
