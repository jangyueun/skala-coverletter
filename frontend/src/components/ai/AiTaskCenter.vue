<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useAiTasksStore } from '@/stores/aiTasks.js'

/* AI 작업 대기 창 + 우측 하단 플로팅. 앱에 하나만 뜬다(App.vue).
   펼침(panelOpen)이면 작업 목록을, 접힘이면 진행 상황 알약을 보여준다. 닫아도 작업은 스토어에서 계속 돈다. */
const T = useAiTasksStore()
const router = useRouter()

/* 경과 시간을 초 단위로 흐르게 한다. 도는 작업이 있을 때만 타이머를 켠다 — 없는데 매초 렌더하지 않게. */
const now = ref(Date.now())
let timer = null
watch(() => T.running.length, n => {
  if (n && !timer) timer = setInterval(() => { now.value = Date.now() }, 1000)
  if (!n && timer) { clearInterval(timer); timer = null; now.value = Date.now() }
}, { immediate: true })
onBeforeUnmount(() => timer && clearInterval(timer))

const elapsed = job => {
  const ms = (job.doneAt ?? now.value) - job.startedAt
  const s = Math.max(0, Math.round(ms / 1000))
  return s < 60 ? `${s}초` : `${Math.floor(s / 60)}분 ${String(s % 60).padStart(2, '0')}초`
}

const hasJobs = computed(() => T.jobs.length > 0)
const runningCount = computed(() => T.running.length)
const doneCount = computed(() => T.doneUnseen.length)

const errorText = job => job.error?.body?.message || job.error?.message || 'AI 작업이 실패했습니다'

/* "결과 보기" — 서술자대로 화면을 옮긴다. 라우터를 쥔 건 여기다. */
function goto(job) {
  T.markSeen(job)
  T.close()
  const v = job.view
  if (!v) return
  if (v.type === 'draft') {
    router.push({ path: `/postings/${v.postingId}`, query: { tab: 'essay', q: String(v.questionId) } })
  }
  else if (v.type === 'intake') {
    router.push('/experiences').then(() => T.requestIntakeReopen())
  }
}
</script>

<template>
  <!-- 펼친 대기 창 -->
  <section v-if="T.panelOpen && hasJobs" class="center panel" role="dialog" aria-label="AI 작업">
    <header class="hd">
      <p class="ht">AI 작업<span v-if="runningCount" class="hn">{{ runningCount }} 진행 중</span></p>
      <button class="x" aria-label="접기" @click="T.close()">─</button>
    </header>

    <ul class="jobs">
      <li v-for="job in T.jobs" :key="job.id" class="job" :class="job.status">
        <span class="ico" aria-hidden="true">
          <svg v-if="job.status === 'running'" class="spin" viewBox="0 0 24 24" width="18" height="18">
            <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"
                    stroke-dasharray="42 14" />
          </svg>
          <span v-else-if="job.status === 'done'" class="dot ok">✓</span>
          <span v-else class="dot err">!</span>
        </span>

        <div class="body min0">
          <p class="jt">{{ job.title }}</p>
          <p class="js">
            <template v-if="job.status === 'running'">읽는 중… {{ elapsed(job) }}</template>
            <template v-else-if="job.status === 'done'">완료 · {{ elapsed(job) }} 걸림</template>
            <template v-else class="jerr">{{ errorText(job) }}</template>
            <span v-if="job.subtitle" class="jsub"> · {{ job.subtitle }}</span>
          </p>
        </div>

        <div class="act">
          <button v-if="job.status === 'done' && job.view" class="btn btn--sm btn--primary" @click="goto(job)">
            결과 보기
          </button>
          <button v-if="job.status !== 'running'" class="rm" aria-label="지우기" @click="T.dismiss(job)">×</button>
        </div>
      </li>
    </ul>
  </section>

  <!-- 접힘 · 우측 하단 플로팅 -->
  <button v-else-if="hasJobs" class="pill" :class="{ done: !runningCount && doneCount }" @click="T.open()">
    <svg v-if="runningCount" class="spin" viewBox="0 0 24 24" width="17" height="17">
      <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"
              stroke-dasharray="42 14" />
    </svg>
    <span v-else class="pdot" aria-hidden="true">✓</span>
    <span class="ptext">
      <template v-if="runningCount">AI 작업 {{ runningCount }}건 진행 중</template>
      <template v-else>AI 작업 완료</template>
    </span>
    <span v-if="!runningCount && doneCount" class="pbadge">{{ doneCount }}</span>
  </button>
