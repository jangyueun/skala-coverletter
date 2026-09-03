import { defineStore } from 'pinia'
import { usePostingsStore } from './postings.js'
import { useExperiencesStore } from './experiences.js'
import { useAnswersStore } from './answers.js'
import { useUiStore } from './ui.js'
import { useAuthStore } from './auth.js'
import { computeMatch, topGap } from '@/domain/matching.js'
import { essayProgress, essayFromSummary, usedIn } from '@/domain/essay.js'
import { dday, isClosed } from '@/domain/deadline.js'

/**
 * 스토어 넷을 읽어 화면이 쓰는 모양으로 합친 파생값. state 가 없다 — 전부 getter 다.
 *
 * 한 스토어에 두지 않은 이유: cards 는 공고+경험+답변+즐겨찾기를 다 읽는데,
 * 넷 중 어느 하나의 것도 아니다. 공고 스토어에 두면 공고 스토어가 답변 스토어를
 * 알게 되고, 백엔드가 붙어 갱신 시점이 갈라질 때 그 결합이 발목을 잡는다.
 *
 * 계산은 domain/ 에 있다. 여기서는 부르기만 한다.
 *
 * 서버 값과 브라우저 계산이 둘 다 있는 자리는 여기서 고른다 —
 *   match   브라우저. 서버 match 는 MATCH 워커가 없어 늘 null 이다. 워커가 붙으면 posting.match 를 우선한다.
 *   essay   그 공고 문항을 받았으면(loadFor) 브라우저, 아니면 서버 요약(posting.essay), 그것도 없으면(목) 브라우저.
 *   usedIn  브라우저 값과 서버 값(experience.usedInQuestions) 중 큰 쪽.
 */
export const useDerivedStore = defineStore('derived', {
  getters: {
    /** 세 스토어가 다 왔나. 하나라도 안 왔으면 화면은 Skeleton 을 그린다.
        auth 는 여기 안 넣는다 — 카드가 무엇을 보여줄지는 가르지만, 안 와도 그릴 것은 있다. */
    ready() {
      return usePostingsStore().loaded && useExperiencesStore().loaded && useAnswersStore().loaded
    },

    /** 카드가 필요한 것을 전부 계산해 붙인 목록. 정렬까지 적용된 최종형. */
    cards() {
      const P = usePostingsStore(), ui = useUiStore()
      /* 매칭순은 로그인 상태에서만 뜻이 있다. ui.sort 를 사인아웃에서만 되돌리면
         **한 번도 로그인 안 한 첫 방문**이 그 경로를 안 지나 숨긴 값으로 정렬된다.
         값을 고치는 대신 여기서 파생하면 두 경로가 한 번에 덮인다. */
      const sort = useAuthStore().signedIn ? ui.sort : 'deadline'
      const list = P.live.map(p => this.cardOf(p, P.live))
      return list.sort((a, b) =>
        sort === 'match' ? b.match.overall - a.match.overall : a.d - b.d)
    },

    /**
     * MY 화면의 네 목록.
     *
     * 마감이 지난 공고도 포함해야 하므로 live 가 아니라 list 전체를 본다.
     * 지난 공고는 홈 목록에서는 빠지지만, 내가 자소서를 다 쓴 것이라면
     * "내가 뭘 냈는지" 를 돌아볼 자리가 있어야 한다.
     */
    myLists() {
      const P = usePostingsStore()
      const all = P.list.map(p => this.cardOf(p, P.list))
      const live = all.filter(c => !c.closed)
      const byDeadline = (a, b) => a.d - b.d
      return [
        { k: 'bookmark', title: '즐겨찾기한 공고', desc: '나중에 보려고 담아 둔 것',
          items: live.filter(c => c.bookmarked).sort(byDeadline) },
        { k: 'writing', title: '자소서 작성 중', desc: '문항은 있는데 아직 다 못 채운 것',
          items: live.filter(c => c.essay.state === 'WRITING' || c.essay.state === 'EMPTY').sort(byDeadline) },
        { k: 'done', title: '작성 완료', desc: '다 썼고 아직 낼 수 있는 것',
          items: live.filter(c => c.essay.state === 'DONE').sort(byDeadline) },
        /* 마감 지남은 자소서 완성 여부와 무관하게 전부 넣는다. 다 쓰고 낸 것만 남기면
           쓰다가 마감을 놓친 공고가 통째로 사라진다 — 그게 오히려 돌아봐야 할 것이다. */
        { k: 'closed', title: '마감 지남',
          desc: '끝난 공고. 다 쓴 것은 다음 지원에 다시 쓰고, 못 끝낸 것은 왜 그랬는지 본다',
          items: all.filter(c => c.closed).sort((a, b) => b.d - a.d) },
      ]
    },

    /** 여러 공고에서 동시에 갭인 역량 — "다음에 뭘 채워야 하나" */
    topGap() {
      const P = usePostingsStore(), E = useExperiencesStore()
      return P.loaded && E.loaded ? topGap(P.live, E.list, P.competencies) : null
    },
  },

  actions: {
    cardOf(p, scope) {
      return {
        posting: p,
        match: this.matchFor(p),
        essay: this.essayFor(p),
        d: dday(p.deadline),
        closed: isClosed(p.deadline),
        // 서버가 공고 DTO 에 실어 준다. 목도 같은 자리에 둔다 — 스토어가 뒤집는다(postings.toggleBookmark).
        bookmarked: !!p.bookmarked,
        // 같은 기업의 다른 직무가 몇 건인지 — 공고가 직무 단위임을 카드에서 드러낸다
        sameCompany: scope.filter(x => x.company === p.company).length,
      }
    },
    matchFor(posting) {
      const P = usePostingsStore(), E = useExperiencesStore()
      return computeMatch(posting, E.list, P.competencies)
    },
    essayFor(posting) {
      const A = useAnswersStore()
      /* 문항을 이 공고 단위로 받았으면 그게 최신이다 — 저장 직후 카드가 바로 따라온다.
         아니면 목록 DTO 의 essay 요약(서버가 센 값). 그것도 없으면(목) 전 공고 문항에서 센다. */
      if (A.loadedFor[posting.id] || !posting.essay) return essayProgress(A.questionsFor(posting.id))
      return essayFromSummary(posting.essay)
    },
    usedIn(experienceId) {
      const local = usedIn(experienceId, useAnswersStore().questions)
      /* 실제 서버는 문항을 공고 단위로만 주므로 브라우저가 전부를 들고 있지 않다. 서버가 세어 준
         usedInQuestions(경험 DTO)와 브라우저 값 중 큰 쪽 — 새로고침 직후엔 서버가, 저장 직후엔 브라우저가 맞다.
         목은 usedInQuestions 가 없어 브라우저 값 그대로다. */
      const server = useExperiencesStore().byId(experienceId)?.usedInQuestions ?? 0
      return { ...local, questions: Math.max(local.questions, server) }
    },
  },
})
