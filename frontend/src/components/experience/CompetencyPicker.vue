<script setup>
import { computed } from 'vue'
import { useCareerStore } from '@/stores/careerStore.js'
import { STR, strLabel, SCORE, groupByCategory } from '@/lib/matching.js'

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

/* 사전이 45개다. 평평하게 늘어놓으면 "이게 기술인지 인재상인지" 를
   이름만 보고 판단해야 한다. 범주로 묶으면 고를 자리를 먼저 찾고 그 안에서 고른다.
   이미 고른 것은 빠지므로, 한 범주를 다 고르면 그 줄 자체가 사라진다. */
const pool = computed(() =>
  groupByCategory(store.competencies.filter(c => !(c.id in props.pick))))

const add    = id => { props.pick[id] = SCORE.PICK_STRENGTH }
const remove = id => { delete props.pick[id] }

/* 내부값은 연속이지만 사람에게는 약·중·강 3단계로만 보여준다.
   어떤 값이 들어와도 셋 중 하나로 스냅된다.

   순환 버튼이 아니라 셋을 다 펼쳐 둔다. 순환은 선택지가 안 보이고,
   원하는 값을 지나치면 한 바퀴를 더 돌아야 한다 — 세 개짜리에서는
   그냥 원하는 걸 직접 누르는 게 언제나 한 번이다.

   등급 라벨에는 색을 쓰지 않는다. 액센트로 칠하면 "약" 이 강조돼 보여서
   값의 크기와 시각적 무게가 반대로 간다. 색은 조작과 판독값의 몫이다. */
const setStr = (id, v) => { props.pick[id] = v }
</script>

<template>
  <div>
    <div class="picked">
      <span v-for="p in picked" :key="p.id" class="chip">
        {{ p.c.name }}
        <span class="str" role="group" :aria-label="`${p.c.name} 강도`">
          <button v-for="s in STR" :key="s.lab" type="button" class="sv"
                  :aria-pressed="strLabel(p.s) === s.lab"
                  :title="`${p.c.name} · ${s.lab}`"
                  @click="setStr(p.id, s.v)">{{ s.lab }}</button>
        </span>
        <button type="button" class="rm" :aria-label="`${p.c.name} 제거`" @click="remove(p.id)">×</button>
      </span>
      <span v-if="!picked.length" class="none">아래에서 역량을 고르세요 · 최소 1개</span>
    </div>

    <div class="pool">
      <div v-for="g in pool" :key="g.k" class="grp">
        <p class="label gl">{{ g.label }}</p>
        <div class="tags">
          <button v-for="c in g.items" :key="c.id" type="button" class="tag add" @click="add(c.id)">
            {{ c.name }}
          </button>
        </div>
      </div>
      <p v-if="!pool.length" class="none">사전의 역량을 모두 골랐습니다.</p>
    </div>
  </div>
</template>

<style scoped>
.picked { display: flex; gap: 6px; flex-wrap: wrap; min-height: 26px; }
.none { font-size: var(--fs-xs); color: var(--muted); }

.chip {
  display: inline-flex; align-items: stretch;
  border: 1px solid var(--line-strong); border-radius: var(--r-sm);
  background: var(--panel-raised); font-size: var(--fs-2xs); font-weight: 600;
  overflow: hidden;
}
.chip { padding-left: 8px; align-items: center; }

.sv, .rm {
  border: none; background: transparent; cursor: pointer;
  font-family: var(--mono); font-weight: 700; font-size: var(--fs-2xs);
  padding: 3px 6px; color: var(--faint);
  transition: background var(--release) linear, color var(--release) linear;
}
.str { display: inline-flex; border-left: 1px solid var(--line); margin-left: 7px; }
.sv:hover { background: var(--panel-sunken); color: var(--ink); }
/* 고른 값은 눌린 채로 머문다 — 버튼 계열이 쓰는 것과 같은 반전이다 */
.sv[aria-pressed='true'] { background: var(--ink); color: var(--panel-raised); }
.sv:active { background: var(--accent); color: var(--accent-ink); transition-duration: var(--snap); }

.rm { color: var(--muted); font-size: var(--fs-sm); padding: 3px 8px 4px; }
.rm:hover { color: var(--gap); }
.rm:active { background: var(--gap); color: var(--panel-raised); transition-duration: var(--snap); }

/* 범주 사이 간격은 태그가 줄바꿈되는 간격(5px)보다 확실히 커야 한다.
   비슷하면 묶음이 안 보이고 그냥 45개짜리 긴 목록으로 읽힌다. */
.pool {
  display: flex; flex-direction: column; gap: 16px;
  margin-top: 11px; padding-top: 12px; border-top: 1px dashed var(--line);
}

/* 범주 이름은 왼쪽 홈통에 고정한다. 위에 얹으면 줄 수가 두 배가 되고,
   사전 45개가 다이얼로그를 넘겨 버린다. */
.grp { display: grid; grid-template-columns: 78px minmax(0, 1fr); gap: 10px; align-items: baseline; }
.gl { margin: 0; text-align: right; white-space: nowrap; }
.tags { display: flex; gap: 5px; flex-wrap: wrap; }

@media (max-width: 560px) {
  /* 좁아지면 홈통을 접는다 — 78px 을 떼 주지 않으면 태그가 두 글자씩 끊긴다 */
  .grp { grid-template-columns: 1fr; gap: 4px; }
  .gl { text-align: left; }
}
.add { cursor: pointer; font: inherit; font-size: var(--fs-2xs); font-weight: 600; }
.add:hover { border-color: var(--line-strong); color: var(--ink); }
.add:active { background: var(--accent); border-color: var(--accent); color: var(--accent-ink); transition-duration: var(--snap); }
</style>
