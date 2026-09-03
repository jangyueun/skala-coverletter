<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({ card: { type: Object, required: true } })
const emit = defineEmits(['bookmark'])
const router = useRouter()

const p = computed(() => props.card.posting)
const pct = computed(() => Math.round(props.card.match.overall * 100))
const gaps = computed(() => props.card.match.rows.filter(r => r.isGap))
const covered = computed(() =>
  props.card.match.rows.filter(r => !r.isGap).sort((a, b) => b.weight - a.weight))

/* 태그는 앞의 2개 + 보강 필요 1개까지만 낸다. 나머지는 개수로 접는다 —
   다 펼치면 카드마다 높이가 달라져 그리드가 들쭉날쭉해진다. */
const shown = computed(() => covered.value.slice(0, 2).length + gaps.value.slice(0, 1).length)
const rest = computed(() => props.card.match.rows.length - shown.value)

const ROLE = {
  BACKEND: '백엔드', FRONTEND: '프론트엔드', FULLSTACK: '풀스택',
  PLATFORM: '플랫폼·인프라', AI: 'AI',
}
const roleLabel = computed(() => ROLE[p.value.role] || p.value.role)

/* 마감이 급한 것만 주황으로 채운다. 전부 채우면 급한 게 하나도 없는 것과 같다. */
const urgent = computed(() => props.card.d <= 7)
</script>

<template>
  <article class="card" role="button" tabindex="0"
           :aria-label="`${p.company} ${p.position} 상세 보기`"
           @click="router.push(`/postings/${p.id}`)"
           @keydown.enter.prevent="router.push(`/postings/${p.id}`)"
           @keydown.space.prevent="router.push(`/postings/${p.id}`)">

    <!-- 윗줄 — 판독값과 즐겨찾기 -->
    <header class="top">
      <div class="read">
        <span class="num pct">{{ pct }}<span class="pc">%</span></span>
        <span class="label ml">Match</span>
      </div>
      <button class="bm" :aria-pressed="card.bookmarked"
              :aria-label="`${p.company} ${p.position} 즐겨찾기`"
              @click.stop="emit('bookmark', p.id)">
        {{ card.bookmarked ? '★ 즐겨찾기됨' : '☆ 즐겨찾기' }}
      </button>
    </header>

    <div class="who">
      <span class="co">{{ p.company }}</span>
      <span class="tag role">{{ roleLabel }}</span>
    </div>
    <h3 class="pos">{{ p.position }}</h3>

    <div class="tags">
      <span v-for="r in covered.slice(0, 2)" :key="r.competencyId" class="tag">{{ r.comp.name }}</span>
      <span v-for="r in gaps.slice(0, 1)" :key="'g' + r.competencyId" class="tag tag--gap">
        보강 필요 · {{ r.comp.name }}
      </span>
      <span v-if="rest > 0" class="tag more">+{{ rest }}</span>
    </div>

    <footer class="foot">
      <div class="when">
        <b class="num dd" :class="{ urgent }">D-{{ card.d }}</b>
        <span class="date">{{ p.deadline }} 마감</span>
      </div>
      <span class="tag" :class="card.essay.state === 'DONE' ? 'tag--ok' : ''">
        자소서 {{ card.essay.label }}<template v-if="card.essay.total"> {{ card.essay.done }}/{{ card.essay.total }}</template>
      </span>
    </footer>
  </article>
</template>

<style scoped>
/* 카드는 흰 면 + 얇은 선. 누르면 살짝 가라앉고 테두리가 잉크로 선다. */
.card {
  display: flex; flex-direction: column; gap: 11px;
  padding: 18px 20px 16px;
  background: var(--panel-raised);
  border: 1px solid var(--line);
  border-radius: var(--r);
  text-align: left; font: inherit; color: inherit; cursor: pointer;
  transition: border-color var(--seat-out) linear, background var(--seat-out) linear,
              transform var(--seat-out) var(--ease);
}
.card:hover { border-color: var(--ink); }
.card:active {
  background: var(--panel-sunken); border-color: var(--ink);
  transform: translateY(1px);
  transition-duration: var(--seat-in);
}

.top { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.read { display: flex; align-items: baseline; gap: 7px; }
.pct { font-size: 26px; font-weight: 800; line-height: 1; }
.pc { font-size: 0.5em; color: var(--muted); margin-left: 1px; }
.ml { font-size: 9px; }

.bm {
  padding: 5px 12px;
  border: 1px solid var(--line); border-radius: var(--pill);
  background: var(--panel); color: var(--muted);
  font-size: 11.5px; font-weight: 600; cursor: pointer; white-space: nowrap;
  transition: background var(--release) linear, color var(--release) linear, border-color var(--release) linear;
}
.bm:hover { border-color: var(--ink); color: var(--ink); }
.bm:active, .bm[aria-pressed='true'] {
  background: var(--ink); border-color: var(--ink); color: var(--panel);
  transition-duration: var(--snap);
}

.who { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.co { font-size: 12.5px; font-weight: 600; color: var(--muted); }
.role { font-size: 10.5px; padding: 2px 9px; }

.pos {
  margin: -3px 0 0;
  font-size: 18px; font-weight: 700;
  letter-spacing: var(--track-tight); line-height: 1.3;
}

.tags { display: flex; gap: 5px; flex-wrap: wrap; }
.more { border-style: dashed; color: var(--faint); }

.foot {
  display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;
  margin-top: auto; padding-top: 13px; border-top: 1px solid var(--line-soft);
}
.when { display: flex; align-items: baseline; gap: 9px; min-width: 0; }
.dd { font-size: 17px; font-weight: 800; }
.dd.urgent { color: var(--accent); }
.date { font-size: 11.5px; color: var(--faint); }
</style>
