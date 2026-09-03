<script setup>
import { ref, reactive, computed } from 'vue'
import { useCareerStore } from '@/stores/careerStore.js'
import { computeMatch, SCORE } from '@/lib/matching.js'
import IntakeField from './IntakeField.vue'
import CompetencyPicker from './CompetencyPicker.vue'

const emit = defineEmits(['done'])
const store = useCareerStore()

const FIELDS = [
  { k: 'situation', l: 'S', d: '어떤 상황이었나', rows: 2 },
  { k: 'task',      l: 'T', d: '무엇을 목표로 삼았나', rows: 2 },
  { k: 'action',    l: 'A', d: '내가 한 행동과 적용한 방식', rows: 3 },
  { k: 'result',    l: 'R', d: '결과 (숫자로)', rows: 2 },
]
const AI_FIELDS = ['situation', 'action']   // 후보가 값을 들고 오는 칸
const MINE_FALLBACK = ['task', 'result']

const links = ref(`https://github.com/jhyun/msa-order-service
https://github.com/jhyun/algo-study-2025
https://jhyun.dev/portfolio
https://drive.example/해커톤_발표자료.pdf`)

const state = ref('idle')      // idle | running | done
const step = ref(1)
const chosen = ref(new Set())
const drafts = reactive({})    // { key: {title, period, category, S,T,A,R, comp} }
const active = ref(null)
const armed = ref(null)        // 2단 확인 무장 건수
const linksOpen = ref(true)

const cands = computed(() => store.intakeCandidates)
const candOf = k => cands.value.find(c => c.key === k)

const aiOf = (c, f) => (c[f] || '').trim()
const isAIField = (c, f) => AI_FIELDS.includes(f) && !!aiOf(c, f)

/* 본인이 채워야 하는 칸 — 필드명을 박아 넣지 않고 **AI 가 실제로 되물은 것**에서 파생한다.
   AX-4 가 언젠가 R 을 저장소에서 찾아내 questions 에서 빼면 규칙이 저절로 따라간다. */
function mineOf(c) {
  const asked = [...new Set((c.questions || []).map(q => q.field))]
    .filter(f => FIELDS.some(x => x.k === f) && !isAIField(c, f))
  return asked.length ? asked : MINE_FALLBACK.filter(f => !isAIField(c, f))
}
const requiredText = c => [...new Set([...mineOf(c), 'result'])]

const editedFields = k => {
  const c = candOf(k), d = drafts[k]
  if (!d) return []
  return AI_FIELDS.filter(f => isAIField(c, f) && d[f].trim() !== aiOf(c, f))
}

function seed(k) {
  if (drafts[k]) return
  const c = candOf(k)
  const comp = {}
  ;(c.suggestedCompetencyIds || []).forEach(id => { comp[id] = SCORE.PICK_STRENGTH })
  drafts[k] = {
    title: c.title, period: c.period || '', category: c.category,
    situation: c.situation || '', task: '', action: c.action || '', result: '', comp,
  }
}

function missingOf(k) {
  const c = candOf(k), d = drafts[k], m = []
  if (!d) return [{ f: 'seed', l: '초안 없음' }]
  if (!d.title.trim()) m.push({ f: 'title', l: '제목' })
  requiredText(c).forEach(f => {
    if (!d[f].trim()) m.push({ f, l: FIELDS.find(x => x.k === f).l })
  })
  if (!Object.keys(d.comp).length) m.push({ f: 'comp', l: '역량' })
  return m
}
const isReady = k => missingOf(k).length === 0

async function analyze() {
  const n = links.value.split('\n').map(s => s.trim()).filter(Boolean).length
  if (!n) return
  state.value = 'running'
  await new Promise(r => setTimeout(r, 1600))
  state.value = 'done'
  step.value = 1
  chosen.value = new Set()
  Object.keys(drafts).forEach(k => delete drafts[k])   // 재분석 시 옛 초안이 되살아나지 않게
  active.value = null
  linksOpen.value = false
}

function toggle(k) {
  const next = new Set(chosen.value)
  next.has(k) ? next.delete(k) : next.add(k)
  chosen.value = next
}

function enterStep2() {
  if (!chosen.value.size) return
  ;[...chosen.value].forEach(seed)
  if (!chosen.value.has(active.value)) {
    active.value = [...chosen.value].find(k => !isReady(k)) ?? [...chosen.value][0]
  }
  step.value = 2
}

