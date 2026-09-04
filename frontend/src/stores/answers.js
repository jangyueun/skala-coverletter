import { defineStore } from 'pinia'
import { api } from '@/api/index.js'

/**
 * 자소서 — 문항(questions)과 편집 버퍼(drafts).
 *
 * 문항 모양은 v6 GET /api/postings/{id}/questions 항목 + postingId. answer 는 로그인 사용자의 답변이고
 * 없으면 null — 지원서(application)라는 중간 단계는 없어졌다. 문항은 공고에 직접 붙는다.
 *
 * 문항은 두 길로 들어온다 —
 *   load()             앱을 켤 때 한 번. 목은 전 공고 문항을 다 주고, 실제 서버는 그런 API 가 없어 빈 배열이다.
 *   loadFor(postingId) 공고 상세를 열 때. 두 모드 다 이 길을 탄다. 그 공고의 기존 행을 빼고 새로 받은 것으로 갈아 끼운다.
 * loadedFor[postingId] 가 켜진 공고는 브라우저가 최신 문항을 들고 있다는 뜻이고, derived.essayFor 가
 * 서버의 essay 요약 대신 이걸 센다 — 저장 직후 카드가 바로 따라오게.
 *
 * 버퍼는 questionId 를 키로 하는 맵이어야 한다. 단일 버퍼로 두면 문항 탭을
 * 옮기는 순간(activeId 만 바뀌고 아무 훅도 안 탄다) 조용히 덮인다.
 * 컴포넌트가 아니라 스토어에 두는 이유도 같다 — 문항 없는 공고로 이동하면
 * EssayEditor 가 v-if 로 통째 언마운트되는데, 그때 로컬 ref 는 사라진다.
 *
 * 저장은 서버에 await 하고, **성공한 뒤에만** 커밋본을 갈아 끼우고 버퍼를 지운다.
 * 실패하면 버퍼는 남는다 — 쓴 글은 안 사라진다.
 */
