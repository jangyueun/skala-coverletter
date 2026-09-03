<script setup>
import { ref, computed, reactive } from 'vue'
import { useCareerStore } from '@/stores/careerStore.js'
import CompetencyPicker from './CompetencyPicker.vue'
import IntakePanel from './IntakePanel.vue'

const store = useCareerStore()
const el = ref(null)
const editId = ref(null)
const tab = ref('manual')   // manual | intake

const CATS = ['팀 프로젝트', '개인 프로젝트', '실습 프로젝트', '대외활동', '인턴·근무', '수상·자격']
const FIELDS = [
  { k: 'situation', l: 'S', d: '어떤 상황이었나', rows: 2 },
  { k: 'task',      l: 'T', d: '무엇을 목표로 삼았나', rows: 2 },
  { k: 'action',    l: 'A', d: '내가 한 행동과 적용한 방식', rows: 3 },
  { k: 'result',    l: 'R', d: '결과 (숫자로)', rows: 2 },
]

const blank = () => ({
  title: '', period: '', category: CATS[0],
  situation: '', task: '', action: '', result: '', comp: {},
})
const form = reactive(blank())
const errors = ref([])

function open(id) {
  editId.value = id ?? null
  Object.assign(form, blank())
  if (id != null) {
    const e = store.experienceById(id)
    if (e) {
      Object.assign(form, {
        title: e.title, period: e.period, category: e.category,
        situation: e.situation, task: e.task, action: e.action, result: e.result,
        comp: { ...(e.strength || {}) },
      })
      e.competencyIds.forEach(cid => { if (!(cid in form.comp)) form.comp[cid] = 0.7 })
    }
  }
  errors.value = []
  tab.value = 'manual'
  el.value.showModal()
}
defineExpose({ open })

const starDone = computed(() => FIELDS.filter(f => form[f.k].trim()).length)

/* 지금 비어 있는 요구 역량 — 이 경험이 그걸 증명한다면 태그하라고 알린다 */
const openGaps = computed(() => {
  const s = new Set()
  store.livePostings.forEach(p =>
    store.matchFor(p).rows.filter(r => r.isGap).forEach(r => s.add(r.comp.name)))
  return [...s]
})

function save() {
  const errs = []
  if (!form.title.trim()) errs.push('제목은 필수입니다.')
  if (!form.result.trim()) errs.push('결과(R)는 필수입니다. 성과 없는 경험은 자소서에서 쓸 수 없습니다.')
  if (!Object.keys(form.comp).length) errs.push('역량을 최소 하나 태그해야 매칭에 쓰입니다.')
  errors.value = errs
  if (errs.length) return

  const patch = {
    title: form.title.trim(),
    period: form.period.trim() || '기간 미입력',
    category: form.category,
    situation: form.situation.trim(), task: form.task.trim(),
    action: form.action.trim(), result: form.result.trim(),
    competencyIds: Object.keys(form.comp).map(Number),
    strength: { ...form.comp },
  }
  if (editId.value != null) store.updateExperience(editId.value, patch)
  else store.addExperience({ ...patch, usedInAnswers: 0 })
  el.value.close()
}
</script>

<template>
  <dialog ref="el" class="dlg" @cancel="editId = null">
    <form method="dialog" class="inner" @submit.prevent>
      <header class="hd">
        <div>
          <p class="label">{{ editId != null ? 'Edit' : 'New' }}</p>
          <h2 class="h">{{ editId != null ? '경험 수정' : '경험 등록' }}</h2>
        </div>
        <button type="button" class="btn btn--sm" @click="el.close()">닫기</button>
      </header>

      <!-- 수정 중에는 인테이크 탭이 아예 없다. 이미 있는 경험을
           포폴에서 다시 가져올 일이 없고, 있으면 덮어쓸 위험만 생긴다. -->
      <nav v-if="editId == null" class="tabs">
        <button type="button" class="btn btn--sm" :aria-pressed="tab === 'manual'" @click="tab = 'manual'">직접 입력</button>
        <button type="button" class="btn btn--sm" :aria-pressed="tab === 'intake'" @click="tab = 'intake'">포폴에서 가져오기</button>
        <span class="tag ax">AX-4</span>
      </nav>

      <div v-show="tab === 'manual'" class="body">
        <div class="row2">
          <label class="fld f2">
            <span class="lb">제목 *</span>
            <input v-model="form.title" class="inp" placeholder="MSA 주문·결제 서비스 구축">
          </label>
          <label class="fld">
            <span class="lb">기간</span>
            <input v-model="form.period" class="inp" placeholder="2026.08">
          </label>
          <label class="fld">
            <span class="lb">분류</span>
            <select v-model="form.category" class="inp">
              <option v-for="c in CATS" :key="c">{{ c }}</option>
            </select>
          </label>
        </div>

        <div class="grp">
          <div class="grph">
            <span class="subhead">STAR</span>
            <span class="gauge" aria-hidden="true">
              <i v-for="f in FIELDS" :key="f.k" :class="{ on: form[f.k].trim() }" style="height:11px" />
            </span>
            <span class="num cnt">{{ starDone }} / 4</span>
          </div>

          <label v-for="f in FIELDS" :key="f.k" class="fld">
            <span class="lb"><b class="fl">{{ f.l }}</b> {{ f.d }}</span>
            <textarea v-model="form[f.k]" class="inp" :rows="f.rows"></textarea>
          </label>
        </div>

        <div class="grp">
          <span class="subhead">이 경험이 증명하는 역량 *</span>
          <CompetencyPicker :pick="form.comp" class="pick" />
        </div>

        <div v-if="errors.length" class="errs">
          <p v-for="(e, i) in errors" :key="i" class="err">⚠ {{ e }}</p>
        </div>
      </div>

      <footer v-show="tab === 'manual'" class="ft">
        <p class="gaph">
          <template v-if="openGaps.length">
            지금 비어 있는 요구 역량 — <b>{{ openGaps.join(', ') }}</b>.
            이 경험이 그걸 증명한다면 꼭 태그하세요.
          </template>
          <template v-else>요구 역량이 모두 덮여 있습니다.</template>
        </p>
        <div class="acts">
          <button type="button" class="btn btn--sm" @click="el.close()">취소</button>
          <button type="button" class="btn btn--primary" @click="save()">
            {{ editId != null ? '저장' : '등록' }}
          </button>
        </div>
      </footer>

      <div v-show="tab === 'intake'" class="body">
        <IntakePanel @done="n => { el.close(); }" />
      </div>
    </form>
  </dialog>
