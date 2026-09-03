import Anthropic from '@anthropic-ai/sdk'
import { z } from 'zod'
import { zodOutputFormat } from '@anthropic-ai/sdk/helpers/zod'

/**
 * dev 전용 AI 서버 — Vite 프로세스 안에서 도는 미들웨어.
 *
 * 왜 이렇게 하나:
 *
 * 1. **키가 브라우저에 안 간다.** Vite 는 `VITE_` 로 시작하는 환경변수를 빌드 산출물에
 *    문자열로 박아 넣는다. 프런트에서 Anthropic 을 직접 부르면 dist 안에 키가 평문으로
 *    들어가고, devtools 네트워크 탭에도 그대로 보인다. 여기서는 키를 Node 쪽에서만 읽는다.
 *
 * 2. **나중에 지우기만 하면 된다.** 프런트는 `/api/ai/*` 를 부르는데, 그건 나중에 Spring 이
 *    서빙할 바로 그 경로다. 백엔드가 생기면 이 플러그인을 vite.config.js 에서 빼고
 *    프록시가 :8080 으로 넘기게 두면 끝 — 프런트 코드는 한 줄도 안 고친다.
 *
 * 3. **별도 프로세스가 없다.** `npm run dev` 하나로 돈다. FastAPI 를 따로 띄울 필요가 없다.
 *
 * 키가 없어도 앱은 뜬다. 그 경우 이 엔드포인트만 503 을 주고 이유를 말한다.
 */

/* ── AI 가 돌려줘야 하는 모양 ────────────────────────────────
   스키마를 주면 모델이 반드시 이 형태로만 답한다. 파싱 실패를 걱정하지 않아도 된다. */

const RequiredCompetency = z.object({
  competencyId: z.number().describe('반드시 주어진 역량 사전 안의 id 중 하나'),
  /* 범위를 스키마로 막는다. weight 는 computeMatch 가중평균의 **분모**라
     0 이 오면 모든 공고가 0.0 이 되고 음수면 "매칭률 : -40%" 가 나온다.
     id 를 검증하는 이유("지어낸 값이 매칭 점수로 흘러든다")가 그대로 적용된다. */
  weight: z.number().min(0.5).max(1).describe('0.5~1.0. 공고가 이 역량을 얼마나 무겁게 요구하는가'),
  evidence: z.string().describe('그렇게 판단한 근거가 된 공고 원문 문장 그대로'),
})

const ExtractResult = z.object({
  required: z.array(RequiredCompetency),
  newCompetencies: z.array(z.string())
    .describe('사전에 없어서 매길 수 없었던 요구사항. 없으면 빈 배열'),
  role: z.enum(['BACKEND', 'FRONTEND', 'FULLSTACK', 'PLATFORM', 'AI', 'ETC'])
    .describe('직무 계열. 기업이 쓰는 직무명이 제각각이라 계열로 묶는다'),
})

const EXTRACT_SYSTEM = `너는 채용공고에서 요구 역량을 뽑는다.

지켜야 할 것:
- **주어진 역량 사전 안에서만 고른다.** 사전에 없는 것을 competencyId 로 지어내지 마라.
  사전으로 표현할 수 없는 요구사항은 newCompetencies 에 문자열로 넣어라.
- evidence 는 **공고 원문 문장을 그대로** 옮긴다. 요약하거나 다듬지 마라.
  근거를 확인할 수 없으면 그 역량을 넣지 마라.
- weight 는 공고가 그것을 어디에 뒀는지로 판단한다.
  자격요건 상단 > 자격요건 하단 > 우대사항 > 인재상 순으로 무겁다.
- 없는 것을 지어내는 것보다 적게 뽑는 것이 낫다. 이 결과가 사용자의 매칭 점수를 움직인다.`

/* ── 포폴 인테이크 ─────────────────────────────────────────
   사용자가 준 링크를 **모델이 직접 읽는다.** web_fetch 는 서버 도구라
   우리가 크롤러를 짤 필요가 없고, 대화에 이미 있는 URL 만 가져오므로
   모델이 엉뚱한 곳에 접속할 수 없다. */

const Evidence = z.object({
  type: z.enum(['REPO', 'PR', 'FILE', 'DOC', 'PAGE']).describe('근거의 출처 종류'),
  ref: z.string().describe('저장소명·파일 경로·PR 번호·문서명 등 사람이 알아볼 식별자'),
  quote: z.string().describe('근거가 된 원문 조각 그대로. 요약하지 마라'),
})

const Question = z.object({
  field: z.enum(['title', 'period', 'situation', 'task', 'action', 'result']),
  q: z.string().describe('본인에게 물어야 할 것. 자료에 답이 없어서 묻는 것이다'),
  why: z.string().describe('왜 자료만으로는 못 채우는지'),
})

