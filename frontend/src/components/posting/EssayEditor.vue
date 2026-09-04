<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { useAnswersStore } from '@/stores/answers.js'
import { useExperiencesStore } from '@/stores/experiences.js'
import { usePostingsStore } from '@/stores/postings.js'
import { useAiTasksStore } from '@/stores/aiTasks.js'
import { lengthState } from '@/domain/essay.js'
import { categoryLabel, periodLabel } from '@/domain/experience.js'
import { api } from '@/api/index.js'

const props = defineProps({ questions: { type: Array, required: true } })
const A = useAnswersStore()
const E = useExperiencesStore()
const P = usePostingsStore()
const aiTasks = useAiTasksStore()
const route = useRoute()

/* "결과 보기" 로 돌아오면 ?q=문항id 가 붙는다 — 그 문항을 편다. 없으면 첫 문항. */
const wantedId = Number(route.query.q)
const activeId = ref(props.questions.some(x => x.id === wantedId) ? wantedId : props.questions[0]?.id ?? null)
const q = computed(() => props.questions.find(x => x.id === activeId.value) ?? null)
const qNo = x => props.questions.indexOf(x) + 1

/* 문항이 바뀌면(공고를 옮기면) 첫 문항으로 되돌린다.
   안 하면 다른 공고의 문항 id 를 들고 있어 화면이 빈다.

   **버퍼는 건드리지 않는다.** 버퍼는 questionId 를 키로 하므로 A사 내용이
   B사 문항에 흘러갈 수 없고, 여기서 비우면 그게 곧 유실이다. */
watch(() => props.questions, list => {
  if (!list.some(x => x.id === activeId.value)) activeId.value = list[0]?.id ?? null
})

/* AI 작업 "결과 보기" 로 ?q=문항id 를 달고 돌아오면 그 문항을 편다 — 이미 이 화면이 떠 있어도(같은 공고) 반응한다. */
watch(() => route.query.q, id => {
  const n = Number(id)
  if (n && props.questions.some(x => x.id === n)) activeId.value = n
})

/* 화면은 버퍼를 읽고 쓴다. 저장 버튼을 누르기 전까지 커밋본은 안 바뀐다. */
const buf = computed(() => (q.value ? A.draftOf(q.value.id) : { content: '', usedExperienceIds: [] }))
const text = computed({
  get: () => buf.value.content,
  set: v => { if (q.value) A.editDraft(q.value.id, { content: v }) },
})

const len = computed(() => lengthState(text.value, q.value))

const dirty = computed(() => (q.value ? A.isDirty(q.value.id) : false))
const savedAt = computed(() => (q.value ? A.savedAt[q.value.id] : null))
const saving  = computed(() => !!(q.value && A.saving[q.value.id]))
/* 지금 보고 있지 않은 문항 중 저장 안 된 것 — 탭 배지만으로는 놓치기 쉽다 */
const otherDirty = computed(() => A.dirtyIds.filter(id => id !== q.value?.id).length)

/* 이 답변이 근거로 삼은 경험. 본문과 함께 저장되므로 버퍼에 들어간다 —
   옆의 "저장 시 함께 기록됩니다" 가 이걸로 비로소 사실이 된다. */
const used = computed(() => buf.value.usedExperienceIds)
function toggleExp(id) {
  if (!q.value) return
  const cur = used.value
  A.editDraft(q.value.id, {
    usedExperienceIds: cur.includes(id) ? cur.filter(x => x !== id) : [...cur, id],
  })
}

/* AI 초안 — 전역 AI 작업 스토어에 맡긴다(stores/aiTasks.js). 근거가 없으면 서버가 400 을 주므로 버튼을 미리 잠근다.
   초안은 서버에 저장되지 않는다 — 완료되면 스토어가 버퍼에 넣고(onDone), 저장할 때 draftTaskId 로 출처가 남는다.

   기다리는 동안 이 화면을 떠나도(다른 공고·경험 탭으로) 작업은 스토어에서 계속 돈다. 우측 하단 플로팅으로 진행이 보이고,
   완료 뒤 "결과 보기" 를 누르면 이 문항으로 돌아온다(?q=). 진행/실패 표시는 스토어에서 이 문항 것만 읽는다. */
