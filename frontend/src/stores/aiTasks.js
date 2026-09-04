import { defineStore } from 'pinia'

/**
 * 진행 중인 AI 작업을 한곳에서 안다. 초안·인테이크가 여기로 모인다.
 *
 * 왜 전역인가 — AI 작업은 몇십 초에서 몇 분이 걸린다(인테이크는 저장소를 읽느라). 그동안 사용자가 그 화면을
 * 떠나거나 창을 닫아도 작업은 계속 돌아야 하고, 어디에 있든 진행 상황이 보여야 한다. 컴포넌트에 두면 언마운트되는
 * 순간 폴링이 끊긴다. 그래서 요청·폴링·결과를 이 스토어가 통째로 안고, 화면(AiTaskCenter · EssayEditor · IntakePanel)은
 * 이 스토어를 읽기만 한다.
 *
 * 결과 반영은 두 가지다 —
 *   onDone   완료 즉시 부수효과(초안을 버퍼에 넣기). 컴포넌트가 없어도 스토어 싱글턴을 부르므로 안전하다.
 *   view     "결과 보기" 가 갈 곳을 가리키는 서술자(닫힌 화면을 다시 여는 신호). AiTaskCenter 가 해석한다.
 *            함수가 아니라 데이터로 둔다 — 작업을 시작한 컴포넌트가 언마운트돼도 살아 있어야 하기 때문이다.
 */
export const useAiTasksStore = defineStore('aiTasks', {
  state: () => ({
    jobs: [],            // 최신이 앞. { id, kind, title, subtitle, status, startedAt, doneAt, result, error, seen, view }
    panelOpen: false,    // 대기 창이 펼쳐져 있나. 닫으면 우측 하단 플로팅으로 접힌다
    nextId: 1,
    intakeReopenSeq: 0,  // "인테이크 결과 보기" 가 오르면 ExperienceView 가 다이얼로그를 다시 연다
  }),

  getters: {
    running: s => s.jobs.filter(j => j.status === 'running'),
    /** 완료·실패했는데 아직 대기 창을 안 연 것 — 플로팅 배지가 이걸 센다 */
    doneUnseen: s => s.jobs.filter(j => j.status !== 'running' && !j.seen),
    latestIntake: s => s.jobs.find(j => j.kind === 'intake') ?? null,
  },

  actions: {
    /** 이 문항의 초안이 지금 만들어지고 있나 — EssayEditor 버튼 잠금이 본다 */
    isDraftRunning(questionId) {
      return this.jobs.some(j => j.kind === 'draft' && j.status === 'running' && j.view?.questionId === questionId)
    },
    /** 이 문항의 마지막 초안 작업(실패 메시지를 화면에 그릴 때 쓴다) */
    draftJobFor(questionId) {
      return this.jobs.find(j => j.kind === 'draft' && j.view?.questionId === questionId) ?? null
    },

    /**
     * 작업 하나를 시작하고 끝까지 안는다. run() 이 결과가 담긴 Promise 를 준다(api/real/ai.js 가 폴링을 이미 안는다).
     * 성공하면 onDone 으로 부수효과를, 실패하면 error 를 담는다. 시작하면 대기 창을 펼친다.
     */
    async start({ kind, title, subtitle = '', view = null, run, onDone }) {
      // 같은 문항 초안이 이미 돌고 있으면 새로 만들지 않는다 — 버튼을 두 번 눌러도 하나다.
      if (kind === 'draft' && this.isDraftRunning(view?.questionId)) {
        return this.draftJobFor(view?.questionId)
      }
      this.jobs.unshift({
        id: this.nextId++, kind, title, subtitle,
        status: 'running', startedAt: Date.now(), doneAt: null,
        result: null, error: null, seen: false, view,
      })
      if (this.jobs.length > 12) this.jobs.length = 12
      // 배열에 들어간 반응형 프록시를 잡아 그걸 고친다. 밖에서 만든 원본 객체를 고치면 화면이 안 따라온다.
      const job = this.jobs[0]
      this.panelOpen = true

      try {
        job.result = await run()
        job.status = 'done'
      }
      catch (e) {
        job.error = e
        job.status = 'error'
      }
      finally {
        job.doneAt = Date.now()
        // 대기 창을 보고 있는 중이면 방금 끝난 것도 본 것으로 친다 — 배지가 헛되이 오르지 않게.
        if (this.panelOpen) job.seen = true
      }
      if (job.status === 'done' && onDone) {
        try { onDone(job.result) }
        catch (e) { console.warn('[aiTasks] onDone 부수효과 실패', e) }
      }
      return job
    },

    open() { this.panelOpen = true; this.jobs.forEach(j => { j.seen = true }) },
    close() { this.panelOpen = false },
    toggle() { this.panelOpen ? this.close() : this.open() },

    /** "결과 보기" — 서술자대로 화면을 옮긴다. 실제 이동은 AiTaskCenter(라우터를 쥔 곳)가 한다. */
    markSeen(job) { job.seen = true },
    requestIntakeReopen() { this.intakeReopenSeq++ },

    /** 목록에서 지운다(작업은 이미 끝난 것만). */
    dismiss(job) {
      const i = this.jobs.indexOf(job)
      if (i >= 0) this.jobs.splice(i, 1)
      if (!this.jobs.length) this.panelOpen = false
    },

    /** 인테이크 작업을 비운다 — 등록을 마쳐 패널을 초기화할 때. */
    clearIntake() {
      this.jobs = this.jobs.filter(j => j.kind !== 'intake')
      if (!this.jobs.length) this.panelOpen = false
    },
  },
})