</template>

<style scoped>
/* 펼친 창 · 접힌 알약 모두 우측 하단 고정. 화면 어디에 있든 같은 자리에 뜬다. */
.center {
  position: fixed; right: 20px; bottom: 20px; z-index: 60;
  width: min(380px, calc(100vw - 32px));
  padding: 0; overflow: hidden;
  box-shadow: 0 10px 30px rgba(10, 12, 11, 0.16);
}
.hd {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 14px; border-bottom: 1px solid var(--line);
  background: var(--panel);
}
.ht { margin: 0; font-size: var(--fs-sm); font-weight: 800; letter-spacing: var(--track-tight); }
.hn {
  margin-left: 8px; padding: 1px 8px; border-radius: var(--pill);
  background: var(--accent-soft); color: var(--accent-deep);
  font-size: var(--fs-3xs); font-weight: 700;
}
.x {
  border: none; background: none; cursor: pointer; color: var(--muted);
  font-size: var(--fs-md); line-height: 1; padding: 2px 6px;
}
.x:hover { color: var(--ink); }

.jobs { list-style: none; margin: 0; padding: 6px; display: flex; flex-direction: column; gap: 2px; max-height: 60vh; overflow-y: auto; }
.job {
  display: grid; grid-template-columns: 22px minmax(0, 1fr) auto; align-items: center; gap: 10px;
  padding: 10px; border-radius: var(--r-sm);
}
.job:hover { background: var(--panel-sunken); }
.min0 { min-width: 0; }
.ico { display: grid; place-items: center; color: var(--accent); }
.spin { animation: spin 0.9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.dot {
  display: grid; place-items: center; width: 18px; height: 18px; border-radius: 50%;
  font-size: var(--fs-3xs); font-weight: 800; color: #fff;
}
.dot.ok { background: var(--ok); }
.dot.err { background: var(--gap); }

.jt { margin: 0; font-size: var(--fs-xs); font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.js { margin: 2px 0 0; font-size: var(--fs-2xs); color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.job.error .js { color: var(--gap); font-weight: 600; }
.jsub { color: var(--muted); }

.act { display: flex; align-items: center; gap: 4px; }
.rm { border: none; background: none; cursor: pointer; color: var(--muted); font-size: var(--fs-sm); line-height: 1; padding: 2px 6px; }
.rm:hover { color: var(--gap); }

/* 플로팅 알약 */
.pill {
  position: fixed; right: 20px; bottom: 20px; z-index: 60;
  display: inline-flex; align-items: center; gap: 9px;
  padding: 11px 16px; border: 1px solid var(--line-strong); border-radius: var(--pill);
  background: var(--panel-raised); color: var(--ink); cursor: pointer;
  font-size: var(--fs-xs); font-weight: 700;
  box-shadow: 0 8px 22px rgba(10, 12, 11, 0.16);
  transition: transform var(--seat-out) var(--ease), border-color var(--release) linear;
}
.pill:hover { transform: translateY(-1px); border-color: var(--ink); }
.pill .spin { color: var(--accent); }
.pill.done { border-color: var(--ok); }
.pdot { display: grid; place-items: center; width: 17px; height: 17px; border-radius: 50%; background: var(--ok); color: #fff; font-size: var(--fs-3xs); font-weight: 800; }
.ptext { white-space: nowrap; }
.pbadge {
  display: inline-grid; place-items: center; min-width: 17px; height: 17px; padding: 0 5px;
  border-radius: var(--pill); background: var(--accent); color: var(--accent-ink);
  font-size: var(--fs-3xs); font-weight: 800;
}

@media (max-width: 480px) {
  .center, .pill { right: 12px; bottom: 12px; }
}
</style>