const readyKeys = computed(() => [...chosen.value].filter(isReady))
const skipped = computed(() => chosen.value.size - readyKeys.value.length)

/* 근거 표시가 편집을 따라 움직인다.
   evidence 에 필드 귀속 정보가 없으므로 **박스 단위로 한 번만** 말한다 —
   없는 인과를 지어내지 않는다. */
const evidenceNote = computed(() => {
  if (!active.value) return ''
  const c = candOf(active.value), ed = editedFields(active.value)
  const kept = AI_FIELDS.filter(f => isAIField(c, f) && !ed.includes(f))
  if (!kept.length) return { tone: 'gap', t: 'AI 가 쓴 문장이 남아 있지 않습니다 — 등록해도 아래 근거는 이 경험에 붙지 않습니다.' }
  if (ed.length) return { tone: '', t: `아래 근거는 ${kept.map(f => FIELDS.find(x => x.k === f).l).join('·')} 에 대한 것입니다. ${ed.map(f => FIELDS.find(x => x.k === f).l).join('·')} 는 본인이 고쳤습니다 — 근거는 AI 원문 기준입니다.` }
  return { tone: '', t: '아래 근거에서 S·A 를 뽑았습니다. 사실과 다르면 그 자리에서 고치세요 — 고치면 표시가 남습니다.' }
})

/* 매칭 %를 실제로 움직이는 것은 STAR 텍스트가 아니라 역량 태그다.
   문장에만 출처 규율을 걸고 점수 입력에 안 거는 비일관을 이 한 줄로 막는다. */
const compNote = computed(() => {
  if (!active.value) return ''
  const sug = new Set(candOf(active.value).suggestedCompetencyIds || [])
  const now = new Set(Object.keys(drafts[active.value].comp).map(Number))
  const added = [...now].filter(id => !sug.has(id)).length
  const gone = [...sug].filter(id => !now.has(id)).length
  const kept = [...now].filter(id => sug.has(id)).length
  return (added || gone)
    ? `AI 제안 ${sug.size}개 중 ${kept}개 유지 · 직접 ${added}개 추가 · ${gone}개 제거`
    : `${sug.size}개 모두 AI 제안 그대로입니다 — 매칭 점수를 움직이는 값이니 한 번 확인하세요.`
})

function buildExp(k) {
  const c = candOf(k), d = drafts[k]
  const edited = editedFields(k)
  const aiKept = AI_FIELDS.filter(f => isAIField(c, f) && !edited.includes(f))
  return {
    title: d.title.trim(), period: d.period.trim() || '기간 미입력', category: d.category,
    situation: d.situation.trim(), task: d.task.trim(),
    action: d.action.trim(), result: d.result.trim(),
    competencyIds: Object.keys(d.comp).map(Number),
    strength: { ...d.comp },
    usedInAnswers: 0,
    source: 'AI_INTAKE',
    editedFields: edited.map(f => FIELDS.find(x => x.k === f).l),
    /* AI 원문이 남은 칸이 하나도 없으면 근거가 따라가지 않는다.
       다시 쓴 문장에 'PR #412' 를 붙이면 근거가 아무 데나 찍히는 도장이 된다. */
    evidenceRefs: aiKept.length ? c.evidence.map(e => `${e.type} · ${e.ref}`) : [],
  }
}

/* 등록하면 매칭이 얼마나 오르는지 미리 보여준다. 전역을 건드리지 않고 계산만. */
const matchDelta = computed(() => {
  if (!readyKeys.value.length) return null
  const p = store.livePostings[0]
  if (!p) return null
  const before = Math.round(computeMatch(p, store.experiences).overall * 100)
  const after = Math.round(computeMatch(p, [
    ...store.experiences,
    ...readyKeys.value.map((k, i) => ({ ...buildExp(k), id: -1 - i })),
  ]).overall * 100)
  return after > before ? { co: p.company, before, after } : null
})

function commit() {
  const ready = readyKeys.value
  if (!ready.length) return
  // 경험 삭제 경로가 없어 등록은 되돌릴 수 없다. 버리는 건이 있으면 한 번 멈춘다.
  if (skipped.value && armed.value !== ready.length) {
    armed.value = ready.length
    return
  }
  ready.forEach(k => store.addExperience(buildExp(k)))
  armed.value = null
  emit('done', ready.length)
}

