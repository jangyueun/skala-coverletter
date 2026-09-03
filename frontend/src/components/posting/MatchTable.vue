<script setup>
import { ref } from 'vue'
import { strLabel } from '@/domain/matching.js'
import { catShort } from '@/domain/competency.js'

/* 목업의 구성을 그대로 가져왔다 — 표 + 누르면 펼쳐지는 근거.
   역량이 10~14개라 블록으로 쌓으면 화면이 길어져 비교가 안 된다.
   표는 같은 축(커버리지·가중치)이 세로로 정렬돼 한눈에 대조된다. */
defineProps({ rows: { type: Array, required: true } })

const open = ref(new Set())
function toggle(id) {
  const next = new Set(open.value)
  next.has(id) ? next.delete(id) : next.add(id)
  open.value = next
}
</script>

<template>
  <div class="tw">
    <table class="tb">
      <thead>
        <tr>
          <th>요구 역량</th>
          <th class="c">가중치</th>
          <th class="c">구분</th>
          <th class="cov">커버리지</th>
          <th class="c">근거</th>
        </tr>
      </thead>
      <tbody>
        <template v-for="r in rows" :key="r.competencyId">
          <tr class="mrow" :class="{ on: open.has(r.competencyId) }"
              tabindex="0" role="button" :aria-expanded="open.has(r.competencyId)"
              :aria-label="`${r.comp.name} 근거 경험 펼치기`"
              @click="toggle(r.competencyId)"
              @keydown.enter.prevent="toggle(r.competencyId)"
              @keydown.space.prevent="toggle(r.competencyId)">
            <td>
              <b class="nm" :class="{ gap: r.isGap }">{{ r.comp.name }}</b>
            </td>
            <!-- 가중치는 역량 바로 옆이다. 이 공고가 그 역량을 얼마나 무겁게 요구하는지가
                 역량 이름에 딸린 값이라, 표 끝에 두면 이름과 눈으로 이어 붙여야 했다. -->
            <td class="c num wt">{{ r.weight.toFixed(1) }}</td>
            <td class="c cat">{{ catShort(r.comp.category) }}</td>
            <td>
              <div class="mt">
                <div class="meter" :class="{ gap: r.isGap }">
                  <i :style="{ width: Math.round(r.score * 100) + '%' }" />
                </div>
                <span class="num pct" :class="{ gap: r.isGap }">{{ Math.round(r.score * 100) }}%</span>
              </div>
            </td>
            <td class="c">
              <span class="num evc" :class="{ gap: !r.evid.length }">
                {{ r.evid.length ? r.evid.length + '건' : '없음' }}
              </span>
            </td>
          </tr>

          <!-- 요구가 먼저, 답이 그다음. 공고가 이걸 왜 묻는지를 읽고
               내 경험이 그에 답하는 순서다. -->
          <tr v-if="open.has(r.competencyId)" class="mdet">
            <td colspan="5">
              <div class="d">
                <p class="dk">공고 근거</p>
                <p class="dv src">“{{ r.evidence }}”</p>
              </div>

              <div class="d">
                <p class="dk">내 경험</p>
                <div class="dv">
                  <template v-if="r.evid.length">
                    <p v-for="e in r.evid" :key="e.id" class="ev">
                      <b class="et">{{ e.title }}</b>
                      <i class="st">{{ strLabel(e.strength?.[r.competencyId] ?? 0.6) }}</i>
                      <span class="res">{{ e.result }}</span>
                    </p>
                  </template>
                  <p v-else class="none">
                    이 역량을 태그한 경험이 하나도 없습니다.
                    경험을 등록하거나, 기존 경험에 이 역량을 태그하세요.
                  </p>
                </div>
              </div>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
/* 좁은 화면에서 표가 페이지를 밀지 않게 자체 스크롤을 준다 */
.tw { overflow-x: auto; }
.tb { width: 100%; border-collapse: collapse; min-width: 520px; }

