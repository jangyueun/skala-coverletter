import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAiTasksStore } from '@/stores/aiTasks.js'

/* 전역 AI 작업 스토어 — 요청·완료·실패를 안고, 대기 창/플로팅이 읽는 상태를 준다. */
describe('aiTasks store', () => {
  let T
  beforeEach(() => { setActivePinia(createPinia()); T = useAiTasksStore() })

  const defer = () => { let resolve, reject; const p = new Promise((res, rej) => { resolve = res; reject = rej }); return { p, resolve, reject } }

  it('시작하면 running 으로 목록에 오르고 대기 창이 펼쳐진다', async () => {
    const d = defer()
    const promise = T.start({ kind: 'draft', title: 't', view: { type: 'draft', questionId: 1 }, run: () => d.p })
    expect(T.running).toHaveLength(1)
    expect(T.panelOpen).toBe(true)
    expect(T.isDraftRunning(1)).toBe(true)
    d.resolve({ draft: 'x' })
    await promise
    expect(T.running).toHaveLength(0)
    expect(T.isDraftRunning(1)).toBe(false)
  })

  it('완료되면 결과를 담고 onDone 부수효과를 부른다', async () => {
    let applied = null
    await T.start({ kind: 'draft', title: 't', view: { type: 'draft', questionId: 1 },
      run: async () => ({ draft: '초안', taskId: 9 }), onDone: r => { applied = r } })
    const job = T.jobs[0]
    expect(job.status).toBe('done')
    expect(job.result).toEqual({ draft: '초안', taskId: 9 })
    expect(applied).toEqual({ draft: '초안', taskId: 9 })
  })

  it('실패하면 error 를 담고 onDone 은 안 부른다', async () => {
    let called = false
    await T.start({ kind: 'intake', title: 't', run: async () => { throw new Error('503') }, onDone: () => { called = true } })
    const job = T.jobs[0]
    expect(job.status).toBe('error')
    expect(job.error.message).toBe('503')
    expect(called).toBe(false)
  })

  it('같은 문항 초안이 이미 돌면 새로 만들지 않는다 — 버튼을 두 번 눌러도 하나', () => {
    const d = defer()
    T.start({ kind: 'draft', title: 't', view: { type: 'draft', questionId: 1 }, run: () => d.p })
    T.start({ kind: 'draft', title: 't2', view: { type: 'draft', questionId: 1 }, run: () => defer().p })
    expect(T.jobs.filter(j => j.kind === 'draft')).toHaveLength(1)
  })

  it('창을 닫아 둔 사이 완료되면 배지가 오르고, 열면 본 것으로 처리된다', async () => {
    const d = defer()
    const promise = T.start({ kind: 'draft', title: 't', view: { type: 'draft', questionId: 1 }, run: () => d.p })
    T.close()                 // 진행 중에 창을 접는다 — 이제 완료돼도 못 본 상태다
    d.resolve({})
    await promise
    expect(T.doneUnseen).toHaveLength(1)
    T.open()
    expect(T.doneUnseen).toHaveLength(0)
  })

  it('latestIntake 는 인테이크 작업을, clearIntake 는 그것만 지운다', async () => {
    await T.start({ kind: 'draft', title: 'd', view: { type: 'draft', questionId: 1 }, run: async () => ({}) })
    await T.start({ kind: 'intake', title: 'i', view: { type: 'intake' }, run: async () => ({ candidates: [1] }) })
    expect(T.latestIntake.result.candidates).toEqual([1])
    T.clearIntake()
    expect(T.latestIntake).toBeNull()
    expect(T.jobs.filter(j => j.kind === 'draft')).toHaveLength(1)
  })

  it('인테이크 결과 보기 신호는 seq 를 올린다', () => {
    const before = T.intakeReopenSeq
    T.requestIntakeReopen()
    expect(T.intakeReopenSeq).toBe(before + 1)
  })
})
