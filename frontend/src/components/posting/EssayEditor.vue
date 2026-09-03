<script setup>
import { ref, computed, watch } from 'vue'
import { useCareerStore } from '@/stores/careerStore.js'
import { lengthState } from '@/lib/lint.js'

const props = defineProps({ questions: { type: Array, required: true } })
const store = useCareerStore()

const activeId = ref(props.questions[0]?.id ?? null)
const q = computed(() => props.questions.find(x => x.id === activeId.value) ?? null)

/* 문항이 바뀌면(공고를 옮기면) 첫 문항으로 되돌린다.
   안 하면 다른 공고의 문항 id 를 들고 있어 화면이 빈다. */
watch(() => props.questions, list => {
  if (!list.some(x => x.id === activeId.value)) activeId.value = list[0]?.id ?? null
})

const text = computed({
  get: () => q.value?.draft ?? '',
  set: v => { if (q.value) q.value.draft = v },
})

const len = computed(() => lengthState(text.value, q.value))

/* 이 답변이 근거로 삼은 경험. 체크한 id 가 usedExperienceIds 로 남아
   "AI 가 무엇을 보고 썼는지" 를 DB 가 기억한다. */
const used = computed(() => q.value?.usedExperienceIds ?? [])
function toggleExp(id) {
  if (!q.value) return
  const cur = q.value.usedExperienceIds ?? []
  q.value.usedExperienceIds = cur.includes(id) ? cur.filter(x => x !== id) : [...cur, id]
}

const drafting = ref(false)
async function makeDraft() {
  if (!q.value) return
  drafting.value = true
  await new Promise(r => setTimeout(r, 1400))       // 202 + 폴링을 흉내낸다
  text.value = q.value.aiDraft || text.value
  drafting.value = false
}
</script>

<template>
  <div v-if="q" class="wrap">
    <!-- 문항 선택 -->
    <nav class="qtabs" aria-label="문항">
      <button v-for="x in questions" :key="x.id" class="btn btn--sm"
              :aria-pressed="x.id === activeId" @click="activeId = x.id">
        문항 {{ questions.indexOf(x) + 1 }}
        <span class="st" :class="(x.draft || '').trim() ? 'on' : ''">
          {{ (x.draft || '').trim() ? '●' : '○' }}
        </span>
      </button>
    </nav>

    <div class="two">
      <div class="main">
        <p class="prompt">{{ q.prompt }}</p>

        <!-- 분량 — 막대와 숫자가 같은 판정을 쓴다 -->
        <div class="meterrow">
          <div class="meter" :class="len.tone"><i :style="{ width: len.pct + '%' }" /></div>
          <span class="num cnt" :class="len.tone">{{ len.n }} / {{ len.limit }}자</span>
          <button class="btn btn--sm" :disabled="drafting" @click="makeDraft">
            {{ drafting ? '생성 중…' : 'AI 초안' }}
          </button>
        </div>

        <textarea v-model="text" class="inp" rows="12"
                  :placeholder="`요구 ${q.charLimit}자의 80% 이상 채우세요`"></textarea>

      </div>

      <!-- 근거로 쓸 경험 -->
      <aside class="side">
        <p class="label">근거로 쓸 경험</p>
        <p class="sd">체크한 경험이 AI 초안의 근거가 되고, 저장 시 함께 기록됩니다.</p>
        <label v-for="e in store.experiences" :key="e.id" class="ex" :class="{ on: used.includes(e.id) }">
          <input type="checkbox" :checked="used.includes(e.id)" @change="toggleExp(e.id)">
          <span class="min0">
            <b class="et">{{ e.title }}</b>
            <span class="em">{{ e.category }} · {{ e.period }}</span>
          </span>
        </label>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.wrap { display: flex; flex-direction: column; gap: 14px; }
.min0 { min-width: 0; }

.qtabs { display: flex; gap: 7px; flex-wrap: wrap; }
.st { font-size: 9px; color: var(--faint); }
.st.on { color: var(--ok); }
.btn[aria-pressed='true'] .st { color: var(--panel); }

.two { display: grid; grid-template-columns: minmax(0, 1fr) 232px; gap: 20px; align-items: start; }
.main { display: flex; flex-direction: column; gap: 11px; min-width: 0; }

.prompt { margin: 0; font-size: 15px; font-weight: 700; line-height: 1.55; }

.meterrow { display: flex; align-items: center; gap: 10px; }
.meter { flex: 1; height: 5px; background: var(--panel-sunken); border-radius: var(--pill); overflow: hidden; }
.meter i { display: block; height: 100%; background: var(--ink); transition: width 120ms linear; }
.meter.bad i { background: var(--accent); }
.cnt { font-size: 12px; font-weight: 600; flex: none; }
.cnt.bad { color: var(--accent); }
.cnt.idle { color: var(--faint); }

.inp {
  padding: 13px 14px; background: var(--panel); color: var(--ink);
  border: 1px solid var(--line); border-radius: var(--r);
  font-size: 13.5px; line-height: 1.85; resize: vertical;
  transition: border-color var(--release) linear;
}
.inp:hover { border-color: var(--ink); }
.inp:focus { outline: none; border-color: var(--accent); }

.side { display: flex; flex-direction: column; gap: 7px; }
.sd { margin: 0 0 3px; font-size: 11px; color: var(--muted); line-height: 1.5; }
.ex {
  display: flex; gap: 8px; align-items: flex-start;
  padding: 9px 11px; border: 1px solid var(--line); border-radius: var(--r);
  cursor: pointer; transition: border-color var(--release) linear;
}
.ex:hover { border-color: var(--ink); }
.ex.on { border-color: var(--accent); }
.ex input { margin-top: 2px; accent-color: var(--accent); flex: none; }
.et { display: block; font-size: 12px; font-weight: 600; line-height: 1.4; }
.em { display: block; font-size: 10.5px; color: var(--faint); margin-top: 2px; }

@media (max-width: 820px) {
  /* 좁은 폭에서도 문항과 본문이 먼저다. 근거 경험은 아래로 —
     order:-1 로 올리면 뭘 쓰라는 건지 보기 전에 체크박스부터 만난다. */
  .two { grid-template-columns: 1fr; }
  .side { margin-top: 6px; }
}
</style>
