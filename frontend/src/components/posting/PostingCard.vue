<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.js'
import { deadlineLabel } from '@/domain/deadline.js'

const props = defineProps({ card: { type: Object, required: true } })
const emit = defineEmits(['bookmark'])
const router = useRouter()
const auth = useAuthStore()

const p = computed(() => props.card.posting)
const pct = computed(() => Math.round(props.card.match.overall * 100))
/* 로그인 상태면 "내가 덮은 역량", 아니면 "이 공고가 요구하는 역량".
   덮었다는 건 내 경험과 맞춰 본 결과라 로그아웃 상태에서는 낼 수 없다.
   대신 요구 역량 자체는 공고에 적힌 것이니 그대로 보여 준다 —
   태그 줄을 통째로 비우면 카드가 무엇을 뽑는 자리인지 안 보인다. */
const covered = computed(() => {
  const rows = props.card.match.rows
  const src = auth.signedIn ? rows.filter(r => !r.isGap) : rows
  return [...src].sort((a, b) => b.weight - a.weight)
})

/* 목록에는 **덮은 역량만** 낸다. 보강 필요는 상세의 매칭 상세 분석 탭에서 본다.
   목록은 "어디에 지원할까" 를 고르는 화면이고, "무엇이 부족한가" 는
   공고 하나를 정한 뒤에 볼 것이다. 카드마다 부족을 띄우면 목록 전체가
   경고판이 되어 정작 고르는 일이 방해받는다.

   앞의 3개까지만 내고 나머지는 개수로 접는다 — 다 펼치면 공고마다
   역량 수가 달라(6~10개) 카드 높이가 들쭉날쭉해진다. */
/* 보여준 3개 말고 **덮은 것 중** 남은 수다. 요구 역량 전체에서 빼면 갭까지 세어,
   경험이 하나도 없는 사람이 태그 없이 "+8" 만 보게 된다 — 목록에 안 내기로 한 수치가 배지로 샌다. */
const rest = computed(() => Math.max(0, covered.value.length - 3))

/* 마감이 급한 것만 주황으로 채운다. 전부 채우면 급한 게 하나도 없는 것과 같다. */
/* 마감이 지난 것은 급할 것이 없다 — 음수 D 가 urgent 로 잡혀 주황이 되던 걸 막는다 */
const urgent = computed(() => !props.card.closed && props.card.d <= 7)
</script>

<template>
  <article class="card" role="button" tabindex="0"
           :aria-label="`${p.company} ${p.position} 상세 보기`"
           @click="router.push(`/postings/${p.id}`)"
           @keydown.enter.prevent="router.push(`/postings/${p.id}`)"
           @keydown.space.prevent="router.push(`/postings/${p.id}`)">

    <!-- 윗줄 — 판독값과 즐겨찾기.
         수치는 제목보다 작게 둔다. 카드의 머리는 직무명이지 숫자가 아니다. -->
    <!-- 매칭률과 즐겨찾기는 나에 관한 것이라 로그인해야 나온다.
         상세의 매칭 탭을 막아 놓고 목록에서 그 결과를 보여 주면 앞뒤가 안 맞는다. -->
    <header v-if="auth.signedIn" class="top">
      <div class="read">
        <span class="ml">매칭률 :</span>
        <span class="num pct">{{ pct }}<span class="pc">%</span></span>
      </div>
      <button class="bm" :aria-pressed="card.bookmarked"
              :aria-label="`${p.company} ${p.position} 즐겨찾기`"
              @click.stop="emit('bookmark', p.id)">
        {{ card.bookmarked ? '★ 즐겨찾기됨' : '☆ 즐겨찾기' }}
      </button>
    </header>

    <div class="idt">
      <span class="co">{{ p.company }}</span>
      <h3 class="pos">{{ p.position }}</h3>
    </div>

    <div class="tags">
      <span v-for="r in covered.slice(0, 3)" :key="r.competencyId" class="tag">{{ r.comp.name }}</span>
      <span v-if="rest > 0" class="tag more">+{{ rest }}</span>
    </div>

    <footer class="foot">
      <div class="when">
        <!-- 지난 공고에 D-(-5) 는 셈이 아니라 잡음이다. 날짜만 남긴다. -->
        <b v-if="!card.closed" class="num dd" :class="{ urgent }">D-{{ card.d }}</b>
        <span class="date">{{ deadlineLabel(p.deadline) }} {{ card.closed ? '마감됨' : '마감' }}</span>
      </div>
      <span class="tag" :class="card.essay.state === 'DONE' ? 'tag--ok' : ''">
        자소서 {{ card.essay.label }}<template v-if="card.essay.total"> {{ card.essay.done }}/{{ card.essay.total }}</template>
      </span>
    </footer>
  </article>