export const useAnswersStore = defineStore('answers', {
  state: () => ({
    questions: [],     // postingId 로 묶인다. 목은 전 공고, 실제는 열어 본 공고만
    loadedFor: {},     // { [postingId]: true } — loadFor 로 그 공고 문항을 받았다
    loadingFor: {},    // { [postingId]: true } — 받는 중
    drafts: {},        // { [questionId]: { content, usedExperienceIds, draftTaskId } } — 미커밋 편집분
    savedAt: {},       // { [questionId]: 'HH:MM' } — 서버 updatedAt
    saving: {},        // { [questionId]: true } — 저장 중인 문항
    loading: false,
    error: null,
    loaded: false,
  }),

  getters: {
    /** 저장 안 된 문항 id — 이탈 경고와 "다른 문항 N개" 표시가 이걸 본다 */
    dirtyIds() {
      return Object.keys(this.drafts).map(Number).filter(id => this.isDirty(id))
    },
  },

  actions: {
    async load() {
      if (this.loading) return
      this.loading = true; this.error = null
      try { this.questions = await api.answers.list() }
      catch (e) { this.error = e }
      finally { this.loading = false; this.loaded = true }
    },

    /** 이 공고의 문항을 서버에서 받아 갈아 끼운다. 버퍼(drafts)는 건드리지 않는다 — 쓰던 글은 남는다. */
    async loadFor(postingId) {
      const id = Number(postingId)
      if (!id || this.loadingFor[id]) return
      this.loadingFor[id] = true; this.error = null
      try {
        const rows = await api.answers.questions(id)
        this.questions = [
          ...this.questions.filter(q => q.postingId !== id),
          ...rows.map(q => ({ ...q, postingId: id })),
        ]
        this.loadedFor[id] = true
      }
      catch (e) { this.error = e }
      finally { delete this.loadingFor[id] }
    },

    questionById(id) { return this.questions.find(q => q.id === Number(id)) },

    /** 이 공고의 문항들, 순번대로. 없으면 빈 배열. */
    questionsFor(postingId) {
      return this.questions
        .filter(q => q.postingId === Number(postingId))
        .sort((a, b) => a.sequence - b.sequence)
    },

    /** 커밋본의 답변. 서버는 없는 답변을 null 로 준다 — 화면은 빈 답변으로 본다. */
    answerOf(questionId) {
      const a = this.questionById(questionId)?.answer
      return { content: a?.content ?? '', usedExperienceIds: a?.usedExperienceIds ?? [] }
    },

    /* ── 버퍼 ────────────────────────────────────────────────
       lazy 다. 화면에 문항을 띄우는 것만으로는 안 만들고, 실제로 고칠 때
       커밋본을 복사해서 만든다. 그래야 "열어만 봤는데 저장 안 됨" 이 뜨지 않는다. */

    /** 화면이 읽어야 할 값 — 버퍼가 있으면 버퍼, 없으면 커밋본 */
    draftOf(questionId) {
      return this.drafts[questionId] ?? { ...this.answerOf(questionId), draftTaskId: null }
    },

    editDraft(questionId, patch) {
      const cur = this.draftOf(questionId)
      this.drafts[questionId] = {
        content: patch.content ?? cur.content,
        usedExperienceIds: patch.usedExperienceIds ?? cur.usedExperienceIds,
        /* AI 초안을 넣은 뒤 손으로 고쳐도 출처는 남는다 — 서버의 ai_task_id 는
           "초안을 반영했다" 는 뜻이지 "그대로 냈다" 가 아니다. */
        draftTaskId: patch.draftTaskId !== undefined ? patch.draftTaskId : cur.draftTaskId,
      }
    },

    /* 플래그가 아니라 diff 로 판정한다. dirty 플래그를 들면 되돌려 놓아도
       dirty 로 남아, 화면이 사실과 다른 말을 하게 된다. */
    isDirty(questionId) {
      const b = this.drafts[questionId]
      if (!b) return false
      if (!this.questionById(questionId)) return false
      const q = this.answerOf(questionId)
      if (b.content !== q.content) return true
      const a = [...(b.usedExperienceIds || [])].sort()
      const c = [...q.usedExperienceIds].sort()
      return a.length !== c.length || a.some((v, i) => v !== c[i])
    },

    /** 본문과 근거 경험을 한 번에 커밋한다 — usedIn() 이 둘을 AND 로 보기 때문이다. */
    async save(questionId) {
      const b = this.drafts[questionId]
      if (!b || this.saving[questionId]) return
      this.saving[questionId] = true; this.error = null
      /* 보낸 값을 붙잡아 둔다. await 이 도는 300~800ms 동안 사용자는 계속 타이핑하고,
         그 사이 입력까지 지우면 저장을 누른 사람이 쓴 글을 잃는다 —
         목 지연이 실제 네트워크와 비슷해 평범한 리듬에서 바로 걸린다. */
      const sent = { content: b.content, usedExperienceIds: [...b.usedExperienceIds], draftTaskId: b.draftTaskId ?? null }
      try {
        const saved = await api.answers.save(questionId, sent)
        const q = this.questionById(questionId)
        if (q) {
          q.answer = {
            content: saved.content, charCount: saved.charCount,
            usedExperienceIds: saved.usedExperienceIds, updatedAt: saved.updatedAt,
          }
        }
        // 그 사이 더 쳤으면 버퍼를 남긴다. isDirty 가 새 커밋본과 비교해 판정한다.
        const now = this.drafts[questionId]
        const same = now && now.content === sent.content
          && now.usedExperienceIds.length === sent.usedExperienceIds.length
          && now.usedExperienceIds.every((v, i2) => v === sent.usedExperienceIds[i2])
        if (same) delete this.drafts[questionId]
        this.savedAt[questionId] = hhmm(saved.updatedAt)
      }
      catch (e) { this.error = e }        // 버퍼는 그대로 — 다시 저장을 누르면 된다
      finally { delete this.saving[questionId] }
    },
  },
})

/** 서버 updatedAt(ISO) → 'HH:MM'. 못 읽으면 지금 시각 — "저장됨" 이 시각 없이 뜨는 것보다 낫다. */
function hhmm(iso) {
  const d = iso ? new Date(iso) : new Date()
  const t = Number.isNaN(d.getTime()) ? new Date() : d
  return `${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`
}
