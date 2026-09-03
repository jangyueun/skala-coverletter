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

const ROLE = {
  BACKEND: '백엔드', FRONTEND: '프론트엔드', FULLSTACK: '풀스택',
  PLATFORM: '플랫폼·인프라', AI: 'AI',
}
const roleLabel = computed(() => ROLE[p.value.role] || p.value.role)

/* 마감이 급한 것만 빨강으로 채운다. 전부 채우면 급한 게 하나도 없는 것과 같다. */
const urgent = computed(() => props.card.d <= 7)
</script>

<template>
  <article class="row" role="button" tabindex="0"
           :aria-label="`${p.company} ${p.position} 상세 보기`"
           @click="router.push(`/postings/${p.id}`)"
           @keydown.enter.prevent="router.push(`/postings/${p.id}`)"
           @keydown.space.prevent="router.push(`/postings/${p.id}`)">
    <div class="grid">
      <div class="main">
        <!-- 윗줄 — D-day · 직무 계열 · 기업 -->
        <div class="top">
          <span class="tag" :class="urgent ? 'tag--due' : 'tag--ink'">D-{{ card.d }}</span>
          <span class="tag">{{ roleLabel }}</span>
          <span class="co">
            {{ p.company }}
            <template v-if="card.sameCompany > 1"> · 이 회사 {{ card.sameCompany }}건</template>
          </span>
        </div>

        <h3 class="pos">{{ p.position }}</h3>

        <!-- 아랫줄 — 메타를 파이프로 나눈다. 채용 사이트 리스트의 관례다. -->
        <p class="meta">
          <span v-for="r in covered.slice(0, 3)" :key="r.competencyId">{{ r.comp.name }}</span>
          <span v-if="gaps.length" class="g">보강 필요 {{ gaps.map(g => g.comp.name).join(' · ') }}</span>
        </p>

        <p class="when">
          {{ p.deadline }} 마감
          <span class="dot">·</span>
          <span :class="card.essay.state === 'DONE' ? 'done' : ''">
            자소서 {{ card.essay.label }}<template v-if="card.essay.total"> {{ card.essay.done }}/{{ card.essay.total }}</template>
          </span>
        </p>
      </div>

      <!-- 우측 판독값 — 이 서비스가 채용 사이트와 다른 지점 -->
      <div class="read">
        <div class="num num--lg num--read">{{ pct }}<span class="pc">%</span></div>
        <p class="label">Match</p>
        <button class="bm" :aria-pressed="card.bookmarked"
                :aria-label="`${p.company} ${p.position} 즐겨찾기`"
                @click.stop="emit('bookmark', p.id)">
          {{ card.bookmarked ? '★' : '☆' }}
        </button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.grid { display: flex; justify-content: space-between; align-items: flex-start; gap: 24px; }
.main { min-width: 0; flex: 1; }

.top { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.co { font-size: 12.5px; color: var(--muted); }

.pos {
  margin: 11px 0 0;
  font-size: 21px; font-weight: 700;
  letter-spacing: var(--track-tight); line-height: 1.3;
}

/* 메타를 파이프로 나눈다 — 태그를 더 얹으면 윗줄 pill 과 싸운다 */
.meta { margin: 9px 0 0; font-size: 12.5px; color: var(--muted); display: flex; flex-wrap: wrap; }
.meta span + span::before { content: '|'; margin: 0 8px; color: var(--line); }
.meta .g { color: var(--gap); font-weight: 600; }

.when { margin: 6px 0 0; font-size: 12px; color: var(--faint); }
.dot { margin: 0 6px; }
.when .done { color: var(--ok); }

.read { flex: none; text-align: right; display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.pc { font-size: 0.5em; color: var(--muted); margin-left: 1px; font-weight: 600; }

.bm {
  margin-top: 8px; padding: 4px 10px;
  border: 1px solid var(--line); border-radius: var(--pill);
  background: var(--panel); color: var(--muted); cursor: pointer; font-size: 13px; line-height: 1.2;
  transition: background var(--release) linear, color var(--release) linear, border-color var(--release) linear;
}
.bm:hover { border-color: var(--ink); color: var(--ink); }
.bm:active, .bm[aria-pressed='true'] {
  background: var(--ink); border-color: var(--ink); color: var(--panel); transition-duration: var(--snap);
}

@media (max-width: 560px) {
  .grid { flex-direction: column; gap: 12px; }
  .read { flex-direction: row; align-items: baseline; gap: 10px; text-align: left; }
  .bm { margin-top: 0; margin-left: auto; }
  .pos { font-size: 18px; }
}
</style>