const drafting = computed(() => !!q.value && aiTasks.isDraftRunning(q.value.id))
const draftError = computed(() => {
  const job = q.value && aiTasks.draftJobFor(q.value.id)
  return job && job.status === 'error' ? job.error : null
})
function makeDraft() {
  const target = q.value
  if (!target) return
  const experienceIds = A.draftOf(target.id).usedExperienceIds
  if (!experienceIds.length) return
  const posting = P.byId(target.postingId)
  aiTasks.start({
    kind: 'draft',
    title: `AI 초안 · 문항 ${qNo(target)}`,
    subtitle: posting ? `${posting.company} ${posting.position}` : '',
    view: { type: 'draft', postingId: target.postingId, questionId: target.id },
    run: () => api.ai.draft(target.id, experienceIds),
    // 완료 즉시 버퍼로. 화면을 떠나 있어도 스토어 싱글턴이라 안전하다.
    onDone: ({ draft, taskId }) => A.editDraft(target.id, { content: draft, draftTaskId: taskId ?? null }),
  })
}

/* 되돌리기 버튼은 두지 않는다. 편집 중 되돌리기는 textarea 의 Ctrl+Z 가
   이미 하고, 그쪽이 한 글자 단위라 더 정확하다. 저장 옆에 파괴 버튼을
   두면 잘못 눌러 방금 쓴 걸 통째로 버리는 쪽이 더 자주 일어난다. */
function save() { if (q.value) A.save(q.value.id) }

/* 버퍼는 스토어에 있어 화면을 옮겨도 살아 있다. 정말 사라지는 건
   페이지를 떠날 때뿐이라 경고도 그때만 한다 — 라우터 이동까지 막으면
   사라지지도 않을 것을 사라진다고 말하는 거짓 경고가 된다. */
function guard(e) { if (A.dirtyIds.length) e.preventDefault() }
onMounted(() => window.addEventListener('beforeunload', guard))
onBeforeUnmount(() => window.removeEventListener('beforeunload', guard))
</script>

<template>
  <div v-if="q" class="wrap">
    <!-- 문항 선택 — 기호는 "내용이 있나", 색은 "저장됐나" -->
    <nav class="qtabs" aria-label="문항">
      <button v-for="x in questions" :key="x.id" class="btn btn--sm"
              :aria-pressed="x.id === activeId" @click="activeId = x.id">
        문항 {{ qNo(x) }}
        <span class="st"
              :class="A.isDirty(x.id) ? 'dirty' : A.draftOf(x.id).content.trim() ? 'on' : ''"
              :title="A.isDirty(x.id) ? '저장하지 않은 변경이 있습니다' : ''">
          {{ A.draftOf(x.id).content.trim() ? '●' : '○' }}
        </span>
      </button>
    </nav>

    <div class="two">
      <div class="main">
        <p class="prompt">{{ q.promptText }}</p>

        <!-- 분량 — 막대와 숫자가 같은 판정을 쓴다. 제한이 없는 문항은 글자 수만 센다. -->
        <div class="meterrow">
          <div class="meter" :class="len.tone"><i :style="{ width: len.pct + '%' }" /></div>
          <span class="num cnt" :class="len.tone">{{ len.n }}<template v-if="len.limit"> / {{ len.limit }}</template>자</span>
          <button class="btn btn--sm" :disabled="drafting || !used.length"
                  :title="used.length ? '' : '오른쪽에서 근거로 쓸 경험을 먼저 고르세요'" @click="makeDraft">
            {{ drafting ? '생성 중…' : 'AI 초안' }}
          </button>
        </div>

        <textarea v-model="text" class="inp" rows="12"
                  :aria-label="`문항 ${qNo(q)} 답변`"></textarea>
        <p v-if="draftError" class="derr">AI 초안을 못 받았습니다 — {{ draftError.body?.message || draftError.message }}</p>

        <!-- 저장 — 이 화면의 유일한 주요 행동 -->
        <div class="saverow">
          <p class="sst" :class="{ warn: dirty }">
            <template v-if="dirty">저장하지 않은 변경이 있습니다</template>
            <template v-else-if="savedAt">{{ savedAt }} 저장됨</template>
            <template v-else>아직 저장하지 않았습니다</template>
            <span v-if="otherDirty" class="also"> · 다른 문항 {{ otherDirty }}개도 저장 대기</span>
          </p>

          <button class="btn btn--primary" :disabled="!dirty || saving" @click="save">
            {{ saving ? '저장 중…' : dirty ? '저장' : '저장됨' }}
          </button>
        </div>
      </div>

      <!-- 근거로 쓸 경험 -->
      <aside class="side">
        <p class="subhead">근거로 쓸 경험</p>
        <p class="sd">체크한 경험이 AI 초안의 근거가 되고, 저장 시 함께 기록됩니다.</p>
        <label v-for="e in E.list" :key="e.id" class="ex" :class="{ on: used.includes(e.id) }">
          <input type="checkbox" :checked="used.includes(e.id)" @change="toggleExp(e.id)">
          <span class="min0">
            <b class="et">{{ e.title }}</b>
            <span class="em">{{ categoryLabel(e.category) }} · {{ periodLabel(e.startDate, e.endDate) }}</span>
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
.st { font-size: var(--fs-3xs); color: var(--muted); }
.st.on { color: var(--ok); }
.st.dirty { color: var(--gap); }
/* 선택된 탭에서는 배지가 흰색으로 덮인다. 그래도 되는 이유는 —
   지금 보고 있는 문항의 상태는 바로 아래 저장 줄이 말해 주기 때문이다.
   배지는 "지금 안 보고 있는 문항" 을 위한 것이다. */