const Candidate = z.object({
  key: z.string().describe('영문 소문자 짧은 식별자. 중복되지 않게'),
  title: z.string(),
  period: z.string().describe('YYYY.MM 또는 YYYY.MM - YYYY.MM. 자료에서 확인 안 되면 빈 문자열'),
  category: z.enum(['팀 프로젝트', '개인 프로젝트', '실습 프로젝트', '대외활동', '인턴·근무', '수상·자격']),
  situation: z.string().describe('어떤 상황이었나. **자료에서 확인된 것만.** 없으면 빈 문자열'),
  action: z.string().describe('무엇을 했나. **자료에서 확인된 것만.** 없으면 빈 문자열'),
  evidence: z.array(Evidence),
  questions: z.array(Question),
  suggestedCompetencyIds: z.array(z.number()).describe('반드시 주어진 사전 안의 id'),
})

const IntakeResult = z.object({
  candidates: z.array(Candidate),
  unreadable: z.array(z.object({
    source: z.string(),
    reason: z.string().describe('왜 못 읽었는지 — 비공개, 404, 내용 없음 등'),
  })).describe('읽으려 했으나 실패한 자료. 없으면 빈 배열'),
})

const INTAKE_SYSTEM = `너는 지원자가 준 자료(저장소 · 포트폴리오 · 발표자료)를 읽고
자소서에 쓸 **경험 후보**를 뽑는다. 링크는 web_fetch 로 직접 읽어라.

지켜야 할 것:

- **자료에서 확인된 것만 쓴다.** 코드에 없는 목표(task)와 수치(result)는
  절대 지어내지 말고 questions 로 되물어라. 지어낸 성과는 면접에서 그대로 무너진다.
  그래서 이 스키마에는 task 와 result 칸이 아예 없다 — 본인이 다음 화면에서 쓴다.
- situation 과 action 도 확인 안 되면 **빈 문자열**로 둬라. 채우는 것보다 비우는 게 낫다.
- evidence.quote 는 원문 조각 그대로다. 파일 경로 · 커밋 메시지 · PR 제목 · 문서 문장.
  요약하거나 다듬지 마라. 근거를 댈 수 없으면 그 후보를 만들지 마라.
- suggestedCompetencyIds 는 **주어진 사전 안에서만** 고른다. 지어낸 id 는 매칭 점수로 흘러든다.
  자료로 증명되는 것만 골라라 — README 에 이름만 적힌 기술은 근거가 아니다.
- 하나의 자료에서 서로 다른 경험이 여럿 보이면 나눠라. 반대로 여러 자료가
  같은 프로젝트를 가리키면 하나로 합치고 evidence 를 여러 개 달아라.
- 읽지 못한 링크는 unreadable 에 이유와 함께 넣어라. 조용히 빠뜨리지 마라 —
  사용자는 자기가 준 자료가 반영됐는지 알아야 한다.`

/** 사용자가 볼 수 있는 형태로 오류를 만든다. 원문 오류는 서버 콘솔에만 남긴다. */
function fail(res, status, message) {
  res.statusCode = status
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.end(JSON.stringify({ message }))
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let body = ''
    /* 청크마다 String(chunk) 하면 멀티바이트 문자가 청크 경계에 걸릴 때 양쪽이 깨진다.
       Node 는 바이트 경계로 끊으므로 한글 한 글자가 반씩 나뉠 수 있고, 본문이 클수록
       확률이 오른다 — intake 가 역량 사전·파일명·md 첨부를 싣는 바로 그 경우다.
       setEncoding 은 부분 시퀀스를 버퍼링해 다음 청크와 이어 붙인다. */
    req.setEncoding('utf8')
    req.on('data', c => {
      body += c
      // PDF 를 base64 로 받으므로 넉넉히. Anthropic 요청 한도가 32MB 다.
      if (body.length > 24_000_000) {
        // 응답은 이미 나간다. 스트림을 안 끊으면 나머지 업로드를 계속 문자열에 쌓는다.
        req.destroy()
        reject(new Error('요청 본문이 너무 큽니다'))
      }
    })
    req.on('end', () => {
      try { resolve(body ? JSON.parse(body) : {}) } catch (e) { reject(e) }
    })
    req.on('error', reject)
  })
}

