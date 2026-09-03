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
  weight: z.number().describe('0.5~1.0. 공고가 이 역량을 얼마나 무겁게 요구하는가'),
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

/** 사용자가 볼 수 있는 형태로 오류를 만든다. 원문 오류는 서버 콘솔에만 남긴다. */
function fail(res, status, message) {
  res.statusCode = status
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.end(JSON.stringify({ message }))
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let body = ''
    req.on('data', c => {
      body += c
      if (body.length > 1_000_000) reject(new Error('요청 본문이 너무 큽니다'))
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

      server.middlewares.use('/api/ai/extract', async (req, res, next) => {
        if (req.method !== 'POST') return next()
        if (!client) {
          return fail(res, 503,
            'AI 키가 설정되지 않았습니다. frontend/.env 에 ANTHROPIC_API_KEY 를 넣고 dev 서버를 다시 시작하세요.')
        }

        try {
          const { postingText, competencies } = await readJson(req)
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
