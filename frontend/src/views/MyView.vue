<script setup>
import { computed } from 'vue'
import { useCareerStore } from '@/stores/careerStore.js'
import { useAuthStore } from '@/stores/authStore.js'
import SignInGate from '@/components/SignInGate.vue'
import PostingCard from '@/components/posting/PostingCard.vue'

const store = useCareerStore()
const auth = useAuthStore()

/* 로그아웃해도 이 화면에 머문다. 목록이 사라지고 그 자리에 로그인 안내가
   뜨므로 남의 현황이 남지 않고, 다시 로그인하면 보던 곳으로 바로 돌아온다. */
function signOut() { auth.signOut() }
const lists = computed(() => store.myLists)
const total = computed(() => lists.value.reduce((a, l) => a + l.items.length, 0))
</script>

<template>
  <section class="pagehead">
    <div class="pagehead-l">
      <h1 class="display">내 지원 현황</h1>
      <p class="pagehead-lede">담아 둔 공고와 쓰던 자소서를 한 자리에서 봅니다.</p>
    </div>
  </section>

  <SignInGate v-if="!auth.signedIn"
              desc="담아 둔 공고와 쓰던 자소서를 한 자리에서 봅니다. 내 것을 보는 화면이라 로그인이 필요합니다." />

  <template v-else>
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

  <!-- 로그아웃은 목록을 다 보고 난 뒤에야 필요한 것이라 맨 아래 오른쪽에 둔다.
       위에 두면 "내 지원 현황" 을 보러 온 사람이 제일 먼저 나가는 문을 만난다. -->
  <section class="acct">
    <p class="who">{{ auth.name }} 님으로 로그인되어 있습니다</p>
    <button class="btn btn--sm" @click="signOut">로그아웃</button>
  </section>
  </template>
</template>

<style scoped>
/* 목록과 확실히 떨어뜨린다 — 붙어 있으면 마지막 그룹의 일부로 읽힌다 */
.acct {
  display: flex; align-items: center; justify-content: flex-end; gap: 14px;
  margin: 40px 0 8px; padding-top: 18px; border-top: 1px solid var(--line);
}
.who { margin: 0; font-size: var(--fs-xs); color: var(--muted); }

.count { margin: 0 0 6px; font-size: var(--fs-md); font-weight: 600; padding-bottom: 14px; border-bottom: 2px solid var(--ink); }
.count .n { font-size: var(--fs-xl); font-weight: 800; color: var(--accent); margin-right: 3px; }

.grp { padding: 28px 0 4px; }
.gh { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.gt { margin: 0; font-size: var(--fs-lg); font-weight: 800; letter-spacing: var(--track-tight); }
.gn {
  display: inline-block; margin-left: 8px; padding: 1px 8px;
  border-radius: var(--pill); background: var(--ink); color: var(--panel);
  font-family: var(--mono); font-size: var(--fs-2xs); font-weight: 600;
}
.gd { margin: 0; font-size: var(--fs-xs); color: var(--muted); }

.grid { display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(310px, 1fr)); align-items: stretch; }
.empty {
  margin: 0; padding: 26px; text-align: center; font-size: var(--fs-sm); color: var(--muted);
  background: var(--panel-sunken); border-radius: var(--r);
}
.empty b { color: var(--ink); }

/* 마감 지난 것은 한 단계 물러나 보이게. 지우지는 않되 지금 할 일과 구분한다. */
.closed { border-top: 1px solid var(--line); margin-top: 14px; }
.closed .grid { opacity: 0.72; }
.closed .grid:hover { opacity: 1; }
.closed .gn { background: var(--muted); }
</style>
