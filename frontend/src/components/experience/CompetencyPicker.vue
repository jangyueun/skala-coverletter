<script setup>
import { computed } from 'vue'
import { useCareerStore } from '@/stores/careerStore.js'
import { STR, strLabel, SCORE } from '@/lib/matching.js'

/* 수동 폼과 인테이크 에디터가 같이 쓴다.
   두 곳에 복사해 두면 한쪽만 고쳐진다.

   **부모가 소유한 반응형 객체를 받아 제자리에서 고친다.**
   v-model 로 매번 새 객체를 emit 하면, 같은 틱에 두 번 클릭했을 때
   두 번째가 아직 갱신되지 않은 props 를 펼쳐서 첫 선택을 덮어쓴다.
   역량을 빠르게 두 개 고르면 하나가 사라지는 실제 버그였다. */
const props = defineProps({
  pick: { type: Object, required: true },   // { competencyId: strength } — 반응형, 제자리 수정
})
const store = useCareerStore()

const picked = computed(() =>
  Object.entries(props.pick)
    .map(([id, s]) => ({ c: store.competencies.find(x => x.id === +id), s, id: +id }))
    .filter(x => x.c))

const pool = computed(() => store.competencies.filter(c => !(c.id in props.pick)))

const add    = id => { props.pick[id] = SCORE.PICK_STRENGTH }
const remove = id => { delete props.pick[id] }

/* 내부값은 연속이지만 사람에게는 3단계로만 보여준다.
   어떤 값이 들어와도 약→중→강 으로 스냅된다. */
const cycle = id => {
  const cur = STR.findIndex(s => s.lab === strLabel(props.pick[id]))
  props.pick[id] = STR[(cur + 1) % STR.length].v
}
</script>

<template>
  <div>
    <div class="picked">
      <span v-for="p in picked" :key="p.id" class="chip">
        {{ p.c.name }}
        <button type="button" class="cyc" :title="`약 / 중 / 강 전환 · 내부값 ${p.s}`" @click="cycle(p.id)">
          {{ strLabel(p.s) }}
        </button>
        <button type="button" class="rm" :aria-label="`${p.c.name} 제거`" @click="remove(p.id)">×</button>
      </span>
      <span v-if="!picked.length" class="none">아래에서 역량을 고르세요 · 최소 1개</span>
    </div>

    <div class="pool">
      <button v-for="c in pool" :key="c.id" type="button" class="tag add" @click="add(c.id)">
        {{ c.name }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.picked { display: flex; gap: 6px; flex-wrap: wrap; min-height: 26px; }
.none { font-size: 12px; color: var(--muted); }

.chip {
  display: inline-flex; align-items: stretch;
  border: 1px solid var(--line-strong); border-radius: var(--r-sm);
  background: var(--panel-raised); font-size: 11.5px; font-weight: 600;
  overflow: hidden;
}
.chip { padding-left: 8px; align-items: center; }

.cyc, .rm {
  border: none; background: transparent; cursor: pointer;
  font-family: var(--mono); font-weight: 700; font-size: 11px;
  padding: 3px 7px; color: var(--accent);
  transition: background var(--release) linear, color var(--release) linear;
}
.cyc { border-left: 1px solid var(--line); margin-left: 7px; }
.cyc:hover { background: var(--panel-sunken); }
.cyc:active { background: var(--accent); color: var(--accent-ink); transition-duration: var(--snap); }

.rm { color: var(--muted); font-size: 13px; padding: 3px 8px 4px; }
.rm:hover { color: var(--gap); }
.rm:active { background: var(--gap); color: var(--panel-raised); transition-duration: var(--snap); }

.pool {
  display: flex; gap: 5px; flex-wrap: wrap;
  margin-top: 11px; padding-top: 11px; border-top: 1px dashed var(--line);
}
.add { cursor: pointer; font: inherit; font-size: 11px; font-weight: 600; }
.add:hover { border-color: var(--line-strong); color: var(--ink); }
.add:active { background: var(--accent); border-color: var(--accent); color: var(--accent-ink); transition-duration: var(--snap); }
</style>