th {
  padding: 0 10px 9px; text-align: left;
  border-bottom: 2px solid var(--ink);
  font-family: var(--mono); font-size: var(--fs-3xs); font-weight: 500;
  letter-spacing: var(--track-label); text-transform: uppercase; color: var(--muted);
  white-space: nowrap;
}
th:first-child, td:first-child { padding-left: 2px; }
th:last-child,  td:last-child  { padding-right: 2px; }
.c { text-align: center; }
.r { text-align: right; }
.cov { width: 38%; }

td { padding: 11px 10px; border-bottom: 1px solid var(--line-soft); vertical-align: middle; }

.mrow { cursor: pointer; transition: background var(--release) linear; }
.mrow:hover { background: var(--panel-sunken); }
.mrow.on { background: var(--panel-sunken); }
.mrow.on td { border-bottom-color: transparent; }

.nm { font-size: var(--fs-sm); font-weight: 700; letter-spacing: var(--track-tight); }
.nm.gap { color: var(--gap); }
.cat { font-size: var(--fs-2xs); color: var(--muted); white-space: nowrap; }

.mt { display: flex; align-items: center; gap: 9px; }
.meter { flex: 1; min-width: 60px; height: 5px; background: var(--panel-sunken); border-radius: var(--pill); overflow: hidden; }
.mrow:hover .meter, .mrow.on .meter { background: var(--line); }
.meter i { display: block; height: 100%; background: var(--ink); }
.meter.gap i { background: var(--gap); }
.pct { font-size: var(--fs-xs); font-weight: 700; flex: none; min-width: 34px; text-align: right; }
.pct.gap { color: var(--gap); }

/* 표 셀의 근거 건수. 아래 펼친 근거 문단의 .ev 와 이름이 같아
   문단까지 600 으로 굵어지고 있었다. */
.evc { font-size: var(--fs-xs); font-weight: 600; }
.evc.gap { color: var(--gap); }
.wt { font-size: var(--fs-xs); color: var(--muted); white-space: nowrap; }

/* ── 펼친 근거 ─────────────────────────────────────────────
   앞의 모양이 안 좋았던 이유는 세 가지였다 —
   경험 제목이 위 역량 이름과 같은 굵기라 무엇이 상위인지 안 보였고,
   강/중/약이 알약으로 떠 있어 시선을 먼저 가져갔고,
   공고 근거가 회색으로 맨 아래에 붙어 읽는 순서가 거꾸로였다.

   왼쪽 홈통에 무엇인지 적고 오른쪽에 내용을 두는, 이 앱이 이미 쓰는 꼴로 맞춘다. */
.mdet td {
  background: var(--panel-sunken);
  padding: 2px 12px 14px; border-bottom: 1px solid var(--line-soft);
}
.d { display: grid; grid-template-columns: 62px minmax(0, 1fr); gap: 12px; align-items: baseline; }
.d + .d { margin-top: 9px; }
.dk {
  margin: 0; text-align: right; white-space: nowrap;
  font-family: var(--mono); font-size: var(--fs-3xs); font-weight: 500;
  letter-spacing: var(--track-label); color: var(--muted);
}
.dv { margin: 0; min-width: 0; }

/* 공고 원문은 인용이다. 기울여서 내 문장이 아님을 표시한다. */
.src { font-size: var(--fs-xs); color: var(--ink-2); font-style: italic; }

.ev { margin: 0; font-size: var(--fs-xs); line-height: 1.7; }
.ev + .ev { margin-top: 5px; }
/* 역량 이름(13px/700)보다 한 단 낮춘다 — 무엇에 딸린 것인지가 굵기로 보여야 한다 */
.et { font-weight: 600; }
/* 강도는 알약이 아니라 글자 뒤에 붙는 작은 표식이다 */
.st {
  font-family: var(--mono); font-style: normal; font-size: var(--fs-3xs); font-weight: 700;
  color: var(--muted); margin-left: 5px;
}
.res { color: var(--muted); margin-left: 8px; }
.none { margin: 0; font-size: var(--fs-xs); color: var(--gap); font-weight: 600; line-height: 1.6; }
</style>