/* 초안이 한 글자라도 바뀌면 2단 확인 무장이 풀린다 */
const onEdit = () => { armed.value = null }
</script>

<template>
  <div class="wrap">
    <!-- 링크 -->
    <div v-if="linksOpen" class="fld">
      <label class="lb" for="inUrl">링크를 한 줄에 하나씩 — GitHub 저장소 · 포트폴리오 · 발표자료 PDF</label>
      <textarea id="inUrl" v-model="links" class="inp" rows="4" spellcheck="false"></textarea>
    </div>
    <p v-else class="fold">
      링크 <b class="num">{{ links.split('\n').filter(s => s.trim()).length }}</b>개를 분석했습니다
      <button type="button" class="btn btn--sm" @click="linksOpen = true">링크 고치기</button>
    </p>

    <div class="run">
      <button type="button" class="btn btn--primary" :disabled="state === 'running'" @click="analyze">
        {{ state === 'done' ? '다시 분석' : '분석' }}
      </button>
      <span class="tag" :class="state === 'done' ? 'tag--ok' : state === 'running' ? 'tag--gap' : ''">
        {{ state === 'idle' ? '대기' : state === 'running' ? 'PENDING' : 'COMPLETED' }}
      </span>
      <span class="muted note">첨부 PDF·마크다운은 텍스트를 추출해 함께 읽는다</span>
    </div>

    <!-- 1단계 · 후보 선택 -->
    <template v-if="state === 'done' && step === 1">
      <div class="stephd">
        <span class="tag tag--ink">1 / 2 · 후보 선택</span>
        <span class="muted">여러 개를 한 번에 고를 수 있습니다</span>
      </div>
      <p class="lead">
        저장소와 첨부에서 확인된 것만 채웠습니다.
        <b>목표와 수치는 코드에 없어 비워 두고 다음 화면에서 직접 씁니다</b> — 지어내면 면접에서 그대로 무너집니다.
      </p>

      <label v-for="c in cands" :key="c.key" class="cand"
             :class="{ dup: c.duplicateOfExperienceId, on: chosen.has(c.key) }">
        <input type="checkbox" :checked="chosen.has(c.key)" :disabled="!!c.duplicateOfExperienceId"
               @change="toggle(c.key)">
        <div class="min0">
          <div class="ch">
            <b class="ct">{{ c.title }}</b>
            <span v-if="c.duplicateOfExperienceId" class="tag">이미 등록됨</span>
            <span v-if="drafts[c.key]" class="tag tag--ink" title="체크를 풀어도 쓰던 내용은 남아 있습니다">작성 중</span>
            <span class="tag">{{ c.category }}</span>
          </div>
          <p class="cs"><b>S</b> {{ c.situation }}</p>
          <p class="cs"><b>A</b> {{ c.action }}</p>
          <div class="evs">
            <span v-for="(e, i) in c.evidence" :key="i" class="tag mono" :title="e.quote">{{ e.type }} · {{ e.ref }}</span>
          </div>
        </div>
      </label>

      <div class="nav1">
        <span class="muted">{{ chosen.size }}건 선택</span>
        <button type="button" class="btn" :disabled="!chosen.size" @click="enterStep2">
          선택한 {{ chosen.size }}건 편집하기 →
        </button>
      </div>
    </template>

    <!-- 2단계 · 에디터 -->
    <template v-if="state === 'done' && step === 2 && active">
      <div class="stephd">
        <span class="tag tag--ink">2 / 2 · 편집</span>
        <span class="muted">AI 가 쓴 문장도 그 자리에서 고칠 수 있습니다 — 고치면 표시가 남습니다</span>
      </div>

      <div class="two" :class="{ solo: chosen.size === 1 }">
        <!-- 후보 레일 -->
        <aside class="rail">
          <p class="label">후보 {{ chosen.size }} · 등록 가능 {{ readyKeys.length }}</p>
          <button v-for="k in [...chosen]" :key="k" type="button"
                  class="panel panel--press rc" :class="{ sel: k === active }" @click="active = k">
            <b class="rt">{{ drafts[k]?.title || candOf(k).title }}</b>
            <span class="rp">
              <span class="tag" :class="isReady(k) ? 'tag--ok' : 'tag--gap'">
                {{ isReady(k) ? '등록 가능' : missingOf(k).map(m => m.l).join('·') + ' 남음' }}
              </span>
              <span v-if="editedFields(k).length" class="tag tag--ink">
                {{ editedFields(k).map(f => FIELDS.find(x => x.k === f).l).join('·') }} 수정함
              </span>
            </span>
          </button>
          <button type="button" class="btn btn--sm" @click="step = 1">← 후보 다시 고르기</button>
        </aside>

        <!-- 에디터 — DOM 에 언제나 정확히 한 건 -->
        <div class="ed">
          <div class="row2">
            <label class="fld f2"><span class="lb">제목 *</span>
              <input v-model="drafts[active].title" class="inp" @input="onEdit"></label>
            <label class="fld"><span class="lb">기간</span>
              <input v-model="drafts[active].period" class="inp" @input="onEdit"></label>
            <label class="fld"><span class="lb">분류</span>
              <select v-model="drafts[active].category" class="inp">
                <option v-for="c in ['팀 프로젝트','개인 프로젝트','실습 프로젝트','대외활동','인턴·근무','수상·자격']" :key="c">{{ c }}</option>
              </select></label>
          </div>

          <div class="grp">
            <div class="grph">
              <span class="label">STAR</span>
              <span class="gauge" aria-hidden="true">
                <i v-for="f in FIELDS" :key="f.k" :class="{ on: drafts[active][f.k].trim() }" style="height:11px" />
              </span>
              <span class="num cnt">{{ FIELDS.filter(f => drafts[active][f.k].trim()).length }} / 4</span>
              <span class="tag" :class="mineOf(candOf(active)).every(f => drafts[active][f].trim()) ? 'tag--ok' : 'tag--gap'">
                내가 쓴 것 {{ mineOf(candOf(active)).filter(f => drafts[active][f].trim()).length }}/{{ mineOf(candOf(active)).length }}
              </span>
            </div>

            <p class="evnote" :class="evidenceNote.tone">{{ evidenceNote.t }}</p>
            <p v-for="(e, i) in candOf(active).evidence" :key="i" class="evq" :title="e.quote">
              <span class="mono">{{ e.type }} · {{ e.ref }}</span> — “{{ e.quote }}”
            </p>

            <IntakeField
              v-for="f in FIELDS" :key="f.k"
              :field="f.k" :label="f.l" :desc="f.d" :rows="f.rows"
              :draft="drafts[active]"
              :ai-text="candOf(active)[f.k] || ''"
              :mine="mineOf(candOf(active)).includes(f.k)"
              :questions="(candOf(active).questions || []).filter(q => q.field === f.k)"
              @input="onEdit"
            />
          </div>

          <div class="grp">
            <span class="label">이 경험이 증명하는 역량 *</span>
            <CompetencyPicker :pick="drafts[active].comp" />
            <p class="cnote">{{ compNote }}</p>
          </div>
        </div>
      </div>
    </template>

    <!-- 하단 — 상태와 효과가 편집 내내 보인다 -->
    <div v-if="state === 'done' && step === 2" class="foot">
      <p class="fh">
        <template v-if="armed !== null">
          <b class="danger">비어 있는 {{ skipped }}건은 저장되지 않고 사라집니다. 다시 누르면 확정합니다.</b>
        </template>
        <template v-else>
          <span v-if="skipped" class="danger">아직 비어 있는 {{ skipped }}건은 등록되지 않습니다</span>
          <span v-if="skipped && matchDelta"> · </span>
          <span v-if="matchDelta">
            {{ matchDelta.co }} 매칭 {{ matchDelta.before }}% → <b class="up">{{ matchDelta.after }}%</b>
          </span>
          <span v-if="!skipped && !matchDelta">되물은 칸을 채우면 등록 대상이 됩니다.</span>
        </template>
      </p>
      <button type="button" class="btn btn--primary" :disabled="!readyKeys.length" @click="commit">
        {{ armed !== null ? `${readyKeys.length}건만 등록 · 한 번 더`
           : readyKeys.length ? `${readyKeys.length}건 등록` : '등록할 건 없음' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.wrap { display: flex; flex-direction: column; gap: 14px; }
.min0 { min-width: 0; }

.fld { display: flex; flex-direction: column; gap: 5px; }
.lb { font-size: 11.5px; font-weight: 600; color: var(--muted); }
.inp {
  padding: 9px 11px; background: var(--panel); border: 1px solid var(--line);
  border-radius: var(--r-sm); color: var(--ink); font-size: 13px; line-height: 1.6; resize: vertical;
  transition: border-color var(--release) linear, background var(--release) linear;
}
.inp:hover { border-color: var(--line-strong); }
.inp:focus { outline: none; border-color: var(--accent); background: var(--panel-raised); }

.fold { margin: 0; font-size: 12px; color: var(--muted); display: flex; align-items: center; gap: 9px; }
.run { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.note { font-size: 11.5px; margin-left: auto; }

.stephd { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; font-size: 12px; }
.lead { margin: 0; font-size: 12.5px; color: var(--muted); line-height: 1.6; }
.lead b { color: var(--ink); }

/* 후보 카드 */
.cand {
  display: flex; gap: 10px; padding: 12px 14px;
  background: var(--panel-raised); border: 1px solid var(--line);
  border-top: 2px solid var(--line-strong); border-radius: var(--r); cursor: pointer;
  transition: border-color var(--release) linear;
}
.cand:hover { border-color: var(--line-strong); }
.cand.on { border-color: var(--accent); border-top-color: var(--accent); }
.cand.dup { opacity: .5; cursor: not-allowed; }
.cand input { margin-top: 3px; accent-color: var(--accent); flex: none; }
.ch { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; }
.ct { font-size: 13.5px; font-weight: 700; }
.cs { margin: 5px 0 0; font-size: 11.5px; color: var(--muted); line-height: 1.5; }
.cs b { color: var(--accent); font-family: var(--mono); margin-right: 4px; }
.evs { display: flex; gap: 5px; flex-wrap: wrap; margin-top: 8px; }

.nav1 {
  display: flex; align-items: center; gap: 10px;
  padding-top: 12px; border-top: 1px solid var(--line);
}
.nav1 .btn { margin-left: auto; }

/* 2단계 — 좌 레일 + 우 에디터 */
.two { display: grid; grid-template-columns: 172px minmax(0, 1fr); gap: 14px; align-items: start; }
.two.solo { grid-template-columns: 1fr; }
.rail { display: flex; flex-direction: column; gap: 7px; position: sticky; top: 0; }
.two.solo .rail { position: static; flex-direction: row; align-items: center; flex-wrap: wrap; }

.rc { padding: 9px 11px; text-align: left; display: flex; flex-direction: column; gap: 5px; border-top-width: 2px; }
.rc.sel { border-color: var(--accent); border-top-color: var(--accent); }
.rt { font-size: 12px; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rp { display: flex; gap: 4px; flex-wrap: wrap; }
.rp .tag { font-size: 9.5px; padding: 1px 5px; }

.ed { display: flex; flex-direction: column; gap: 13px; min-width: 0; }
.row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.f2 { grid-column: span 2; }

.grp {
  display: flex; flex-direction: column; gap: 11px;
  padding: 14px 15px; border: 1px solid var(--line-soft); border-radius: var(--r); background: var(--panel);
}
.grph { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.cnt { font-size: 12px; font-weight: 600; color: var(--muted); }

.evnote { margin: 0; font-size: 11.5px; color: var(--muted); line-height: 1.55; }
.evnote.gap { color: var(--gap); font-weight: 600; }
.evq {
  margin: 0; font-size: 11px; color: var(--faint); line-height: 1.5;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.cnote { margin: 0; font-size: 11px; color: var(--muted); }

.foot {
  display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  padding-top: 13px; border-top: 1px solid var(--line);
}
.fh { margin: 0; font-size: 11.5px; color: var(--muted); flex: 1 1 240px; line-height: 1.5; }
.danger { color: var(--gap); font-weight: 600; }
.up { color: var(--accent); font-weight: 700; }
.foot .btn { margin-left: auto; }

@media (max-width: 620px) {
  .two { grid-template-columns: 1fr; }
  .rail { position: static; flex-direction: row; overflow-x: auto; }
  .rc { min-width: 152px; flex: none; }
  .row2 { grid-template-columns: 1fr; }
  .f2 { grid-column: span 1; }
}
</style>