export default function aiDevServer() {
  return {
    name: 'careerfit-ai-dev-server',
    apply: 'serve',              // 빌드에는 절대 포함되지 않는다

    configureServer(server) {
      // process.env 에서 읽는다. VITE_ 접두사가 없으므로 클라이언트 번들에 노출되지 않는다.
      const apiKey = process.env.ANTHROPIC_API_KEY
      const client = apiKey ? new Anthropic({ apiKey }) : null

      if (!client) {
        server.config.logger.warn(
          '\n  [ai] ANTHROPIC_API_KEY 가 없습니다 — /api/ai/* 는 503 을 돌려줍니다.' +
          '\n      frontend/.env 에 ANTHROPIC_API_KEY=sk-ant-... 를 넣으면 켜집니다.' +
          '\n      (VITE_ 접두사를 붙이지 마세요. 붙이면 키가 브라우저로 새어 나갑니다.)\n')
      }

      /* 포폴 인테이크 — 링크는 web_fetch 로 모델이 직접 읽고, 첨부는 본문에 실어 보낸다. */
      server.middlewares.use('/api/ai/intake', async (req, res, next) => {
        if (req.method !== 'POST') return next()
        if (!client) {
          return fail(res, 503,
            'AI 키가 설정되지 않았습니다. frontend/.env 에 ANTHROPIC_API_KEY 를 넣고 dev 서버를 다시 시작하세요.')
        }

        try {
          const { links = [], files = [], competencies } = await readJson(req)
          const urls = links.map(s => String(s).trim()).filter(Boolean)
          if (!urls.length && !files.length) return fail(res, 400, '링크나 첨부파일이 필요합니다.')
          if (!Array.isArray(competencies) || !competencies.length) {
            return fail(res, 400, '역량 사전이 필요합니다.')
          }

          /* 사용자 메시지에 자료를 싣는다.
             web_fetch 는 **대화에 이미 있는 URL 만** 가져온다 — 여기 적힌 것 외에는
             모델이 어디에도 접속할 수 없다. 그게 이 도구를 쓰는 이유이기도 하다. */
          const content = []

          for (const f of files) {
            if (f.mediaType === 'application/pdf') {
              // PDF 는 네이티브로 읽는다. 텍스트 추출을 우리가 할 필요가 없다.
              content.push({
                type: 'document',
                source: { type: 'base64', media_type: 'application/pdf', data: f.data },
                title: f.name,
              })
            } else {
              // md · txt 는 그냥 글이다. base64 를 풀어 본문에 붙인다.
              const text = Buffer.from(f.data, 'base64').toString('utf8')
              content.push({ type: 'text', text: `# 첨부: ${f.name}\n\n${text}` })
            }
          }

          content.push({
            type: 'text',
            text: `# 역량 사전 (suggestedCompetencyIds 는 이 안에서만)
${competencies.map(c => `${c.id}. ${c.name} [${c.category}]`).join('\n')}

# 읽을 링크
${urls.length ? urls.map(u => `- ${u}`).join('\n') : '(없음)'}

${files.length ? `# 첨부 ${files.length}건은 위에 실려 있다.\n` : ''}
위 링크를 web_fetch 로 직접 읽고, 첨부와 함께 경험 후보를 뽑아라.`,
          })

          const t0 = Date.now()
          const messages = [{ role: 'user', content }]
          let response

          /* 서버 도구는 여러 턴이 돌 수 있다. pause_turn 이면 지금까지의 응답을
             그대로 붙여 이어 간다 — 내용을 요약해서 넣으면 도구 결과가 깨진다. */
          for (let turn = 0; turn < 8; turn++) {
            response = await client.messages.create({
              model: 'claude-opus-5',
              max_tokens: 32000,
              thinking: { type: 'adaptive' },
              system: INTAKE_SYSTEM,
              tools: [{
                type: 'web_fetch_20260318',
                name: 'web_fetch',
                /* 실패한 fetch 도 이 수에 들어간다. 저장소 하나를 주면 모델이
                   그 안의 파일로 더 들어가므로 링크 수보다 넉넉히 준다 —
                   fetch 결과에 있던 URL 은 다시 fetch 할 수 있다(그게 이 도구의 규칙이다). */
                max_uses: Math.min(24, urls.length * 4 + 4),
                /* 페이지 하나가 10kB 면 약 2,500 토큰, 500kB PDF 면 12만 토큰이다.
                   상한을 안 두면 포트폴리오 한 건이 컨텍스트를 통째로 먹는다. */
                max_content_tokens: 40000,
                /* 가져온 원문을 응답에 도로 실어 보낼 이유가 없다 — 우리는 후보만 쓴다.
                   출력 토큰이 그만큼 준다. */
                response_inclusion: 'excluded',
              }],
              messages,
              output_config: { format: zodOutputFormat(IntakeResult) },
            })
            if (response.stop_reason !== 'pause_turn') break
            messages.push({ role: 'assistant', content: response.content })
          }

          const parsed = response.parsed_output
          if (!parsed) return fail(res, 502, 'AI 응답을 해석하지 못했습니다. 다시 시도해 주세요.')

          /* 모델이 사전 밖의 id 를 냈으면 여기서 버린다.
             프롬프트로 지시했다고 검증을 생략하면 지어낸 id 가 매칭 점수로 흘러든다. */
          const known = new Set(competencies.map(c => c.id))
          let dropped = 0
          const candidates = parsed.candidates.map(c => {
            const ids = c.suggestedCompetencyIds.filter(id => known.has(id))
            dropped += c.suggestedCompetencyIds.length - ids.length
            return { ...c, suggestedCompetencyIds: ids, duplicateOfExperienceId: null }
          })
          if (dropped) server.config.logger.warn(`  [ai] 사전에 없는 competencyId ${dropped}건을 버렸습니다.`)

          /* web_fetch 오류는 예외가 아니라 200 안의 블록으로 온다.
             error_code 는 url_not_accessible(비공개·404) · url_not_allowed(robots.txt·사설망) ·
             unsupported_content_type(text·HTML·PDF 만 됨) · max_uses_exceeded 등이다. */
          const fetchErrors = (response.content || [])
            .filter(b => b.type === 'web_fetch_tool_result' && b.content?.type === 'web_fetch_tool_result_error')
            .map(b => b.content.error_code)

          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({
            candidates,
            unreadable: parsed.unreadable,
            _meta: {
              ms: Date.now() - t0,
              usage: response.usage,          // server_tool_use.web_fetch_requests 로 몇 번 읽었는지 보인다
              droppedUnknownIds: dropped,
              fetchErrors,
            },
          }))
        } catch (e) {
          server.config.logger.error(`  [ai] intake 실패: ${e.message}`)
          const status = e.status === 401 ? 401 : e.status === 429 ? 429 : 502
          fail(res, status,
            status === 401 ? 'API 키가 올바르지 않습니다.'
            : status === 429 ? '요청이 몰렸습니다. 잠시 후 다시 시도해 주세요.'
            : 'AI 호출에 실패했습니다.')
        }
      })

      server.middlewares.use('/api/ai/extract', async (req, res, next) => {
        if (req.method !== 'POST') return next()
        if (!client) {
          return fail(res, 503,
            'AI 키가 설정되지 않았습니다. frontend/.env 에 ANTHROPIC_API_KEY 를 넣고 dev 서버를 다시 시작하세요.')
        }

        try {
          // 클라이언트(api/real/ai.js)는 { text } 를 보낸다. 키가 어긋나 항상 400 이었다.
          const { text: postingText, competencies } = await readJson(req)
          if (!postingText?.trim()) return fail(res, 400, '공고 원문이 비어 있습니다.')
          if (!Array.isArray(competencies) || !competencies.length) {
            return fail(res, 400, '역량 사전이 필요합니다.')
          }

          const t0 = Date.now()
          const response = await client.messages.parse({
            model: 'claude-opus-5',
            max_tokens: 16000,
            thinking: { type: 'adaptive' },
            system: EXTRACT_SYSTEM,
            messages: [{
              role: 'user',
              content: `# 역량 사전 (이 안에서만 고른다)
${competencies.map(c => `${c.id}. ${c.name} [${c.category}] — ${(c.aliases || []).join(', ')}`).join('\n')}

# 채용공고 원문
${postingText}`,
            }],
            output_config: { format: zodOutputFormat(ExtractResult) },
          })

          const parsed = response.parsed_output
          if (!parsed) return fail(res, 502, 'AI 응답을 해석하지 못했습니다. 다시 시도해 주세요.')

          // 모델이 사전 밖의 id 를 냈을 경우를 여기서 막는다.
          // 프롬프트로 지시했다고 검증을 생략하면, 지어낸 id 가 매칭 점수로 흘러든다.
          const known = new Set(competencies.map(c => c.id))
          const required = parsed.required.filter(r => known.has(r.competencyId))
          const dropped = parsed.required.length - required.length
          if (dropped) {
            server.config.logger.warn(`  [ai] 사전에 없는 competencyId ${dropped}건을 버렸습니다.`)
          }

          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({
            ...parsed,
            required,
            _meta: {
              ms: Date.now() - t0,
              usage: response.usage,      // 개발 중 비용 감각을 잡으려고 같이 준다
              droppedUnknownIds: dropped,
            },
          }))
        } catch (e) {
          // 원문 오류는 콘솔에만. 응답에 담으면 내부 사정이 클라이언트로 샌다.
          server.config.logger.error(`  [ai] extract 실패: ${e.message}`)
          const status = e.status === 401 ? 401 : e.status === 429 ? 429 : 502
          fail(res, status,
            status === 401 ? 'API 키가 올바르지 않습니다.'
            : status === 429 ? '요청이 몰렸습니다. 잠시 후 다시 시도해 주세요.'
            : 'AI 호출에 실패했습니다.')
        }
      })
    },
  }
}
