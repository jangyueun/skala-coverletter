import { defineStore } from 'pinia'
import { api } from '@/api/index.js'

/**
 * 자소서 — 지원서(applications)·문항(questions)·편집 버퍼(drafts).
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
    applications: [],
    questions: [],
    drafts: {},        // { [questionId]: { draft, usedExperienceIds } } — 미커밋 편집분
    savedAt: {},       // { [questionId]: 'HH:MM' }
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
      try {
        const [applications, questions] = await Promise.all([api.answers.applications(), api.answers.list()])
        this.applications = applications; this.questions = questions
      }
      catch (e) { this.error = e }
      finally { this.loading = false; this.loaded = true }
    },

    questionById(id) { return this.questions.find(q => q.id === Number(id)) },

    /** 이 공고의 문항들. 지원서가 없으면 빈 배열. */
    questionsFor(postingId) {
      const app = this.applications.find(a => a.postingId === Number(postingId))
      return app ? this.questions.filter(q => q.applicationId === app.id) : []
    },

    /* ── 버퍼 ────────────────────────────────────────────────
       lazy 다. 화면에 문항을 띄우는 것만으로는 안 만들고, 실제로 고칠 때
       커밋본을 복사해서 만든다. 그래야 "열어만 봤는데 저장 안 됨" 이 뜨지 않는다. */

    /** 화면이 읽어야 할 값 — 버퍼가 있으면 버퍼, 없으면 커밋본 */
    draftOf(questionId) {
      const b = this.drafts[questionId]
      if (b) return b
      const q = this.questionById(questionId)
      return { draft: q?.draft ?? '', usedExperienceIds: q?.usedExperienceIds ?? [] }
    },

    editDraft(questionId, patch) {
      const cur = this.draftOf(questionId)
      this.drafts[questionId] = {
        draft: patch.draft ?? cur.draft,
        usedExperienceIds: patch.usedExperienceIds ?? cur.usedExperienceIds,
      }
    },

    /* 플래그가 아니라 diff 로 판정한다. dirty 플래그를 들면 되돌려 놓아도
       dirty 로 남아, 화면이 사실과 다른 말을 하게 된다. */
    isDirty(questionId) {
      const b = this.drafts[questionId]
      if (!b) return false
      const q = this.questionById(questionId)
      if (!q) return false
      if (b.draft !== (q.draft ?? '')) return true
      const a = [...(b.usedExperienceIds || [])].sort()
      const c = [...(q.usedExperienceIds || [])].sort()
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
      const sent = { draft: b.draft, usedExperienceIds: [...b.usedExperienceIds] }
      try {
        const saved = await api.answers.save(questionId, sent)
        const i = this.questions.findIndex(q => q.id === Number(questionId))
        if (i >= 0) this.questions[i] = saved
        // 그 사이 더 쳤으면 버퍼를 남긴다. isDirty 가 새 커밋본과 비교해 판정한다.
        const now = this.drafts[questionId]
        const same = now && now.draft === sent.draft
          && now.usedExperienceIds.length === sent.usedExperienceIds.length
          && now.usedExperienceIds.every((v, i2) => v === sent.usedExperienceIds[i2])
        if (same) delete this.drafts[questionId]
        const d = new Date()
        this.savedAt[questionId] =
          `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
      }
      catch (e) { this.error = e }        // 버퍼는 그대로 — 다시 저장을 누르면 된다
      finally { delete this.saving[questionId] }
    },
  },
})
