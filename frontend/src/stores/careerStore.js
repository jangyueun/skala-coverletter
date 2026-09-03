import { defineStore } from 'pinia'
import { DATA } from '@/lib/mockData.js'
import { computeMatch, essayProgress, dday, topGap, usedIn } from '@/lib/matching.js'

/**
 * 지금은 목 데이터를 그대로 들고 있다.
 *
 * 백엔드가 생기면 state 를 비운 채 시작하고 actions 가 fetch 를 하게 된다.
 * 컴포넌트는 스토어만 보고 있으므로 그때 컴포넌트는 안 고쳐도 된다 —
 * 그게 이 레이어를 두는 이유다.
 */
export const useCareerStore = defineStore('career', {
  state: () => ({
    competencies: DATA.competencies,
    experiences: DATA.experiences.map(e => ({ ...e })),
    postings: DATA.postings,
    applications: DATA.applications,
    questions: DATA.questions.map(q => ({ ...q })),
    intakeCandidates: DATA.intakeCandidates,
    bannedPhrases: DATA.bannedPhrases,

    // 화면 상태
    sort: 'match',              // 'match' | 'deadline'
    bookmarkOnly: false,
    bookmarks: new Set(),

    /* 자소서 편집 버퍼 — 아직 저장하지 않은 것.
       questionId 를 키로 하는 맵이어야 한다. 단일 버퍼로 두면 문항 탭을
       옮기는 순간(activeId 만 바뀌고 아무 훅도 안 탄다) 조용히 덮인다.
       컴포넌트가 아니라 스토어에 두는 이유도 같다 — 문항 없는 공고로 이동하면
       EssayEditor 가 v-if 로 통째 언마운트되는데, 그때 로컬 ref 는 사라진다. */
    drafts: {},        // { [questionId]: { draft, usedExperienceIds } }
    savedAt: {},       // { [questionId]: 'HH:MM' }

    // 경험이 바뀌면 서버가 재평가한다 — 그 진행을 화면에 보여주기 위한 로그
    assessLog: [],
  }),

  getters: {
    /** 마감이 지나지 않은 공고만. 지난 공고를 목록에 두면 할 일이 아닌 것이 섞인다. */
    livePostings: s => s.postings.filter(p => dday(p.deadline) >= 0),

    /** 카드가 필요한 것을 전부 계산해 붙인 목록. 정렬·필터까지 적용된 최종형. */
    cards() {
      const list = this.livePostings
        .filter(p => !this.bookmarkOnly || this.bookmarks.has(p.id))
        .map(p => ({
          posting: p,
          match: computeMatch(p, this.experiences),
          essay: essayProgress(p, this.questions),
          d: dday(p.deadline),
          bookmarked: this.bookmarks.has(p.id),
          // 같은 기업의 다른 직무가 몇 건인지 — 공고가 직무 단위임을 카드에서 드러낸다
          sameCompany: this.livePostings.filter(x => x.company === p.company).length,
        }))
      return list.sort((a, b) =>
        this.sort === 'match' ? b.match.overall - a.match.overall : a.d - b.d)
    },

    /** 여러 공고에서 동시에 갭인 역량 — "다음에 뭘 채워야 하나" */
    topGap() {
      return topGap(this.livePostings, this.experiences)
    },

    /**
     * MY 화면의 네 목록.
     *
     * 마감이 지난 공고도 포함해야 하므로 livePostings 가 아니라 postings 전체를 본다.
     * 지난 공고는 홈 목록에서는 빠지지만, 내가 자소서를 다 쓴 것이라면
     * "내가 뭘 냈는지" 를 돌아볼 자리가 있어야 한다.
     */
    myLists() {
      const all = this.postings.map(p => ({
        posting: p,
        match: computeMatch(p, this.experiences),
        essay: essayProgress(p, this.questions),
        d: dday(p.deadline),
        bookmarked: this.bookmarks.has(p.id),
        sameCompany: this.postings.filter(x => x.company === p.company).length,
      }))
      const live = all.filter(c => c.d >= 0)
      const byDeadline = (a, b) => a.d - b.d

      return [
        { k: 'bookmark', title: '즐겨찾기한 공고',
          desc: '나중에 보려고 담아 둔 것',
          items: live.filter(c => c.bookmarked).sort(byDeadline) },

        { k: 'writing', title: '자소서 작성 중',
          desc: '문항은 있는데 아직 다 못 채운 것',
          items: live.filter(c => c.essay.state === 'WRITING' || c.essay.state === 'EMPTY').sort(byDeadline) },

        { k: 'done', title: '작성 완료',
          desc: '다 썼고 아직 낼 수 있는 것',
          items: live.filter(c => c.essay.state === 'DONE').sort(byDeadline) },

        /* 마감 지남은 **자소서 완성 여부와 무관하게** 전부 넣는다.
           다 쓰고 낸 것만 남기면, 쓰다가 마감을 놓친 공고가 화면에서 통째로
           사라진다 — 그게 오히려 돌아봐야 할 것이다.
           D-day 가 아니라 "얼마나 전이었나" 순으로 최근 것부터 본다. */
        { k: 'closed', title: '마감 지남',
          desc: '끝난 공고. 다 쓴 것은 다음 지원에 다시 쓰고, 못 끝낸 것은 왜 그랬는지 본다',
          items: all.filter(c => c.d < 0).sort((a, b) => b.d - a.d) },
      ]
    },

    /** 저장 안 된 문항 id — 이탈 경고와 "다른 문항 N개" 표시가 이걸 본다 */
    dirtyIds() {
      return Object.keys(this.drafts).map(Number).filter(id => this.isDirty(id))
    },

    dueSoonCount() {
      return this.cards.filter(c => c.d <= 7).length
    },

    /** 경험이 덮고 있는 역량 id 집합 */
    taggedCompetencyIds() {
      return new Set(this.experiences.flatMap(e => e.competencyIds))
    },
  },

  actions: {
    postingById(id) {
      return this.postings.find(p => p.id === Number(id))
    },
    experienceById(id) {
      return this.experiences.find(e => e.id === Number(id))
    },
    matchFor(posting) {
      return computeMatch(posting, this.experiences)
    },
    usedIn(experienceId) {
      return usedIn(experienceId, this.questions)
    },

    /* ── 자소서 저장 ──────────────────────────────────────────
       버퍼는 lazy 다. 화면에 문항을 띄우는 것만으로는 안 만들고,
       실제로 고칠 때 커밋본을 복사해서 만든다. 그래야 "열어만 봤는데
       저장 안 됨" 이 뜨지 않는다. */

    questionById(id) {
      return this.questions.find(q => q.id === Number(id))
    },

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

    /* 본문과 근거 경험을 한 번에 커밋한다. 따로 저장하면 usedIn() 이
       "근거로 찍혔고 AND 본문이 있음" 을 보기 때문에, 경험 카드의
       "N개 공고에 사용" 배지가 반쪽 상태에서 켜졌다 꺼진다. */
    saveDraft(questionId) {
      const b = this.drafts[questionId]
      const i = this.questions.findIndex(q => q.id === Number(questionId))
      if (!b || i < 0) return
      this.questions[i] = {
        ...this.questions[i],
        draft: b.draft,
        usedExperienceIds: [...b.usedExperienceIds],
      }
      delete this.drafts[questionId]
      const d = new Date()
      this.savedAt[questionId] =
        `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
    },

    /** 저장한 데까지 되돌린다 — 버퍼만 버리므로 커밋본은 안 다친다 */
    revertDraft(questionId) {
      delete this.drafts[questionId]
    },

    toggleBookmark(id) {
      // Set 은 Vue 반응성에서 교체해야 갱신된다. mutate 만 하면 화면이 안 바뀐다.
      const next = new Set(this.bookmarks)
      next.has(id) ? next.delete(id) : next.add(id)
      this.bookmarks = next
    },

    addExperience(exp) {
      const id = Math.max(0, ...this.experiences.map(e => e.id)) + 1
      this.experiences.push({ ...exp, id })
      this.onExperienceChanged('ExperienceCreated', [id])
      return id
    },

    updateExperience(id, patch) {
      const i = this.experiences.findIndex(e => e.id === id)
      if (i < 0) return
      this.experiences[i] = { ...this.experiences[i], ...patch }
      this.onExperienceChanged('ExperienceUpdated', [id])
    },

    /**
     * 경험이 바뀌면 서버가 활성 공고들의 평가를 다시 계산한다.
     *
     * 사용자가 "재평가" 버튼을 누르는 구조가 아니다 — 화면에 그런 버튼이 없다.
     * 이벤트가 발행되고 서버가 큐에 넣어 처리하며, 화면은 그 진행만 본다.
     */
    onExperienceChanged(kind, ids) {
      const t = new Date().toTimeString().slice(0, 8)
      const targets = this.livePostings.length
      this.assessLog.unshift(
        { t, k: 'EVENT', m: `${kind} { experienceIds: [${ids.join(', ')}] }` },
        { t, k: 'QUEUED', m: `ENQUEUE reassess × ${targets}` },
      )
      setTimeout(() => this.assessLog.unshift(
        { t, k: 'RUNNING', m: `RUN reassess (${targets}건)` }), 700)
      setTimeout(() => this.assessLog.unshift(
        { t, k: 'DONE', m: `DONE · 평가 ${targets}건 갱신됨` }), 2400)
    },
  },
})