.btn[aria-pressed='true'] .st { color: var(--panel); }

.two { display: grid; grid-template-columns: minmax(0, 1fr) 232px; gap: 20px; align-items: start; }
.main { display: flex; flex-direction: column; gap: 11px; min-width: 0; }

.prompt { margin: 0; font-size: var(--fs-md); font-weight: 700; line-height: 1.55; }

.meterrow { display: flex; align-items: center; gap: 10px; }
.meter { flex: 1; height: 5px; background: var(--panel-sunken); border-radius: var(--pill); overflow: hidden; }
.meter i { display: block; height: 100%; background: var(--ink); transition: width 120ms linear; }
.meter.bad i { background: var(--accent); }
.cnt { font-size: var(--fs-xs); font-weight: 600; flex: none; }
.cnt.bad { color: var(--accent); }
.cnt.idle { color: var(--muted); }

.inp {
  padding: 13px 14px; background: var(--panel); color: var(--ink);
  border: 1px solid var(--line); border-radius: var(--r);
  font-size: var(--fs-sm); line-height: 1.85; resize: vertical;
  transition: border-color var(--release) linear;
}
.inp:hover { border-color: var(--ink); }
.inp:focus { outline: none; border-color: var(--accent); }

.saverow { display: flex; align-items: center; gap: 10px; }
.sst { margin: 0; flex: 1; min-width: 0; font-size: var(--fs-xs); color: var(--muted); }
.sst.warn { color: var(--gap); font-weight: 600; }
.also { color: var(--muted); font-weight: 400; }
.derr { margin: 0; font-size: var(--fs-xs); color: var(--gap); font-family: var(--mono); }

.side { display: flex; flex-direction: column; gap: 7px; }
.sd { margin: 0 0 3px; font-size: var(--fs-2xs); color: var(--muted); line-height: 1.5; }
.ex {
  display: flex; gap: 8px; align-items: flex-start;
  padding: 9px 11px; border: 1px solid var(--line); border-radius: var(--r);
  cursor: pointer; transition: border-color var(--release) linear;
}
.ex:hover { border-color: var(--ink); }
.ex.on { border-color: var(--accent); }
.ex input { margin-top: 2px; accent-color: var(--accent); flex: none; }
.et { display: block; font-size: var(--fs-xs); font-weight: 600; line-height: 1.4; }
.em { display: block; font-size: var(--fs-3xs); color: var(--muted); margin-top: 2px; }

@media (max-width: 820px) {
  /* 좁은 폭에서도 문항과 본문이 먼저다. 근거 경험은 아래로 —
     order:-1 로 올리면 뭘 쓰라는 건지 보기 전에 체크박스부터 만난다. */
  .two { grid-template-columns: 1fr; }
  .side { margin-top: 6px; }
}
</style>
