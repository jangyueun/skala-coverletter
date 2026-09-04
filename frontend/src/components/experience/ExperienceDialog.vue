<script setup>
import { ref, computed, reactive } from 'vue'
import { useExperiencesStore } from '@/stores/experiences.js'
import { EXPERIENCE_CATEGORIES, toMonth, fromMonth, periodValid } from '@/domain/experience.js'
import CompetencyPicker from './CompetencyPicker.vue'
import IntakePanel from './IntakePanel.vue'

const E = useExperiencesStore()
const el = ref(null)
const editId = ref(null)
const tab = ref('manual')   // manual | intake

const FIELDS = [
  { k: 'situation', l: 'S', d: '어떤 상황이었나', rows: 2 },
  { k: 'task',      l: 'T', d: '무엇을 목표로 삼았나', rows: 2 },
  { k: 'action',    l: 'A', d: '내가 한 행동과 적용한 방식', rows: 3 },
  { k: 'result',    l: 'R', d: '결과 (숫자로)', rows: 2 },
]

/* 기간은 시작·종료 월 두 칸이다(v6). 서버는 startDate·endDate(YYYY-MM-DD, 둘 다 NULL 허용)로 받고
   월 입력은 1일로 저장한다. 표시 문자열("2025.03 – 2025.11")은 카드가 periodLabel() 로 만든다.
   분류는 코드값이고 한글은 EXPERIENCE_CATEGORIES 가 붙인다. */
const blank = () => ({
  title: '', startMonth: '', endMonth: '', category: EXPERIENCE_CATEGORIES[0].k,
  situation: '', task: '', action: '', result: '', comp: {},
})
const form = reactive(blank())
const errors = ref([])
/* 저장 요청이 서버에 가 있는 동안 true. 버튼을 두 번 누르면 POST 가 두 번 나가 경험이 둘 생겼다 — 첫 요청이 끝날 때까지 막는다. */
const busy = ref(false)

/* wantTab='intake' 로 열면 포폴 탭에서 시작한다 — AI 작업 "결과 보기" 로 돌아올 때 쓴다.
   수정(id 있음)은 인테이크 탭이 아예 없으므로 항상 직접 입력이다. */
function open(id, wantTab) {
  editId.value = id ?? null
  Object.assign(form, blank())
  if (id != null) {
    const e = E.byId(id)
    if (e) {
      Object.assign(form, {
        title: e.title, startMonth: toMonth(e.startDate), endMonth: toMonth(e.endDate), category: e.category,
        // S·T·A 는 서버에서 NULL 일 수 있다 — trim() 이 터지지 않게 빈 문자열로 받는다
        situation: e.situation ?? '', task: e.task ?? '', action: e.action ?? '', result: e.result ?? '',
        comp: Object.fromEntries(e.competencies.map(c => [c.competencyId, c.strength])),
      })
    }
  }
  errors.value = []
  tab.value = editId.value == null && wantTab === 'intake' ? 'intake' : 'manual'
  el.value.showModal()
}
defineExpose({ open })

const starDone = computed(() => FIELDS.filter(f => form[f.k].trim()).length)
const periodOk = computed(() => periodValid(fromMonth(form.startMonth), fromMonth(form.endMonth)))

/* 서버에 await 한다. 성공해야 닫는다 — 실패하면 폼이 남고 이유가 errors 에 뜬다. */
async function save() {
  if (busy.value) return
  const errs = []
  if (!form.title.trim()) errs.push('제목은 필수입니다.')
  if (!form.result.trim()) errs.push('결과(R)는 필수입니다. 성과 없는 경험은 자소서에서 쓸 수 없습니다.')
  if (!periodOk.value) errs.push('종료 월이 시작 월보다 앞섭니다.')
  if (!Object.keys(form.comp).length) errs.push('역량을 최소 하나 태그해야 매칭에 쓰입니다.')
  errors.value = errs
  if (errs.length) return

  /* v6 POST·PUT /api/experiences 본문 그대로. 역량은 { competencyId, strength } 목록이다. */
  const body = {
    title: form.title.trim(),
    category: form.category,
    startDate: fromMonth(form.startMonth), endDate: fromMonth(form.endMonth),
    situation: form.situation.trim(), task: form.task.trim(),
    action: form.action.trim(), result: form.result.trim(),
    competencies: Object.entries(form.comp).map(([id, strength]) => ({ competencyId: Number(id), strength })),
  }
  busy.value = true
  try {
    if (editId.value != null) await E.update(editId.value, body)
    else await E.create(body)
  } catch (e) {
    errors.value = [`저장에 실패했습니다 — ${e.body?.message || e.message}`]
    return
  } finally {
    busy.value = false
  }
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
          <!-- 월 두 칸. 한 칸짜리 자유 문자열이던 때는 "2026.08" 과 "26년 8월" 이 섞여 정렬도 못 했다.
               종료는 비워도 된다 — 진행 중이거나 한 달짜리다. -->
          <div class="fld">
            <span id="period-lb" class="lb">기간 <span class="lbn">비워도 됩니다</span></span>
            <div class="months" role="group" aria-labelledby="period-lb">
              <input v-model="form.startMonth" type="month" class="inp" aria-label="시작 월" :max="form.endMonth || undefined">
              <span class="dash" aria-hidden="true">–</span>
              <input v-model="form.endMonth" type="month" class="inp" aria-label="종료 월" :min="form.startMonth || undefined">
            </div>
          </div>
          <label class="fld">
            <span class="lb">분류</span>
            <select v-model="form.category" class="inp">
              <option v-for="c in EXPERIENCE_CATEGORIES" :key="c.k" :value="c.k">{{ c.label }}</option>
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
        <div class="acts">
          <button type="button" class="btn btn--sm" @click="el.close()">취소</button>
          <button type="button" class="btn btn--primary" :disabled="busy" @click="save()">
            {{ busy ? '저장 중…' : editId != null ? '저장' : '등록' }}
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
  /* 브라우저 기본값이 dialog 안의 글자를 검정(CanvasText)으로 되돌린다.
     다크에서 제목이 검정으로 남던 이유다. 잉크를 다시 물려준다. */
  color: var(--ink);
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
.lbn { font-weight: 500; margin-left: 4px; }
.fl { color: var(--accent); font-family: var(--mono); margin-right: 3px; }

/* 시작–종료 두 칸이 한 줄에 앉는다. 대시는 장식이지 입력이 아니다. */
.months { display: flex; align-items: center; gap: 6px; }
.months .inp { flex: 1; min-width: 0; }
.dash { color: var(--muted); flex: none; }

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
.acts { display: flex; gap: 8px; margin-left: auto; }

@media (max-width: 560px) {
  .row2 { grid-template-columns: 1fr; }
  .f2 { grid-column: span 1; }
}
</style>
