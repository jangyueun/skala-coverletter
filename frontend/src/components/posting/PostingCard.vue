<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { SCORE } from '@/lib/matching.js'

const props = defineProps({
  card: { type: Object, required: true },   // { posting, match, essay, d, bookmarked, sameCompany }
})
const emit = defineEmits(['bookmark'])
const router = useRouter()

const p = computed(() => props.card.posting)
const pct = computed(() => Math.round(props.card.match.overall * 100))

/* 눈금 7칸. 숫자를 못 읽는 순간에도 형태로 대략을 알린다.
   갭이 있으면 마지막 켜진 칸을 호박색으로 물들여 "여기서 막혔다"를 표시한다. */
const ticks = computed(() => {
  const on = Math.round((pct.value / 100) * 7)
  const hasGap = gaps.value.length > 0
  return Array.from({ length: 7 }, (_, i) => ({
    h: 5 + i * 1.7,
    cls: i < on ? (hasGap && i === on - 1 ? 'gap' : 'on') : '',
  }))
})

const gaps = computed(() => props.card.match.rows.filter(r => r.isGap))
const covered = computed(() =>
  props.card.match.rows.filter(r => !r.isGap).sort((a, b) => b.weight - a.weight))
const rest = computed(() => props.card.match.rows.length - covered.value.slice(0, 2).length - gaps.value.length)

/* 매칭 수치는 액센트(청록)로 둔다 — 계기의 판독값이다.
   빨강이었다면 못 했다. 빨간 숫자는 "나쁨"으로 읽힌다.
   지원 권장 수준일 때만 굵기를 올려 위계를 하나 더 만든다. */
const strong = computed(() => props.card.match.overall >= SCORE.RECOMMEND)
const urgent = computed(() => props.card.d <= 7)
</script>

<template>
  <article
    class="panel panel--press card"
    role="button"
    tabindex="0"
    :aria-label="`${p.company} ${p.position} 상세 보기`"
    @click="router.push(`/postings/${p.id}`)"
    @keydown.enter.prevent="router.push(`/postings/${p.id}`)"
    @keydown.space.prevent="router.push(`/postings/${p.id}`)"
  >
    <div class="head">
      <div class="who">
        <span class="logo" aria-hidden="true">{{ p.company.slice(0, 1) }}</span>
        <div class="min0">
          <div class="co">
            {{ p.company }}
            <span v-if="card.sameCompany > 1" class="tag same">이 회사 {{ card.sameCompany }}건</span>
          </div>
          <h3 class="pos">{{ p.position }}</h3>
        </div>
      </div>

      <div class="read">
        <div class="num num--lg num--read" :class="{ strong }">{{ pct }}<span class="pc">%</span></div>
        <div class="gauge" aria-hidden="true">
          <i v-for="(t, i) in ticks" :key="i" :class="t.cls" :style="{ height: t.h + 'px' }" />
        </div>
        <div class="label rl">Match</div>
      </div>
    </div>

    <div class="tags">
      <span v-for="r in covered.slice(0, 2)" :key="r.competencyId" class="tag">{{ r.comp.name }}</span>
      <span v-for="r in gaps.slice(0, 1)" :key="'g' + r.competencyId" class="tag tag--gap">
        갭 · {{ r.comp.name }}
      </span>
      <span v-if="rest > 0" class="tag more">+{{ rest }}</span>
    </div>

    <div class="foot">
      <div class="when">
        <b class="num dd" :class="{ urgent }">D-{{ card.d }}</b>
        <span class="muted date">{{ p.deadline }}</span>
      </div>
      <div class="acts">
        <span class="tag" :class="{ 'tag--ok': card.essay.state === 'DONE' }">
          {{ card.essay.label }}<template v-if="card.essay.total">&nbsp;{{ card.essay.done }}/{{ card.essay.total }}</template>
        </span>
        <button
          class="btn btn--sm bm"
          :aria-pressed="card.bookmarked"
          :aria-label="`${p.company} ${p.position} 즐겨찾기`"
          @click.stop="emit('bookmark', p.id)"
        >{{ card.bookmarked ? '★' : '☆' }}</button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.card { padding: 15px 17px 14px; display: flex; flex-direction: column; gap: 13px; }
.min0 { min-width: 0; }

.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; }
.who { display: flex; align-items: flex-start; gap: 10px; min-width: 0; }

.logo {
  width: 32px; height: 32px; flex: none;
  display: grid; place-items: center;
  background: var(--line-strong); color: var(--panel);
  font-family: var(--mono); font-weight: 600; font-size: 13px;
  border-radius: var(--r);
}
.co {
  display: flex; align-items: center; gap: 7px; flex-wrap: wrap;
  font-size: 11.5px; color: var(--muted);
}
.same { font-size: 10px; padding: 1px 6px; }
.pos {
  margin: 1px 0 0; font-size: 16px; font-weight: 700;
  letter-spacing: var(--track-tight); line-height: 1.25;
}

.read { text-align: right; flex: none; display: flex; flex-direction: column; align-items: flex-end; gap: 3px; }
.num--lg.strong { font-weight: 700; }
.pc { font-size: 0.55em; margin-left: 1px; opacity: .6; }
.rl { font-size: 9px; }

.tags { display: flex; gap: 5px; flex-wrap: wrap; }
.more { border-style: dashed; color: var(--faint); }

.foot {
  display: flex; justify-content: space-between; align-items: center; gap: 10px;
  padding-top: 11px; border-top: 1px solid var(--line-soft);
}
.when { display: flex; align-items: baseline; gap: 8px; min-width: 0; }
.dd { font-size: 14px; font-weight: 600; }
.dd.urgent { color: var(--gap); }
.date { font-size: 11px; }
.acts { display: flex; align-items: center; gap: 7px; flex: none; }
.bm { padding: 4px 9px; font-size: 13px; line-height: 1; }
</style>
