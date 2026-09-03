<script setup>
import { computed } from 'vue'

/* STAR 한 칸. "이 문장을 누가 썼는가" 를 세 겹으로 말한다.
   (네 번째 겹은 등록 후 경험 카드의 배지다) */
const props = defineProps({
  field:  { type: String, required: true },       // situation | task | action | result
  label:  { type: String, required: true },       // S · T · A · R
  desc:   { type: String, required: true },
  draft:  { type: Object, required: true },       // 부모가 소유한 반응형 초안
  aiText: { type: String, default: '' },          // AI 원문 (없으면 본인 몫 칸)
  mine:   { type: Boolean, default: false },      // AI 가 되물은 칸인가
  questions: { type: Array, default: () => [] },
  rows:   { type: Number, default: 2 },
})

const value = computed({
  get: () => props.draft[props.field],
  set: v => { props.draft[props.field] = v },     // 제자리 수정 — stale 이 생기지 않는다
})

const isAI = computed(() => !!props.aiText)
const empty = computed(() => !value.value.trim())

/* "고쳤다" 를 플래그로 저장하지 않고 매번 원문과 비교한다.
   고쳤다가 원문 그대로 되돌리면 표시도 저절로 원상복구되고,
   편집해 놓고 AI 표식만 남기는 세탁이 불가능하다. */
const edited = computed(() => isAI.value && value.value.trim() !== props.aiText.trim())

/* ① 스트라이프 ② 배지 — 하나가 지워져도 나머지가 남도록 중복해서 건다 */
const stripe = computed(() => ({
  'f--you':    !isAI.value,
  'f--todo':   props.mine && empty.value,
  'f--edited': edited.value,
}))

const badge = computed(() => {
  if (isAI.value && !edited.value) return { t: 'AI · 근거 확인됨', cls: '' }
  if (isAI.value) return { t: empty.value ? 'AI 문장 지움' : 'AI 문장 → 내가 고침', cls: 'b--edit' }
  if (empty.value) return { t: '저장소에 없음 · 본인만 아는 것', cls: 'tag--gap' }
  return { t: '내가 씀', cls: 'tag--ok' }
})

/* 회피 입력 소프트 경고. 등록을 막지는 않는다 — R 숫자 검사와 같은 강도. */
const tooShort = computed(() => props.mine && value.value.trim().length > 0 && value.value.trim().length < 6)

const revert = () => { value.value = props.aiText }
</script>

<template>
  <div class="f" :class="stripe">
    <div class="hd">
      <label :for="`in-${field}`" class="lb"><b class="fl">{{ label }}</b> {{ desc }}</label>
      <span class="tag bd" :class="badge.cls">{{ badge.t }}</span>
      <button v-if="edited" type="button" class="btn btn--sm rv" @click="revert">AI 원문으로</button>
    </div>

    <!-- ③ 붙는 보조정보의 종류가 다르다.
         AI 칸에는 근거(박스 단위), 본인 칸에는 질문과 "왜 묻는가".
         .why 가 없다는 것 자체가 "이 문장은 아무 데서도 안 나왔습니다" 신호다. -->
    <div v-if="mine" class="q">
      <template v-if="questions.length">
        <template v-for="(qq, i) in questions" :key="i">
          <p class="qt">{{ qq.q }}</p>
          <p class="qw">왜 묻는가 — {{ qq.why }}</p>
        </template>
      </template>
      <template v-else>
        <p class="qt">이 칸은 저장소에서 확인되지 않습니다.</p>
        <p class="qw">왜 묻는가 — AI 는 없는 것을 지어내지 않습니다. 직접 채워 주세요.</p>
      </template>
    </div>

    <textarea
      :id="`in-${field}`" v-model="value" class="inp" :rows="rows"
      :placeholder="field === 'result' ? '숫자를 포함해 써 주세요' : mine ? '한두 문장이면 충분합니다' : ''"
    ></textarea>

    <p v-if="tooShort" class="warn">너무 짧습니다 — 한 문장으로 써야 자소서에서 쓸 수 있습니다.</p>
    <slot name="after" />
  </div>
</template>

<style scoped>
/* ① 왼쪽 스트라이프 — 회색은 AI 원문, 액센트는 내가 고침, 호박 5px 는 아직 안 씀 */
.f {
  display: flex; flex-direction: column; gap: 6px;
  border-left: 3px solid var(--line); padding-left: 11px;
  transition: border-color var(--release) linear, border-width var(--release) linear;
}
.f--you    { border-left-color: var(--gap); }
.f--todo   { border-left-width: 5px; }
.f--edited { border-left-color: var(--accent); }

.hd { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; }
.lb { font-size: 11.5px; font-weight: 600; color: var(--muted); }
.fl { color: var(--accent); font-family: var(--mono); margin-right: 3px; }
.bd { font-size: 10px; }
.b--edit { border-color: var(--accent); color: var(--accent); }
.rv { margin-left: auto; padding: 3px 8px; font-size: 10.5px; }

.q { display: flex; flex-direction: column; gap: 3px; }
.qt { margin: 0; font-size: 12.5px; font-weight: 600; line-height: 1.5; }
.qw { margin: 0; font-size: 11px; color: var(--faint); line-height: 1.5; }

.inp {
  padding: 9px 11px; background: var(--panel);
  border: 1px solid var(--line); border-radius: var(--r-sm);
  color: var(--ink); font-size: 13px; line-height: 1.6; resize: vertical;
  transition: border-color var(--release) linear, background var(--release) linear;
}
.inp:hover { border-color: var(--line-strong); }
.inp:focus { outline: none; border-color: var(--accent); background: var(--panel-raised); }

.warn { margin: 0; font-size: 11px; color: var(--gap); }
</style>