</template>

<style scoped>
/* 폭은 역량 사전이 정한다. 45개를 5개 범주로 묶어 늘어놓는 자리라
   좁으면 태그가 두세 개씩 끊겨 범주 하나가 네 줄이 된다.
   탭(직접 입력/포폴에서 가져오기)마다 폭을 달리하면 전환할 때 화면이 튀므로
   넓은 쪽에 맞춰 하나로 둔다. */
.dlg {
  padding: 0; border: none; background: transparent;
  max-width: 1000px; width: calc(100% - 32px);
}
.dlg::backdrop { background: rgba(10, 12, 11, 0.55); backdrop-filter: blur(2px); }
.inner {
  background: var(--panel-raised);
  border: 1px solid var(--line); border-top: 4px solid var(--line-strong);
  border-radius: var(--r);
  display: flex; flex-direction: column; max-height: min(88vh, 820px);
}

.hd {
  display: flex; justify-content: space-between; align-items: flex-start; gap: 14px;
  padding: 16px 20px 14px; border-bottom: 1px solid var(--line);
}
.h { margin: 3px 0 0; font-size: var(--fs-xl); font-weight: 800; letter-spacing: var(--track-display); }

.body { padding: 18px 20px; overflow-y: auto; display: flex; flex-direction: column; gap: 18px; }

.tabs {
  display: flex; align-items: center; gap: 7px;
  padding: 11px 20px; border-bottom: 1px solid var(--line); background: var(--panel);
}
.ax { margin-left: auto; font-size: var(--fs-3xs); }

.row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 11px; }
.f2 { grid-column: span 2; }

.fld { display: flex; flex-direction: column; gap: 5px; }
.lb { font-size: var(--fs-2xs); font-weight: 600; color: var(--muted); }
.fl { color: var(--accent); font-family: var(--mono); margin-right: 3px; }

.inp {
  padding: 9px 11px;
  background: var(--panel);
  border: 1px solid var(--line); border-radius: var(--r-sm);
  color: var(--ink); font-size: var(--fs-sm); line-height: 1.6;
  resize: vertical;
  transition: border-color var(--release) linear, background var(--release) linear;
}
.inp:hover { border-color: var(--line-strong); }
.inp:focus { outline: none; border-color: var(--accent); background: var(--panel-raised); }

.grp {
  display: flex; flex-direction: column; gap: 11px;
  padding: 14px 15px; border: 1px solid var(--line-soft); border-radius: var(--r);
  background: var(--panel);
}
.grph { display: flex; align-items: center; gap: 9px; }
.cnt { font-size: var(--fs-xs); font-weight: 600; color: var(--muted); margin-left: auto; }
.pick { margin-top: 2px; }


.errs { display: flex; flex-direction: column; gap: 5px; }
.err {
  margin: 0; padding: 8px 11px; font-size: var(--fs-xs); font-weight: 600;
  color: var(--gap); background: var(--panel); border-left: 3px solid var(--gap);
}

.ft {
  display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  padding: 13px 20px; border-top: 1px solid var(--line); background: var(--panel);
}
.gaph { margin: 0; font-size: var(--fs-2xs); color: var(--muted); flex: 1 1 240px; line-height: 1.5; }
.gaph b { color: var(--gap); }
.acts { display: flex; gap: 8px; margin-left: auto; }

@media (max-width: 560px) {
  .row2 { grid-template-columns: 1fr; }
  .f2 { grid-column: span 1; }
}
</style>
