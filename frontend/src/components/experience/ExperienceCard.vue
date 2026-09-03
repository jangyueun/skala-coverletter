<script setup>
import { computed } from 'vue'
import { usePostingsStore } from '@/stores/postings.js'
import { useDerivedStore } from '@/stores/derived.js'
import { strLabel } from '@/domain/matching.js'

const props = defineProps({ exp: { type: Object, required: true } })
const emit = defineEmits(['edit'])
const P = usePostingsStore()
const D = useDerivedStore()

const STAR = [
  { k: 'situation', l: 'S' },
  { k: 'task',      l: 'T' },
  { k: 'action',    l: 'A' },
  { k: 'result',    l: 'R' },
]

const used = computed(() => D.usedIn(props.exp.id))

/* 인테이크로 만든 경험의 출처 표시.
   AI 문장이 하나도 안 남았으면 근거를 달지 않는다 — 통째로 다시 쓴 문장에
   'PR #412' 를 근거로 붙이면 근거가 아무 데나 찍히는 도장이 된다. */
const origin = computed(() => {
  if (props.exp.source !== 'AI_INTAKE') return null
  const refs = props.exp.evidenceRefs || []
  const edited = props.exp.editedFields || []
  if (!refs.length) return { label: '포폴 인테이크 · 본인이 다시 씀', tone: '', title: 'AI 가 쓴 문장은 남아 있지 않습니다' }
  return {
    label: `포폴 인테이크${edited.length ? ' · 일부 수정' : ''}`,
    tone: 'tag--ink',
    title: refs.join(', ') + (edited.length ? ` · ${edited.join('·')} 문장은 본인이 고쳤습니다` : ''),
  }
})

const comps = computed(() => props.exp.competencyIds.map(id => ({
  c: P.competencies.find(x => x.id === id),
  s: props.exp.strength?.[id] ?? 0.6,
})).filter(x => x.c))
</script>

<template>
  <article class="panel card">
    <div class="hd">
      <div class="min0">
        <h3 class="ttl">{{ exp.title }}</h3>
        <div class="meta">
          <span class="tag">{{ exp.category }}</span>
          <span v-if="origin" class="tag" :class="origin.tone" :title="origin.title">{{ origin.label }}</span>
          <span v-if="used.questions" class="tag tag--ok"
                title="본문이 작성된 답변에 근거로 걸린 문항 수. 한 공고에서 여러 문항에 쓰였으면 그만큼 셉니다.">
            자소서 {{ used.questions }}개 문항에 사용
          </span>
        </div>
      </div>
      <div class="right">
        <span class="mono period">{{ exp.period }}</span>
        <button class="btn btn--sm" @click="emit('edit', exp.id)">수정</button>
      </div>
    </div>

    <dl class="star">
      <template v-for="f in STAR" :key="f.k">
        <dt :class="{ empty: !exp[f.k] }">{{ f.l }}</dt>
        <dd :class="{ empty: !exp[f.k] }">{{ exp[f.k] || '비어 있음' }}</dd>
      </template>
    </dl>

    <div class="chips">
      <span v-for="{ c, s } in comps" :key="c.id" class="tag">
        {{ c.name }}<b class="str">{{ strLabel(s) }}</b>
      </span>
    </div>
  </article>
</template>

<style scoped>
.card { padding: 15px 17px 14px; display: flex; flex-direction: column; gap: 12px; }
.min0 { min-width: 0; }
.hd { display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; }
.ttl { margin: 0; font-size: var(--fs-md); font-weight: 700; letter-spacing: var(--track-tight); line-height: 1.3; }
.meta { display: flex; gap: 5px; flex-wrap: wrap; margin-top: 7px; }
.right { display: flex; align-items: center; gap: 9px; flex: none; }
.period { font-size: var(--fs-2xs); color: var(--muted); }

/* STAR 를 표로 둔다. 어느 칸이 비었는지가 한눈에 보여야 한다. */
.star {
  display: grid; grid-template-columns: 18px 1fr; gap: 5px 10px; margin: 0;
  padding: 11px 0; border-top: 1px solid var(--line-soft); border-bottom: 1px solid var(--line-soft);
}
.star dt {
  font-family: var(--mono); font-size: var(--fs-2xs); font-weight: 600;
  color: var(--accent); line-height: 1.55;
}
.star dd { margin: 0; font-size: var(--fs-xs); line-height: 1.55; color: var(--ink-2); }
.star dt.empty { color: var(--gap); }
.star dd.empty { color: var(--gap); font-style: italic; }

.chips { display: flex; gap: 5px; flex-wrap: wrap; }
.str { margin-left: 5px; color: var(--muted); font-weight: 700; }
</style>