</template>

<style scoped>
/* 카드는 흰 면 + 얇은 선.
   호버하면 액센트 테두리가 서고 글자가 액센트로 넘어가며,
   **기업 이름이 커진다.** 커지는 지점이 하나뿐이라 눈이 거기로 간다 —
   여러 군데가 동시에 움직이면 아무 데도 안 보인다. */
.card {
  display: flex; flex-direction: column; gap: 11px;
  padding: 18px 20px 16px;
  background: var(--panel-raised);
  border: 1px solid var(--line);
  border-radius: var(--r);
  text-align: left; font: inherit; color: inherit; cursor: pointer;
  transition: border-color var(--seat-out) linear, background var(--seat-out) linear,
              box-shadow var(--seat-out) linear, transform var(--seat-out) var(--ease);
}
/* 테두리를 두껍게 하는 대신 안쪽 링을 하나 더 그린다 —
   border-width 를 바꾸면 내용이 1px 씩 밀려 글자가 흔들린다. */
.card:hover {
  border-color: var(--accent);
  box-shadow: inset 0 0 0 1px var(--accent);
}
.card:active {
  background: var(--panel-sunken);
  transform: translateY(1px);
  transition-duration: var(--seat-in);
}

.top { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.read { display: flex; align-items: baseline; gap: 7px; }
.pct { font-size: var(--fs-lg); font-weight: 700; line-height: 1; transition: color var(--seat-out) linear; }
.pc { font-size: 0.62em; color: var(--muted); margin-left: 1px; }
/* 무엇을 세는 숫자인지 먼저 말하고 값이 따라온다 — 영문 라벨은 읽는 사람이
   한 번 더 번역해야 한다. */
.ml { font-size: var(--fs-2xs); font-weight: 600; color: var(--muted); }

.bm {
  padding: 5px 12px;
  border: 1px solid var(--line); border-radius: var(--pill);
  background: var(--panel); color: var(--muted);
  font-size: var(--fs-2xs); font-weight: 600; cursor: pointer; white-space: nowrap;
  transition: background var(--release) linear, color var(--release) linear, border-color var(--release) linear;
}
.bm:hover { border-color: var(--ink); color: var(--ink); }
.bm:active, .bm[aria-pressed='true'] {
  background: var(--ink); border-color: var(--ink); color: var(--panel);
  transition-duration: var(--snap);
}

/* 커지는 지점 — 기업명과 직무명을 한 덩어리로 묶어 같이 자란다.
   따로 scale 하면 둘이 미묘하게 어긋나 흔들려 보인다.
   transform-origin 을 왼쪽 위에 두어야 제자리에서 자란다 — 가운데면
   왼쪽으로도 삐져나가 카드 정렬이 흐트러진다. */
.idt {
  display: flex; flex-direction: column; gap: 3px;
  transform-origin: left top;
  transition: transform var(--seat-out) var(--ease);
}
.co {
  font-size: var(--fs-xs); font-weight: 600; color: var(--muted);
  transition: color var(--seat-out) linear;
}
/* 호버 — 액센트로 넘어가고, 이름 덩어리가 커진다 */
.card:hover .idt { transform: scale(1.07); }
.card:hover .co  { color: var(--accent); }
.card:hover .pos { color: var(--accent); }
.card:hover .date, .card:hover .pct, .card:hover .pc { color: var(--accent); }

.pos {
  margin: 0;
  font-size: var(--fs-lg); font-weight: 700;
  letter-spacing: var(--track-tight); line-height: 1.3;
  transition: color var(--seat-out) linear;
}

.tags { display: flex; gap: 5px; flex-wrap: wrap; }
.more { border-style: dashed; color: var(--muted); }

.foot {
  display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;
  margin-top: auto; padding-top: 13px; border-top: 1px solid var(--line-soft);
}
.when { display: flex; align-items: baseline; gap: 9px; min-width: 0; }
.dd { font-size: var(--fs-lg); font-weight: 800; }
.dd.urgent { color: var(--accent); }
.date { font-size: var(--fs-2xs); color: var(--muted); transition: color var(--seat-out) linear; }
</style>
