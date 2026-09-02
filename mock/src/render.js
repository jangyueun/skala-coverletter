
/* ============================================================
   렌더러 — 모든 화면은 위 DATA 객체 하나에서 그려진다.
   목업이지만 매칭 점수·커버리지·문장 점검은 실제로 계산한다.
   ============================================================ */

const $  = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;' }[c]));
const byId = id => DATA.competencies.find(c => c.id === id);
const P = () => DATA.postings.find(x => x.id === DATA.activePostingId);
const CAT = { TECH:'tech', SOFT:'soft', DOMAIN:'domain', VALUE:'value' };
const FLAB  = { situation:'S', task:'T', action:'A', result:'R' };
const FIELDS = ['situation', 'task', 'action', 'result'];   // starbar 순서 = S T A R
const CATLAB = { TECH:'기술', SOFT:'소프트', DOMAIN:'도메인', VALUE:'인재상' };

/* ---------- 화면 전환 ---------- */
function go(id){
  $$('.screen').forEach(s => s.classList.toggle('on', s.id === id));
  $$('.side .navitem').forEach(b => b.setAttribute('aria-current', b.dataset.go === id ? 'page' : 'false'));
  try { localStorage.setItem('cm.screen', id); } catch(e) {}
  window.scrollTo({ top:0, behavior:'instant' in window ? 'instant' : 'auto' });
}
$$('.side .navitem').forEach(b => b.addEventListener('click', () => go(b.dataset.go)));

/* ---------- 토글 ---------- */
$('#noteBtn').addEventListener('click', e => {
  const on = e.currentTarget.getAttribute('aria-pressed') !== 'true';
  e.currentTarget.setAttribute('aria-pressed', String(on));
  document.body.classList.toggle('notes', on);
});
$('#themeBtn').addEventListener('click', e => {
  const on = e.currentTarget.getAttribute('aria-pressed') !== 'true';
  e.currentTarget.setAttribute('aria-pressed', String(on));
  document.documentElement.setAttribute('data-theme', on ? 'dark' : 'light');
});


/* ============================================================
   파생 값 — 같은 사실을 두 곳에 저장하지 않는다.
   공고가 있으면 마감·매칭은 공고에서 계산하고, 문항 진행은 questions 에서 센다.
   ============================================================ */
const postingOf   = app => app && app.postingId ? DATA.postings.find(p => p.id === app.postingId) : null;
const appDday     = app => { const p = postingOf(app); return p ? dday(p.deadline) : app.dday; };
const appMatch    = app => { const p = postingOf(app); return p ? Math.round(computeMatch(p).overall * 100) : null; };
/* 공고 하나의 자소서 진행 — 카드·상세가 같은 함수를 쓴다 */
function essayProgress(posting){
  const app = appOfPosting(posting);
  if (!app) return { state:'NO_APP', label:'', done:0, total:0, ratio:0, thin:0 };
  const qs = DATA.questions.filter(q => q.applicationId === app.id);
  if (!qs.length) return { state:'NO_Q', label:'', done:0, total:0, ratio:0, thin:0, app };
  const done = qs.filter(q => (q.draft || '').trim()).length;
  const thin = qs.filter(q => { const L = (q.draft || '').trim().length; return L > 0 && L < q.charLimit * 0.8; }).length;
  return {
    state: done === qs.length ? 'DONE' : done === 0 ? 'EMPTY' : 'WRITING',
    thin,
    label: `${done} / ${qs.length}문항`,
    done, total: qs.length, ratio: done / qs.length,
    app,
  };
}
const appQs       = app => DATA.questions.filter(q => q.applicationId === app.id);
const appRemain   = app => { const qs = appQs(app); return qs.length ? `${qs.filter(q => !(q.draft||'').trim()).length} / ${qs.length}` : '— / —'; };
/* 이 경험이 실제로 쓰인 정도 — 관측 가능한 것만 센다.
   ① 근거로 걸려 있고  ② 본문이 실제로 작성된 답변  ③ 서로 다른 공고 수
   공고가 직무 단위라 같은 기업의 다른 직무에 쓰면 2건으로 센다.
   체크만 하고 안 쓴 문항은 세지 않는다. 제출 여부는 알 수 없으므로 보지 않는다. */
const usedIn = expId => {
  const qs = DATA.questions.filter(q =>
    (q.usedExperienceIds || []).includes(expId) && (q.draft || '').trim());
  const postingIds = new Set(qs.map(q => {
    const a = DATA.applications.find(x => x.id === q.applicationId);
    return a ? a.postingId : null;
  }).filter(Boolean));
  return { questions: qs.length, postings: postingIds.size };
};
const postingOfQ  = q => postingOf(DATA.applications.find(a => a.id === q.applicationId)) || P();
/* 오기 감지 대상 = 내가 아는 모든 기업 − 지금 쓰고 있는 그 기업.
   고정 배열로 두면 공고를 바꿀 때마다 어긋난다. */
const appOfPosting = posting => DATA.applications.find(a => a.postingId === posting.id) || null;
const rivalsFor   = posting => [...new Set([
  ...DATA.postings.map(x => x.company),
  ...DATA.applications.map(a => a.company),
])].filter(c => c && c !== posting.company);

/* ============================================================
   S1 · 경험 라이브러리
   ============================================================ */
let s1filter = null;

function renderS1(){
  const exps = DATA.experiences;
  $('#s1cnt').textContent = exps.length;
  const tagged = new Set(exps.flatMap(e => e.competencyIds));
  $('#s1cov').textContent = tagged.size;

  $('#s1chips').innerHTML = DATA.competencies
    .filter(c => tagged.has(c.id))
    .map(c => `<button class="chip ${CAT[c.category]}" data-cid="${c.id}" aria-pressed="${s1filter === c.id}">${esc(c.name)}<span style="opacity:.6">${exps.filter(e => e.competencyIds.includes(c.id)).length}</span></button>`)
    .join('');
  $$('#s1chips .chip').forEach(b => b.addEventListener('click', () => {
    const id = +b.dataset.cid;
    s1filter = s1filter === id ? null : id;
    renderS1();
  }));

  const shown = s1filter ? exps.filter(e => e.competencyIds.includes(s1filter)) : exps;
  $('#s1filtern').textContent = s1filter ? `${byId(s1filter).name} · ${shown.length}건` : `전체 ${exps.length}건`;

  $('#s1list').innerHTML = shown.map(e => `
    <article class="card" data-note="experience">
      <div class="cardhead">
        <h3>${esc(e.title)}</h3>
        <span class="n">${esc(e.period)}</span>
        <button class="btn sm" data-edit="${e.id}" style="margin-left:9px" data-note="PUT /api/experiences/${e.id}">수정</button>
      </div>
      <div class="cardbody">
        <div style="display:flex; gap:6px; margin-bottom:12px">
          <span class="pill mut">${esc(e.category)}</span>
          ${e.source === 'AI_INTAKE' ? (() => {
            const ed = e.editedFields || [], refs = e.evidenceRefs || [];
            // AI 문장이 하나도 안 남았으면 근거를 달지 않는다. 통째로 다시 쓴 문장에
            // 'PR #412 · merged' 를 붙이면 근거가 아무 데나 찍히는 도장이 된다.
            if (!refs.length) return `<span class="pill mut" title="포폴 인테이크에서 시작했지만 AI 가 쓴 문장은 남아 있지 않습니다">포폴 인테이크 · 본인이 다시 씀</span>`;
            const t = refs.join(', ') + (ed.length ? ` · ${ed.map(f => FLAB[f]).join('·')} 문장은 본인이 고쳤습니다` : '');
            return `<span class="pill info" title="${esc(t)}">포폴 인테이크${ed.length ? ' · 일부 수정' : ''}</span>`;
          })() : ''}
          ${(() => { const u = usedIn(e.id); return u.postings
            ? `<span class="pill acc" title="본문이 작성된 답변에 근거로 걸린 공고 수. 공고는 직무 단위라 같은 기업의 다른 직무는 따로 센다.">자소서 ${u.postings}개 공고에 사용</span>`
            : ''; })()}
        </div>
        <dl style="display:grid; grid-template-columns:auto 1fr; gap:5px 12px; font-size:12.5px; margin:0">
          <dt style="color:var(--faint); font-weight:600">S</dt><dd style="margin:0">${esc(e.situation)}</dd>
          <dt style="color:var(--faint); font-weight:600">T</dt><dd style="margin:0">${esc(e.task)}</dd>
          <dt style="color:var(--faint); font-weight:600">A</dt><dd style="margin:0">${esc(e.action)}</dd>
          <dt style="color:var(--matched); font-weight:600">R</dt><dd style="margin:0; color:var(--ink)"><b>${esc(e.result)}</b></dd>
        </dl>
        <div class="chips" style="margin-top:14px; padding-top:12px; border-top:1px solid var(--border)">
          ${e.competencyIds.map(id => { const c = byId(id); return c ? `<span class="chip ${CAT[c.category]}">${esc(c.name)}</span>` : ''; }).join('')}
        </div>
      </div>
    </article>`).join('') || '<p class="empty">이 역량이 태그된 경험이 없습니다</p>';

  $$('#s1list [data-edit]').forEach(b => b.addEventListener('click', () => openExp(+b.dataset.edit)));
}
$('#s1reset').addEventListener('click', () => { s1filter = null; renderS1(); });

/* ============================================================
   S2 · 공고 분석 — 202 + 폴링을 실제로 흉내낸다
   ============================================================ */
let s2done = false;

function logLine(el, cls, text){
  const d0 = new Date();
  const t = [d0.getHours(), d0.getMinutes(), d0.getSeconds()].map(v => String(v).padStart(2,'0')).join(':');
  const d = document.createElement('div');
  d.innerHTML = `<span class="t">${t}</span> <span class="${cls}">${esc(text)}</span>`;
  el.appendChild(d); el.scrollTop = el.scrollHeight;
}

const TODAY = new Date('2026-09-02T00:00:00');
const dday = iso => Math.round((new Date(iso + 'T00:00:00') - TODAY) / 86400000);


/* ============================================================
   즐겨찾기 — 실제 구현에서는 bookmark 테이블(N:M · user ↔ posting).
   목업에서는 브라우저에 남긴다.
   ============================================================ */
const BM_KEY = 'cm.bookmarks';
let bookmarks = new Set();
try { bookmarks = new Set(JSON.parse(localStorage.getItem(BM_KEY) || '[]')); } catch(e) {}

function saveBm(){ try { localStorage.setItem(BM_KEY, JSON.stringify([...bookmarks])); } catch(e) {} }
function isBm(id){ return bookmarks.has(id); }
function toggleBm(id){
  bookmarks.has(id) ? bookmarks.delete(id) : bookmarks.add(id);
  saveBm();
  renderHome();
  if ($('#detail').classList.contains('on')) renderDetail();
  toast(bookmarks.has(id) ? '즐겨찾기에 담았습니다' : '즐겨찾기에서 뺐습니다');
}
const bmIcon = on => `<svg viewBox="0 0 16 16" width="12" height="12" aria-hidden="true"><path d="M3.6 1.6h8.8v12.8L8 11.2 3.6 14.4z" fill="${on ? 'currentColor' : 'none'}" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/></svg>`;
const bmBtn = (id, label) => `<button class="bm" data-bm="${id}" aria-pressed="${isBm(id)}" aria-label="${isBm(id) ? '즐겨찾기 해제' : '즐겨찾기 추가'}" data-note="bookmark (N:M)">${bmIcon(isBm(id))}${label ? (isBm(id) ? '즐겨찾기됨' : '즐겨찾기') : ''}</button>`;
function bindBm(root){
  $$(`${root} [data-bm]`).forEach(b => b.addEventListener('click', e => { e.stopPropagation(); toggleBm(+b.dataset.bm); }));
}

/* ============================================================
   공고 상세 — 공고 내용 · 매칭 · 자소서를 한 곳에
   ============================================================ */
let dtTab = 'post';

function openDetail(id, tab){
  DATA.activePostingId = id;
  dtTab = tab || 'post';
  renderDetail();
  go('detail');
}

function renderDetail(){
  const p = P(), m = computeMatch(p), app = appOfPosting(p), ep = essayProgress(p);
  const gaps = m.rows.filter(r => r.isGap).length;
  const mc = m.overall >= SCORE.RECOMMEND ? 'var(--matched)' : m.overall >= SCORE.WEAK ? 'var(--info)' : 'var(--gap)';
  const d = dday(p.deadline);

  $('#dtHead').className = 'card pad dthead';
  $('#dtHead').innerHTML = `
    <span class="jc-logo" aria-hidden="true">${esc(p.company.slice(0,1))}</span>
    <div class="meta">
      <div class="co">${esc(p.company)}</div>
      <h2 id="dth">${esc(p.position)}</h2>
      <div style="font-size:11.5px; color:var(--faint); margin-top:4px; font-family:var(--mono)">${esc(p.sourceUrl || '직접 입력')}</div>
    </div>
    <div class="nums">
      <div class="num"><b style="color:${mc}">${Math.round(m.overall*100)}%</b><span>매칭</span></div>
      <div class="num"><b style="color:${gaps ? 'var(--gap)' : 'var(--matched)'}">${gaps}</b><span>갭</span></div>
      <div class="num"><b style="color:${ep.total && ep.state === 'DONE' ? 'var(--matched)' : 'var(--ink)'}">${ep.total ? `${ep.done}<span style="font-size:13px; color:var(--faint)">/${ep.total}</span>` : '—'}</b><span>자소서</span></div>
      <div class="num"><b style="color:${d <= 7 ? 'var(--gap)' : 'var(--ink)'}">D-${d}</b><span>${esc(p.deadline)}</span></div>
      <div class="num" style="align-self:center">${bmBtn(p.id, true)}</div>
    </div>
    ${(() => {
      const a = assess(p), st = assessState[p.id] || { state:'QUEUED' }, busy = st.state !== 'FRESH';
      const vc = { RECOMMEND:'var(--matched)', CONDITIONAL:'var(--gap)', HOLD:'var(--muted)' }[a.verdict];
      const vl = { RECOMMEND:'지원 권장', CONDITIONAL:'조건부 지원', HOLD:'보류 권장' }[a.verdict];
      return `<div style="flex-basis:100%; display:flex; align-items:center; gap:10px; flex-wrap:wrap; padding-top:14px; margin-top:2px; border-top:1px solid var(--border); ${busy ? 'opacity:.6' : ''}">
        <span class="pill" style="color:${vc}">${vl}</span>
        <span style="font-size:12.5px; color:var(--ink)">${esc(a.headline)}</span>
        ${busy ? `<span class="pill mut">${ASSESS_LABEL[st.state]}</span>` : ''}
      </div>`;
    })()}`;
  bindBm('#dtHead');

  $('#dtEssayBadge').innerHTML = ep.total
    ? `<span class="pill ${ep.state === 'DONE' ? 'ok' : 'mut'}">${ep.done}/${ep.total}</span>`
    : ep.state === 'NO_APP' ? `<span class="pill mut">미지원</span>`
    : p.questionsFromServer === false ? `<span class="pill warn">문항 없음</span>` : '';

  renderJD();
  bindRelated();
  renderS3();
  renderEssay();
  paintDtTab();
}

function bindRelated(){
  $$('#dtPost [data-rel]').forEach(b => b.addEventListener('click', () => openDetail(+b.dataset.rel, 'post')));
}

function paintDtTab(){
  $$('#dtTabs .tab').forEach(t => t.classList.toggle('on', t.dataset.dt === dtTab));
  $('#dtPost').hidden  = dtTab !== 'post';
  $('#s3body').hidden  = dtTab !== 'match';
  $('#dtEssay').hidden = dtTab !== 'essay';
}
$$('#dtTabs .tab').forEach(t => t.addEventListener('click', () => { dtTab = t.dataset.dt; paintDtTab(); }));
$('#dtBack').addEventListener('click', () => go('home'));

/* ---- 공고 내용 — 원문을 섹션으로 파싱해 보여준다 ---- */
function parseJD(raw){
  const out = []; let cur = null, head = [];
  raw.split('\n').forEach(ln => {
    const t = ln.trim();
    if (!t) return;
    if (t.startsWith('■')){ cur = { title: t.replace(/^■\s*/, ''), items: [] }; out.push(cur); }
    else if (t.startsWith('·') && cur) cur.items.push(t.replace(/^·\s*/, ''));
    else if (!cur) head.push(t);
  });
  return { head, sections: out };
}


/* 관련 공고 — 같은 기업 다른 직무 / 다른 기업 같은 직무.
   role 은 수집 시 분류하는 직무 계열 taxonomy 다 (기업이 쓰는 직무명은 제각각이라
   문자열로는 못 묶는다 — "서버 개발"과 "백엔드 엔지니어"는 같은 계열이다). */
const ROLE_LABEL = { BACKEND:'백엔드', FRONTEND:'프론트엔드', FULLSTACK:'풀스택', PLATFORM:'플랫폼·인프라' };

function relatedHtml(p){
  const live = DATA.postings.filter(x => x.id !== p.id && dday(x.deadline) >= 0);
  const sameCo   = live.filter(x => x.company === p.company);
  const sameRole = live.filter(x => x.role === p.role && x.company !== p.company);
  if (!sameCo.length && !sameRole.length) return '';

  const row = x => {
    const m = Math.round(computeMatch(x).overall * 100);
    const mc = m >= 85 ? 'var(--matched)' : m >= 70 ? 'var(--info)' : 'var(--gap)';
    return `<button class="btn rel-post" data-rel="${x.id}" style="justify-content:flex-start; gap:10px; padding:9px 12px">
      <span class="jc-logo" style="width:28px; height:28px; font-size:12px">${esc(x.company.slice(0,1))}</span>
      <span style="text-align:left; line-height:1.4">
        <span style="display:block; font-size:12.5px; color:var(--ink)">${esc(x.position)}</span>
        <span style="display:block; font-size:11px; color:var(--muted)">${esc(x.company)} · D-${dday(x.deadline)}</span>
      </span>
      <span class="mono" style="margin-left:auto; font-weight:600; color:${mc}">${m}%</span>
    </button>`;
  };

  return `<div class="card" data-note="GET /api/postings?company= / ?role=">
    <div class="cardhead"><h3>관련 공고</h3><span class="n">지금 매칭 ${Math.round(computeMatch(p).overall*100)}%</span></div>
    <div class="cardbody" style="display:grid; gap:16px">
      ${sameCo.length ? `<div>
        <div style="font-size:11.5px; font-weight:600; color:var(--muted); margin-bottom:8px">같은 기업 다른 직무</div>
        <div style="display:grid; gap:7px">${sameCo.map(row).join('')}</div>
      </div>` : ''}
      ${sameRole.length ? `<div>
        <div style="font-size:11.5px; font-weight:600; color:var(--muted); margin-bottom:8px">다른 기업 같은 직무 · ${esc(ROLE_LABEL[p.role] || p.role)}</div>
        <div style="display:grid; gap:7px">${sameRole.map(row).join('')}</div>
      </div>` : ''}
      <p style="font-size:11.5px; color:var(--muted); border-top:1px dashed var(--border); padding-top:10px">
        같은 직무라도 회사마다 요구 역량과 가중치가 달라 매칭이 갈린다. 공고는 기업이 아니라 <b>직무 단위 엔티티</b>다.
      </p>
    </div>
  </div>`;
}

function renderJD(){
  const p = P(), { head, sections } = parseJD(p.rawText);
  $('#dtPost').innerHTML = `
    <div class="card" style="margin-bottom:16px" data-note="job_posting.raw_text">
      <div class="cardhead"><h3>직무 내용</h3><span class="n">${p.source === 'CRAWLED' ? '자동 수집' : '직접 입력'}</span></div>
      <div class="cardbody">
        ${head.length ? `<p style="font-size:12.5px; color:var(--muted); margin-bottom:16px">${esc(head.join(' '))}</p>` : ''}
        ${sections.map(sec => `
          <dl class="jd">
            <dt>${esc(sec.title)}</dt>
            <dd><ul>${sec.items.map(i => `<li>${esc(i)}</li>`).join('')}</ul></dd>
          </dl>`).join('')}
      </div>
    </div>

    <div class="card" style="margin-bottom:16px" data-note="posting_competency">
      <div class="cardhead">
        <h3>요구 역량</h3>
        <span class="n">${p.required.length}개</span>
        <span class="pill acc" style="margin-left:8px">AX-1 · 수집 배치가 추출</span>
      </div>
      <div class="cardbody">
        <div class="chips">
          ${[...p.required].sort((a, b) => b.weight - a.weight).map(r => {
            const c = byId(r.competencyId);
            return `<span class="chip ${CAT[c.category]}" title="${esc(r.evidence)}">${esc(c.name)}</span>`;
          }).join('')}
        </div>
        <p style="font-size:11px; color:var(--muted); margin-top:10px">
          공고 원문에서 뽑은 역량이다. 가중치와 내 경험의 커버리지는 <b>매칭</b> 탭에서 본다.
        </p>

        ${p.newCompetencies.length ? `
          <div style="margin-top:14px; padding-top:12px; border-top:1px dashed var(--border)" data-note="newCompetencies">
            <div style="font-size:11.5px; font-weight:600; color:var(--gap); margin-bottom:7px">
              역량 사전에 없는 표현 ${p.newCompetencies.length}개 · 승인 필요
            </div>
            <div class="chips">${p.newCompetencies.map(n => `<span class="chip value">${esc(n)}</span>`).join('')}</div>
            <p style="font-size:11px; color:var(--muted); margin-top:8px">AI가 사전을 마음대로 늘리지 못하게 격리한다. 사람이 승인해야 <code>competency</code> 마스터에 들어간다.</p>
          </div>` : ''}
      </div>
    </div>

    ${relatedHtml(p)}

    <div class="noteonly" style="margin-top:12px">
      <div style="font-size:11px; color:var(--muted); margin-bottom:6px">수집 배치 기록 · 읽기 전용</div>
      <div class="netlog" id="s2log">
        <div><span class="t">${esc(p.collectedAt || '—')}</span> <span class="m">crawl</span> <span class="t">${esc(p.sourceUrl || '직접 입력')}</span></div>
        <div><span class="t">${esc(p.collectedAt || '—')}</span> <span class="m">POST /internal/ai/extract</span> <span class="t">{ postingId: ${p.id} }</span></div>
        <div><span class="t">${esc(p.collectedAt || '—')}</span> <span class="s2">200</span> <span class="t">{ required: ${p.required.length}, newCompetencies: ${p.newCompetencies.length}, latencyMs: 1240 }</span></div>
        <div><span class="t">${esc(p.collectedAt || '—')}</span> <span class="m">UPSERT</span> <span class="t">posting_competency × ${p.required.length}</span></div>
      </div>
    </div>`;
}

const sleep = ms => new Promise(r => setTimeout(r, ms));


/* ============================================================
   S3 · 매칭 대시보드 — 커버리지를 실제로 계산한다
   ============================================================ */
/* 점수 임계값은 여기 한 곳에만 있다 — 화면·판정·문구가 전부 이 상수를 참조한다 */
const SCORE = {
  GAP: 0.45,          // 이 아래면 갭으로 본다
  WEAK: 0.70,         // 근거가 얕다고 표시하는 선
  STRONG: 0.90,       // 강점으로 문장에 인용하는 선
  RECOMMEND: 0.85,    // 지원 권장 (+ 갭 0)
  CONDITIONAL: 0.62,  // 조건부 지원
  DEFAULT_STRENGTH: 0.60,
  PICK_STRENGTH: 0.70,
};

function computeMatch(post){
  const pp = post || P();
  const rows = pp.required.map(r => {
    const evid = DATA.experiences.filter(e => e.competencyIds.includes(r.competencyId));
    const strength = evid.reduce((a, e) => a + (e.strength?.[r.competencyId] ?? SCORE.DEFAULT_STRENGTH), 0);
    const score = Math.min(1, strength);
    return { ...r, comp: byId(r.competencyId), evid, score, isGap: evid.length === 0 || score < SCORE.GAP };
  });
  const wsum = rows.reduce((a, r) => a + r.weight, 0);
  const overall = wsum ? rows.reduce((a, r) => a + r.weight * r.score, 0) / wsum : 0;
  return { rows, overall };
}


/* ============================================================
   AX-2 · 평가 요약
   숫자만으로는 "그래서 지원해도 되나"에 답이 안 된다.
   요구 역량 + 내 경험을 넣고 판정 · 요약 · 다음 할 일을 받는다.

   목업에서는 computeMatch 결과로 문장을 조립한다.
   실제 구현에서는 아래 buildAssessInput() 이 만드는 JSON 을 그대로
   프롬프트에 넣고, 같은 모양의 응답을 받아 이 카드에 렌더한다.
   ============================================================ */
const EFFORT = { LOW:'낮음', MID:'중간', HIGH:'높음' };
const expSignature = () => DATA.experiences.map(e => e.id).sort((a,b)=>a-b).join(',');

/* 평가는 사용자가 버튼으로 돌리는 게 아니다.
   경험 라이브러리가 바뀌면 이벤트가 발행되고, 서버가 활성 공고들의 평가를 다시 계산해 둔다.
   화면은 저장된 결과를 읽기만 한다. */
let assessState = {};        // { postingId: { state:'FRESH'|'QUEUED'|'RUNNING', at, sig } }
let assessLog = [];          // 설계 주석 모드에서 보여줄 이벤트 기록
const ASSESS_LABEL = { FRESH:'평가 최신', QUEUED:'재계산 대기', RUNNING:'재계산 중' };

function stampNow(){
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ` +
         [d.getHours(), d.getMinutes(), d.getSeconds()].map(v => String(v).padStart(2,'0')).join(':');
}

/* 부트 시점에는 이미 서버가 계산해 둔 상태다 — 사용자가 처음 열어도 평가가 비어 있지 않다. */
function seedAssess(){
  DATA.postings.forEach(p => assessState[p.id] = { state:'FRESH', at:'2026-09-02 06:10', sig: expSignature() });
  assessLog = [{ t:'2026-09-02 06:10', k:'BATCH', m:`nightly reassess × ${DATA.postings.length}` }];
}

/* 경험 라이브러리 변경 → 이벤트 → 활성 공고 재평가 */
function onExperienceChanged(kind, ids){
  const live = DATA.postings.filter(p => dday(p.deadline) >= 0);
  const t = stampNow();
  assessLog.unshift({ t, k:'EVENT',   m:`${kind} { experienceIds: [${ids.join(', ')}] }` });
  assessLog.unshift({ t, k:'ENQUEUE', m:`reassess × ${live.length} (활성 공고)` });
  live.forEach(p => assessState[p.id] = { ...assessState[p.id], state:'QUEUED' });
  repaintAssess();

  setTimeout(() => {
    live.forEach(p => assessState[p.id] = { ...assessState[p.id], state:'RUNNING' });
    assessLog.unshift({ t: stampNow(), k:'RUN', m:`POST /internal/ai/match × ${live.length}` });
    repaintAssess();
  }, 700);

  setTimeout(() => {
    const at = stampNow(), sig = expSignature();
    live.forEach(p => assessState[p.id] = { state:'FRESH', at, sig });
    assessLog.unshift({ t: at, k:'DONE', m:`upsert assessment × ${live.length}` });
    repaintAssess();
    const cur = P();
    const now = Math.round(computeMatch(cur).overall * 100);
    toast(`평가 갱신 완료 · 공고 ${live.length}건 · ${cur.company} ${now}%`);
  }, 2400);
}

function repaintAssess(){
  renderHome();
  if ($('#detail').classList.contains('on')) renderDetail();
}

/* 실제 구현에서 프롬프트에 들어갈 입력 — 설계 주석 모드에서 그대로 보여준다 */
function buildAssessInput(p){
  const m = computeMatch(p);
  return {
    posting: { id: p.id, company: p.company, position: p.position, deadlineDday: dday(p.deadline) },
    required: p.required.map(r => ({ competency: byId(r.competencyId).name, weight: r.weight, evidence: r.evidence })),
    experiences: DATA.experiences.map(e => ({
      id: e.id, title: e.title, result: e.result,
      competencies: e.competencyIds.map(id => byId(id).name),
    })),
    coverage: m.rows.map(r => ({ competency: r.comp.name, score: +r.score.toFixed(2), evidenceIds: r.evid.map(e => e.id) })),
  };
}

function assess(p){
  const m = computeMatch(p);
  const gaps = m.rows.filter(r => r.isGap).sort((a, b) => b.weight - a.weight);
  const strong = m.rows.filter(r => !r.isGap && r.score >= SCORE.STRONG).sort((a, b) => b.weight - a.weight);
  const weak = m.rows.filter(r => !r.isGap && r.score < SCORE.WEAK).sort((a, b) => b.weight - a.weight);
  const d = dday(p.deadline);
  const app = appOfPosting(p);
  const qs = app ? appQs(app) : [];
  const unwritten = qs.filter(q => !(q.draft || '').trim()).length;
  const valueGaps = gaps.filter(g => g.comp.category === 'VALUE');

  const verdict = (m.overall >= SCORE.RECOMMEND && !gaps.length) ? 'RECOMMEND'
                : (m.overall >= SCORE.CONDITIONAL) ? 'CONDITIONAL' : 'HOLD';

  const headline =
    verdict === 'RECOMMEND' ? '요구 역량을 모두 덮습니다 — 우선순위를 높이세요'
    : verdict === 'HOLD' ? `요구 역량 ${gaps.length}개가 비어 있어 우선순위를 낮추는 편이 낫습니다`
    : valueGaps.length ? '기술 역량은 채웠지만 인재상 키워드가 비어 있습니다'
    : `기술 역량은 대체로 맞지만 ${gaps.map(g => g.comp.name).join('·')} 이(가) 비어 있습니다`;

  /* --- 요약 문장 조립 --- */
  const S = [];
  S.push(`요구 역량 ${m.rows.length}개 중 ${m.rows.length - gaps.length}개를 덮어 매칭 ${Math.round(m.overall*100)}%입니다.`);
  if (strong.length){
    const s0 = strong[0];
    const ev = s0.evid.slice(0, 2).map(e => `“${e.title}”`).join('과 ');
    S.push(`${s0.comp.name}은 ${ev}${s0.evid.length > 2 ? ' 등' : ''}이 뒷받침합니다.`);
  }
  if (strong.length > 1) S.push(`${strong.slice(1, 3).map(r => r.comp.name).join('·')}도 근거가 있습니다.`);
  if (gaps.length) S.push(`반면 ${gaps.map(g => `${g.comp.name}(가중치 ${g.weight.toFixed(1)})`).join(', ')}은 이를 증명할 경험이 없습니다.`);
  if (weak.length) S.push(`${weak.map(w => w.comp.name).join('·')}은 태그는 되어 있으나 근거가 얕습니다.`);
  S.push(d <= 10
    ? `마감이 ${d}일 남아, 새 경험을 만들기보다 기존 경험을 이 공고의 언어로 다시 서술하는 편이 현실적입니다.`
    : `마감까지 ${d}일 남아 갭을 메울 경험을 새로 만들 여유가 있습니다.`);

  /* --- 다음 할 일 --- */
  const actions = [];
  gaps.forEach(g => actions.push({
    effort: g.comp.category === 'VALUE' ? 'LOW' : 'MID',
    title: g.suggestion || `${g.comp.name}을 증명할 경험을 추가하거나, 기존 경험을 그 관점으로 다시 서술하세요.`,
    tag: g.comp.name,
  }));
  weak.forEach(w => actions.push({
    effort: 'LOW',
    title: `${w.comp.name}: “${w.evid[0]?.title || ''}”에 이 역량이 드러나는 행동과 수치를 보강하세요.`,
    tag: w.comp.name,
  }));
  if (!app) actions.push({ effort:'LOW', title:'이 공고로 지원서를 만들어야 자소서 문항과 답변 버전을 관리할 수 있습니다.', tag:'지원서' });
  else if (!qs.length) actions.push({ effort:'LOW', title:'서버에 이 공고의 자소서 문항이 없습니다. 채용 사이트에서 확인해 직접 등록하세요.', tag:'문항' });
  else if (unwritten) actions.push({ effort:'MID', title:`자소서 ${qs.length}문항 중 ${unwritten}문항이 비어 있습니다.`, tag:'자소서' });

  return { verdict, headline, summary: S.join(' '), actions: actions.slice(0, 4), overall: m.overall };
}

function assessCard(p){
  const a = assess(p);
  const st = assessState[p.id] || { state:'QUEUED' };
  const busy = st.state !== 'FRESH';
  const vc = { RECOMMEND:'var(--matched)', CONDITIONAL:'var(--gap)', HOLD:'var(--muted)' }[a.verdict];
  const vl = { RECOMMEND:'지원 권장', CONDITIONAL:'조건부 지원', HOLD:'보류 권장' }[a.verdict];

  return `
    <div class="card" style="margin-bottom:18px; border-color:${busy ? 'var(--border)' : vc}" data-note="assessment 테이블 · 이벤트로 갱신">
      <div class="cardhead" style="background:var(--surface-2)">
        <h3>평가 요약</h3>
        <span class="pill acc">AX-2 · temperature 0.2</span>
        <span class="n" style="margin-left:auto; display:flex; align-items:center; gap:6px">
          ${busy ? '<span class="spin" style="border-color:var(--muted); border-right-color:transparent"></span>' : ''}
          ${ASSESS_LABEL[st.state]}${st.at && !busy ? ` · ${esc(st.at)}` : ''}
        </span>
      </div>
      <div class="cardbody" id="asBody">
        ${`
        <div style="display:flex; align-items:center; gap:10px; margin-bottom:11px; flex-wrap:wrap">
          <span class="pill" style="color:${vc}; font-size:12px; padding:3px 10px">${vl}</span>
          <b style="color:var(--ink); font-size:14px">${esc(a.headline)}</b>
        </div>
        <p style="font-size:13px; line-height:1.8; max-width:72ch; ${busy ? 'opacity:.55' : ''}">${esc(a.summary)}</p>

        <div style="margin-top:16px; padding-top:14px; border-top:1px solid var(--border)">
          <div style="font-size:11px; font-weight:600; letter-spacing:.08em; text-transform:uppercase; color:var(--muted); margin-bottom:9px">지금 할 일</div>
          <ol style="display:flex; flex-direction:column; gap:8px; padding-left:0; list-style:none; margin:0">
            ${a.actions.map((x, i) => `
              <li style="display:flex; gap:9px; align-items:flex-start; font-size:12.5px">
                <span class="mono" style="color:var(--faint); flex:none">${i + 1}</span>
                <span class="pill ${x.effort === 'LOW' ? 'ok' : x.effort === 'MID' ? 'warn' : 'mut'}" style="flex:none">${EFFORT[x.effort]}</span>
                <span>${esc(x.title)}</span>
              </li>`).join('')}
          </ol>
        </div>

        ${busy ? `<div style="margin-top:12px; font-size:12px; color:var(--muted)">경험 라이브러리가 바뀌어 서버가 이 공고의 평가를 다시 계산하고 있습니다. 위 내용은 갱신 전 결과입니다.</div>` : ''}
        `}
        <div class="noteonly" style="margin-top:14px">
          <div style="display:grid; gap:12px">
          <div>
            <div style="font-size:11px; color:var(--muted); margin-bottom:6px">평가 파이프라인 · 사용자 트리거 없음</div>
            <div class="netlog">${assessLog.slice(0, 6).map(l =>
              `<div><span class="t">${esc(l.t)}</span> <span class="${l.k === 'DONE' ? 's2' : l.k === 'EVENT' ? 's1' : 'm'}">${esc(l.k)}</span> <span class="t">${esc(l.m)}</span></div>`).join('')}</div>
          </div>
          <div>
            <div style="font-size:11px; color:var(--muted); margin-bottom:6px">프롬프트에 들어가는 입력</div>
            <pre class="code" style="max-height:170px">${esc(JSON.stringify(buildAssessInput(p), null, 1))}</pre>
          </div>
          </div>
        </div>
      </div>
    </div>`;
}

function renderS3(){
  const { rows, overall } = computeMatch();
  const gaps = rows.filter(r => r.isGap);
  const covered = rows.length - gaps.length;

  $('#s3body').innerHTML = `
    ${assessCard(P())}

    <div class="grid g3" style="margin-bottom:18px">
      <div class="card pad" data-note="match_result.overall_score">
        <div class="stat"><span class="v" style="color:${overall >= .7 ? 'var(--matched)' : 'var(--gap)'}">${(overall*100).toFixed(0)}<span style="font-size:16px">%</span></span><span class="l">전체 매칭 스코어 · 가중 평균</span></div>
      </div>
      <div class="card pad"><div class="stat"><span class="v" style="color:var(--matched)">${covered}<span style="font-size:16px; color:var(--faint)"> / ${rows.length}</span></span><span class="l">내 경험이 덮은 요구 역량</span></div></div>
      <div class="card pad"><div class="stat"><span class="v" style="color:var(--gap)">${gaps.length}</span><span class="l">비어 있는 역량 · 갭</span></div></div>
    </div>

    ${gaps.length ? `
    <div class="card" style="margin-bottom:18px; border-color:var(--gap)" data-note="match_detail.status = GAP">
      <div class="cardhead" style="background:var(--gap-soft)">
        <h3 style="color:var(--gap)">지원 전에 메워야 할 갭 ${gaps.length}개</h3>
        <span class="n">가중치 높은 순</span>
      </div>
      <div class="cardbody" style="display:flex; flex-direction:column; gap:12px">
        ${gaps.sort((a,b) => b.weight - a.weight).map(g => `
          <div style="display:flex; gap:12px; align-items:flex-start">
            <span class="chip on-gap" style="flex:none">${esc(g.comp.name)}</span>
            <div style="font-size:12.5px">
              <div style="color:var(--ink)">${esc(g.suggestion || '이 역량을 보여줄 경험을 추가하거나, 기존 경험을 이 관점에서 다시 서술하세요.')}</div>
              <div style="color:var(--faint); font-size:11.5px; margin-top:3px">공고 근거 · “${esc(g.evidence)}”</div>
            </div>
          </div>`).join('')}
      </div>
    </div>` : ''}

    <div class="card" data-note="GET /applications/12/ai/match">
      <div class="cardhead"><h3>요구 역량별 커버리지</h3><span class="n">역량을 누르면 근거 경험이 펼쳐집니다</span></div>
      <div class="tw">
        <table class="tb">
          <thead><tr><th>요구 역량</th><th>구분</th><th style="width:34%">커버리지</th><th>근거 경험</th><th>가중치</th></tr></thead>
          <tbody>
            ${rows.map((r,i) => `
              <tr class="mrow" data-i="${i}" tabindex="0" role="button" aria-expanded="false" aria-label="${esc(r.comp.name)} 근거 경험 펼치기" style="cursor:pointer">
                <td><span class="chip ${r.isGap ? 'on-gap' : 'on-matched'}">${esc(r.comp.name)}</span></td>
                <td style="color:var(--faint); font-size:11.5px">${CATLAB[r.comp.category]}</td>
                <td><div class="meter ${r.isGap ? 'is-gap' : ''}"><i style="width:${Math.round(r.score*100)}%"></i></div></td>
                <td style="font-size:12px; color:${r.evid.length ? 'var(--ink-2)' : 'var(--gap)'}">${r.evid.length ? r.evid.length + '건' : '없음'}</td>
                <td class="mono" style="font-size:11.5px; color:var(--muted)">${r.weight.toFixed(1)}</td>
              </tr>
              <tr class="mdet" data-i="${i}" hidden><td colspan="5" style="background:var(--surface-2)">
                ${r.evid.length ? r.evid.map(e => `
                  <div style="padding:6px 0; font-size:12.5px">
                    <b style="color:var(--ink)">${esc(e.title)}</b>
                    <span style="color:var(--muted)"> · ${esc(e.result)}</span>
                  </div>`).join('')
                : `<div style="padding:6px 0; font-size:12.5px; color:var(--gap)">이 역량을 태그한 경험이 하나도 없습니다. ${esc(r.suggestion || '')}</div>`}
              </td></tr>`).join('')}
          </tbody>
        </table>
      </div>
    </div>

    <div class="card pad" style="margin-top:18px; background:var(--accent-soft); border-color:var(--accent)">
      <p style="font-size:12.5px; color:var(--ink)"><b>왜 이 화면이 필요한가</b> — 취업특강 <b>§21 ③ 직무 키워드 적합도 분석</b>에 따르면 기업은 “해당 직무와 연관된 핵심 역량 단어가 자소서에 있는지 없는지를 AI로 돌린다.” 넣어야 할 키워드는 두 가지 — <b>① 지원 직무의 핵심 역량 ② 그 회사 인재상 키워드</b>. 그런데 강사는 “그런 키워드를 넣을 생각을 안 하시는 것 같아요. 제가 본 적이 거의 없는 것 같습니다”라고 했다. 이 화면은 그 대조를 지원 전에 미리 해 두는 것이다.</p>
    </div>`;

  $$('#s3body .mrow').forEach(tr => {
    const act = () => {
      const d = $(`#s3body .mdet[data-i="${tr.dataset.i}"]`);
      d.hidden = !d.hidden;
      tr.setAttribute('aria-expanded', String(!d.hidden));
    };
    tr.addEventListener('click', act);
    tr.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' '){ e.preventDefault(); act(); } });
  });
}

/* ============================================================
   S4 · 자소서 에디터
   점검 5종 중 4종은 AI가 아니라 문자열 규칙이다.
   ============================================================ */
let s4qid = null, s4text = '', s4used = [];
const appsWithQ = () => DATA.applications.filter(a => DATA.questions.some(q => q.applicationId === a.id));
const qOf = appId => DATA.questions.filter(q => q.applicationId === appId);
const stamp = () => { const d = new Date(); return `${d.getMonth()+1}/${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`; };

function lint(text, q){
  const out = [];
  const p = postingOfQ(q);
  const n = text.trim().length;

  // ① 분량 — 감점 1위(85%). 요구 글자 수의 80% 이상은 채워야 한다.
  if (q && n > 0 && n < q.charLimit * 0.8)
    out.push({ level:'bad', label:'분량', rank:'감점 1위 · 85%',
      msg:`${n}자 · 요구 ${q.charLimit}자의 ${Math.round(n/q.charLimit*100)}%. 무성의해 보이는 자소서가 감점 1위입니다. 80%(${Math.round(q.charLimit*0.8)}자) 이상 채우세요.` });
  if (q && n > q.charLimit)
    out.push({ level:'bad', label:'분량', rank:'제한 초과',
      msg:`${n}자 · 제한 ${q.charLimit}자를 ${n - q.charLimit}자 초과했습니다.` });

  // ② 기업명 오기 — 감점 2위(75%). 채점자가 0점을 준다.
  rivalsFor(p).filter(x => text.includes(x)).forEach(x =>
    out.push({ level:'bad', label:'기업명 오기', rank:'감점 2위 · 75% · 채점자 0점',
      msg:`본문에 “${x}”가 있습니다. 지원 기업은 ${p.company}입니다. “제가 채점할 때 보면 다 빵점 줍니다” — 강의 §21 ② 기본 결격 사유 필터링.` }));

  // ③ 근거 없는 역량 나열 · 금지 표현 — 감점 3위(60%)
  DATA.bannedPhrases.filter(b => text.includes(b.phrase)).forEach(b =>
    out.push({ level:'bad', label:'금지 표현', rank:'감점 3위 · 60%',
      msg:`“${b.phrase}” → ${b.instead}` }));

  // ④ 정량 근거 — 감점 4위(45%). 수치 없는 성과 표현.
  if (n > 80 && !/[0-9]/.test(text))
    out.push({ level:'warn', label:'정량 근거', rank:'감점 4위 · 45%',
      msg:'본문에 숫자가 하나도 없습니다. “여러분의 성과는 무엇이든 숫자로 만들 수 있습니다.”' });

  // ⑤ 직무 키워드 적합도 — 기업 AI가 실제로 돌리는 검사 3위(18%)
  //    결함이 아니라 커버리지 정보다. 한 문항이 모든 키워드를 담을 수는 없다.
  if (n > 0){
    const top = p.required.filter(r => r.weight >= 0.8).map(r => byId(r.competencyId)).filter(Boolean);
    const inText = c => [c.name, ...(c.aliases || [])].some(w => text.includes(w));
    const has = top.filter(inText);
    const miss = top.filter(c => !inText(c));
    out.push({ level: has.length ? 'info' : 'warn', label:'직무 키워드', rank:'§21 ③ 직무 키워드 적합도 · AI 검사 18%',
      msg: `핵심 요구 역량 ${top.length}개 중 ${has.length}개가 본문에 있습니다.` +
           (miss.length ? ` 없는 것 — ${miss.map(c => c.name).join(', ')}. 다른 문항에서 덮으면 됩니다.` : ' 전부 포함되었습니다.') });
  }

  // ⑥ 두괄식 — 인사담당자 62%가 5분 미만으로 스캔한다
  const first = (text.split(/[.!?\n]/)[0] || '').trim();
  if (first.length > 70)
    out.push({ level:'warn', label:'두괄식', rank:'검토 5분 미만 62%',
      msg:`첫 문장이 ${first.length}자입니다. 담당자는 정독하지 않고 스캔합니다. 결론을 먼저, 짧게.` });

  // ⑦ 물어본 개수만큼 답했는가 — 자가 체크
  if (q && q.asks && n > 0){
    const done = (q.asksDone || []).length;
    if (done < q.asks.length)
      out.push({ level:'warn', label:'요구사항', rank:'§33 그룹핑 · 요구사항 누락',
        msg:`이 문항은 ${q.asks.length}가지를 묻습니다. ${done}가지만 확인 표시했습니다 — ${q.asks.filter((_,i) => !(q.asksDone||[]).includes(i)).join(', ')}` });
  }

  // ⑧ 비교 기준 — "숫자만 쓰고 끝내는 사람이 99%. '이게 잘한 거 맞아?' 라는 생각이 든다"
  if (/[0-9]+ *(%|배|건|초|분|시간|명|원)/.test(text) && !/(기존|대비|평균|이전|에서|→|타 ?팀|다른 조|보다)/.test(text))
    out.push({ level:'warn', label:'비교 기준', rank:'감점 4위 · 45%',
      msg:'수치는 있는데 비교 대상이 없습니다. "다른 조는 평균 10%인데 우리는 45%" 처럼 그 숫자가 왜 잘한 건지 근거를 붙이세요.' });

  return out;
}



/* 타이핑 중에는 우측 패널과 카운터만 갱신한다 — textarea 를 다시 그리지 않는다 */
function paintS4Live(q, list){
  const over = s4text.length > q.charLimit, thin = s4text.length < q.charLimit * 0.8;
  const cnt = $('#s4count');
  if (cnt) cnt.innerHTML = `
    <div style="position:relative; width:118px" title="80% 지점 눈금 — 그 아래는 무성의 감점 구간">
      <div class="meter ${thin || over ? 'is-gap' : ''}" style="min-width:118px"><i style="width:${Math.min(100, s4text.length / q.charLimit * 100)}%"></i></div>
      <span style="position:absolute; left:80%; top:-2px; width:1px; height:11px; background:var(--ink); opacity:.55"></span>
    </div>
    <span class="mono" style="font-size:12px; color:${over || thin ? 'var(--gap)' : 'var(--matched)'}; font-variant-numeric:tabular-nums">${s4text.length} / ${q.charLimit}자</span>`;
  const lint = $('#s4lint');
  if (lint) lint.innerHTML = s4LintHtml(q);
}

function bindS4Side(list){
  $$('#s4side .s4ex').forEach(c => c.addEventListener('change', () => {
    s4used = $$('#s4side .s4ex').filter(x => x.checked).map(x => +x.value);
    const q2 = DATA.questions.find(x => x.id === s4qid); q2.usedExperienceIds = s4used;
    const n = $('#s4side .cardhead .n'); if (n) n.textContent = `${s4used.length}건`;
    paintS4Live(q2, list);
  }));
}

function s4EvidenceHtml(q, list){
  return `
      <div class="card" data-note="answer_experience">
        <div class="cardhead"><h3>근거 경험</h3><span class="n">${s4used.length}건</span></div>
        <div class="cardbody" style="display:flex; flex-direction:column; gap:8px">
          ${DATA.experiences.map(e => `
            <label style="display:flex; gap:8px; align-items:flex-start; font-size:12px; cursor:pointer">
              <input type="checkbox" class="s4ex" value="${e.id}" ${s4used.includes(e.id) ? 'checked' : ''} style="margin-top:3px; accent-color:var(--accent)">
              <span>${esc(e.title)}<br><span style="color:var(--faint); font-size:11px">${esc(e.result)}</span></span>
            </label>`).join('')}
          <p style="font-size:11px; color:var(--muted); border-top:1px solid var(--border); padding-top:9px; margin-top:2px">
            체크한 경험의 id 가 <code>usedExperienceIds</code> 로 프롬프트에 들어가고, 저장 시 <code>answer_experience</code> 에 기록된다. AI가 무엇을 근거로 썼는지 DB가 기억한다.
          </p>
        </div>
      </div>`;
}

function s4LintHtml(q){
  const issues = lint(s4text, q);
  const problems = issues.filter(i => i.level !== 'info');
  const notes = issues.filter(i => i.level === 'info');
  return `
      <div class="card" data-note="문자열 규칙 · AI 아님">
        <div class="cardhead">
          <h3>문장 점검</h3>
          <span class="n" style="color:${problems.length ? 'var(--gap)' : 'var(--matched)'}">${problems.length ? problems.length + '건' : '이상 없음'}</span>
        </div>
        <div class="cardbody" style="display:grid; gap:12px; grid-template-columns:repeat(auto-fit,minmax(300px,1fr))">
          ${problems.length ? problems.map(i => `
            <div style="display:flex; flex-direction:column; gap:3px">
              <div style="display:flex; gap:6px; align-items:center">
                <span class="pill ${i.level === 'bad' ? 'warn' : 'mut'}">${i.label}</span>
                <span style="font-size:10px; color:var(--faint); font-family:var(--mono)">${esc(i.rank)}</span>
              </div>
              <span style="font-size:12px; color:var(--ink-2); padding-left:2px">${esc(i.msg)}</span>
            </div>`).join('')
          : '<p style="font-size:12px; color:var(--matched)">분량 · 기업명 · 금지 표현 · 정량 근거 · 비교 기준 · 요구사항 · 두괄식 모두 통과했습니다.</p>'}
          ${notes.map(i => `
            <div style="display:flex; flex-direction:column; gap:3px">
              <div style="display:flex; gap:6px; align-items:center">
                <span class="pill info">${i.label}</span>
                <span style="font-size:10px; color:var(--faint); font-family:var(--mono)">${esc(i.rank)}</span>
              </div>
              <span style="font-size:12px; color:var(--muted); padding-left:2px">${esc(i.msg)}</span>
            </div>`).join('')}
        </div>
        <div style="padding:0 18px 14px; font-size:11px; color:var(--muted)">
          여덟 항목 <b>전부 문자열 규칙</b>이다. LLM을 빼도 그대로 동작한다 — 이게 이 서비스가 AI 없이도 성립하는 근거다.
        </div>
      </div>`;
}

function renderEssay(){
  const p = P();
  const app = appOfPosting(p);
  const ep = essayProgress(p);

  if (!app){
    $('#dtEssay').innerHTML = `
      <div class="emptystate" data-note="application 없음">
        <h3>아직 지원서를 만들지 않았습니다</h3>
        <p>자소서는 <code>application → question → answer</code> 로 묶입니다. 이 공고에 지원서를 만들면 문항을 등록하고 답변 버전을 관리할 수 있습니다.</p>
        <button class="btn primary sm" id="esMkApp">이 공고로 지원서 만들기</button>
      </div>`;
    $('#esMkApp').addEventListener('click', () => {
      openApp();
      $('#apCo').value = p.company; $('#apPos').value = p.position;
      $('#apDday').value = dday(p.deadline);
    });
    return;
  }

  const list = appQs(app);
  if (!list.length){
    const noServer = p.questionsFromServer === false;
    $('#dtEssay').innerHTML = `
      <div class="emptystate" data-note="GET /postings/${p.id}/questions">
        <h3>${noServer ? '이 공고의 자소서 문항이 서버에 없습니다' : '문항을 불러오지 못했습니다'}</h3>
        <p>${noServer
          ? '수집한 공고에 자소서 문항이 늘 붙어 있지는 않습니다. 채용 사이트에서 문항을 확인해 직접 등록하면, 이후 점검·초안·버전 관리는 똑같이 동작합니다.'
          : '잠시 후 다시 시도하거나 문항을 직접 등록하세요.'}</p>
        <div class="noteonly" style="max-width:460px; margin:0 auto 16px">
          <div class="netlog"><div><span class="t">응답</span> <span class="m">GET /api/postings/${p.id}/questions</span></div><div><span class="s2">200</span> <span class="t">{ "questions": [] }</span></div></div>
        </div>
        <div style="display:flex; gap:8px; justify-content:center">
          <button class="btn primary sm" id="esAddQ">문항 직접 추가</button>
          ${noServer ? '' : '<button class="btn sm" id="esRetry">다시 시도</button>'}
        </div>
      </div>`;
    $('#esAddQ').addEventListener('click', () => openQ(app.id));
    const rt = $('#esRetry'); if (rt) rt.addEventListener('click', () => { toast('문항이 여전히 비어 있습니다'); });
    return;
  }

  if (s4qid === null || !list.some(x => x.id === s4qid)){
    s4qid = list[0].id; s4text = list[0].draft || ''; s4used = list[0].usedExperienceIds || [];
  }
  const q = DATA.questions.find(x => x.id === s4qid);
  const issues = lint(s4text, q);
  const problems = issues.filter(i => i.level !== 'info');
  const notes = issues.filter(i => i.level === 'info');
  const over = s4text.length > q.charLimit;

  $('#dtEssay').innerHTML = `
    <div class="split3">
    <div class="card" data-note="application 1:N question">
      <div class="cardhead"><h3>지원서</h3><span class="n">${list.length}문항</span></div>
      <div class="cardbody" style="padding:10px; display:flex; flex-direction:column; gap:10px">
        <div style="font-size:11px; color:var(--faint); display:flex; gap:8px; flex-wrap:wrap">
          <span class="pill ${ESSAY_STATE[ep.state].cls}">${esc(ESSAY_STATE[ep.state].label)}</span>
          <span>${esc(ep.label)}</span>
        </div>
        <button class="btn sm" data-addQ2="${app.id}" style="justify-content:center">＋ 문항 추가</button>
        <div style="display:flex; flex-direction:column; gap:6px; border-top:1px dashed var(--border); padding-top:9px">
        ${list.map(x => `
          <button class="qitem" data-q="${x.id}" aria-current="${x.id === s4qid ? 'true' : 'false'}">
            <span style="font-size:12px; line-height:1.5">${esc(x.prompt.length > 44 ? x.prompt.slice(0,44) + '…' : x.prompt)}</span>
            <span style="font-size:10.5px; color:var(--faint)">${x.charLimit}자 · ${x.draft ? '초안 있음' : '미작성'}</span>
          </button>`).join('')}
        </div>
      </div>
    </div>

    <div class="card" data-note="answer (version 이력)">
      <div class="cardhead">
        <h3 style="font-weight:500; font-size:13px; line-height:1.5">${esc(q.prompt)}</h3>
      </div>
      <div class="cardbody">
        <div style="font-size:11.5px; color:var(--muted); background:var(--surface-2); padding:8px 11px; border-radius:3px; margin-bottom:10px">
          <b style="color:var(--ink)">이 문항의 의도</b> — ${esc(q.intent)}
        </div>
        <div style="display:flex; gap:8px; align-items:center; flex-wrap:wrap; margin-bottom:12px" data-note="§33 물어본 개수만큼 답하기">
          <span style="font-size:11px; color:var(--faint)">물어본 것 ${q.asks.length}개</span>
          ${q.asks.map((a, i) => `<button class="chip ${(q.asksDone||[]).includes(i) ? 'on-matched' : ''}" data-ask="${i}" style="font-size:11px">${(q.asksDone||[]).includes(i) ? '✓' : '○'} ${esc(a)}</button>`).join('')}
        </div>
        <textarea class="inp" id="s4ta" rows="13" spellcheck="false" placeholder="근거 경험을 고른 뒤 초안을 생성하거나, 직접 쓰세요">${esc(s4text)}</textarea>
        <div style="display:flex; align-items:center; gap:10px; margin-top:10px; flex-wrap:wrap">
          <button class="btn primary sm" id="s4gen" data-note="POST /answers/${q.id}/ai/draft → 202">초안 생성</button>
          <button class="btn sm" id="s4save" data-note="PUT /answers/${q.id}">저장</button>
          <span class="spacer" style="flex:1"></span>
          <div style="display:flex; align-items:center; gap:9px" id="s4count">
            <div style="position:relative; width:118px" title="80% 지점 눈금 — 그 아래는 무성의 감점 구간">
              <div class="meter ${s4text.length < q.charLimit*0.8 || over ? 'is-gap' : ''}" style="min-width:118px"><i style="width:${Math.min(100, s4text.length / q.charLimit * 100)}%"></i></div>
              <span style="position:absolute; left:80%; top:-2px; width:1px; height:11px; background:var(--ink); opacity:.55"></span>
            </div>
            <span class="mono" style="font-size:12px; color:${over || s4text.length < q.charLimit*0.8 ? 'var(--gap)' : 'var(--matched)'}; font-variant-numeric:tabular-nums">${s4text.length} / ${q.charLimit}자</span>
          </div>
        </div>
      </div>
    </div>

    <div id="s4side" style="display:flex; flex-direction:column; gap:16px"></div>
    </div>

    <div id="s4lint" style="margin-top:16px"></div>`;

  $('#s4side').innerHTML = s4EvidenceHtml(q, list);
  $('#s4lint').innerHTML = s4LintHtml(q);
  $$('#dtEssay [data-addQ2]').forEach(b => b.addEventListener('click', () => openQ(+b.dataset.addq2)));
  $('#s4save').addEventListener('click', () => {
    const q2 = DATA.questions.find(x => x.id === s4qid);
    if (!s4text.trim()){ toast('내용이 비어 있어 저장하지 않았습니다'); return; }
    q2.draft = s4text;
    renderDetail(); renderHome();
    toast(`저장했습니다 · ${app.company} 지원서에 묶임`);
  });
  $$('#dtEssay [data-ask]').forEach(b => b.addEventListener('click', () => {
    const i = +b.dataset.ask;
    const q2 = DATA.questions.find(x => x.id === s4qid);
    q2.asksDone = q2.asksDone || [];
    q2.asksDone = q2.asksDone.includes(i) ? q2.asksDone.filter(v => v !== i) : [...q2.asksDone, i];
    renderEssay();
  }));
  $$('#dtEssay [data-q]').forEach(b => b.addEventListener('click', () => {
    const x = DATA.questions.find(v => v.id === +b.dataset.q);
    s4qid = x.id; s4text = x.draft || ''; s4used = x.usedExperienceIds || [];
    renderEssay();
  }));
  $('#s4ta').addEventListener('input', e => {
    s4text = e.target.value;
    const q2 = DATA.questions.find(x => x.id === s4qid);
    q2.draft = s4text;
    paintS4Live(q2, list);
  });
  bindS4Side(list);
  $('#s4gen').addEventListener('click', async e => {
    const b = e.currentTarget, target = s4qid;              // 클릭 시점의 문항을 고정한다
    const q2 = DATA.questions.find(x => x.id === target);
    if (!q2.aiDraft){
      toast('이 문항은 목업에 준비된 AI 초안이 없습니다 — 직접 작성하거나 다른 문항을 시도하세요');
      return;
    }
    b.disabled = true; b.innerHTML = '<span class="spin"></span>생성 중';
    await sleep(1500);
    q2.draft = q2.aiDraft;
    if (s4qid !== target){                                   // 대기 중 문항이 바뀌었으면 화면은 건드리지 않는다
      toast(`“${q2.prompt.slice(0, 18)}…” 문항의 초안이 생성되었습니다`);
      renderHome(); return;
    }
    s4text = q2.draft;
    renderEssay();
  });
}

/* 지원서 진행 상태는 저장하지 않는다 — questions 의 draft 유무에서 파생한다.
   제출 여부는 우리가 관측할 수 없으므로 모델에 두지 않는다. */
const ESSAY_STATE = {
  NO_APP:  { label:'지원서 없음',  cls:'mut' },
  NO_Q:    { label:'문항 미등록',  cls:'mut' },
  EMPTY:   { label:'작성 전',      cls:'warn' },
  WRITING: { label:'작성 중',      cls:'warn' },
  DONE:    { label:'작성 완료',    cls:'ok' },
};

/* ============================================================
   홈 — 공고별 매칭도 + 자소서 진행 상태
   ============================================================ */
let homeSort = 'match';
let homeOnlyBm = false;

/* 평균 매칭 대신 이 자리에 둔다.
   평균은 행동으로 이어지지 않는 숫자였다 — 68% 를 보고 사용자가 할 수 있는 게 없고,
   공고를 더 담으면 본인과 무관한 이유로 숫자가 움직여 오해를 만든다.
   대신 "지금 무엇을 채우면 가장 많이 움직이나" 를 센다. 여러 공고에서 동시에
   갭인 역량 하나가 곧 다음에 등록할 경험이고, 그게 인테이크로 이어진다. */
function topGapCard(live){
  const cnt = new Map();
  live.forEach(({ m }) => m.rows.filter(r => r.isGap)
    .forEach(r => cnt.set(r.competencyId, (cnt.get(r.competencyId) || 0) + 1)));
  if (!cnt.size)
    return `<div class="card pad"><div class="stat"><span class="v" style="color:var(--matched)">0</span><span class="l">비어 있는 요구 역량</span></div></div>`;
  // 동점이면 가중치 합이 큰 쪽을 먼저 — 같은 3건이어도 더 무겁게 요구되는 것이 있다.
  const w = id => live.reduce((a, { m }) =>
    a + m.rows.filter(r => r.competencyId === id && r.isGap).reduce((x, r) => x + r.weight, 0), 0);
  const [id, n] = [...cnt.entries()].sort((a, b) => b[1] - a[1] || w(b[0]) - w(a[0]))[0];
  const c = byId(id);
  return `<div class="card pad" data-note="computeMatch().rows 에서 isGap 집계"
    title="${esc(c.name)} 을 증명하는 경험을 등록하면 이 ${n}건의 매칭이 함께 오른다">
    <div class="stat">
      <span class="v" style="color:var(--gap); font-size:19px; line-height:1.3">${esc(c.name)}</span>
      <span class="l">이 역량 하나가 공고 <b style="color:var(--ink-2)">${n}건</b>의 갭</span>
    </div></div>`;
}

function renderHome(){
  const all = DATA.postings.filter(p => dday(p.deadline) >= 0);
  const live = all.filter(p => !homeOnlyBm || isBm(p.id))
    .map(p => ({ p, m: computeMatch(p), pr: essayProgress(p), d: dday(p.deadline) }));

  live.sort((a, b) => homeSort === 'match' ? b.m.overall - a.m.overall : a.d - b.d);

  const soon = live.filter(x => x.d <= 7).length;
  const writing = live.filter(x => x.pr.state === 'WRITING' || x.pr.state === 'EMPTY').length;

  $('#homeStats').innerHTML = [
    ['활성 공고', all.length, ''],
    ['마감 7일 내', soon, soon ? 'var(--gap)' : ''],
    ['즐겨찾기', all.filter(x => isBm(x.id)).length, ''],
  ].map(([l, v, c]) => `<div class="card pad"><div class="stat"><span class="v" ${c ? `style="color:${c}"` : ''}>${v}</span><span class="l">${l}</span></div></div>`).join('')
    + topGapCard(live);

  $('#homeList').innerHTML = live.map(({ p, m, pr, d }) => {
    const gaps = m.rows.filter(r => r.isGap);
    const top = m.rows.filter(r => !r.isGap).sort((x, y) => y.weight - x.weight).slice(0, 2);
    const rest = m.rows.length - top.length - gaps.length;
    const sameCo = live.filter(x => x.p.company === p.company).length;
    const mc = m.overall >= SCORE.RECOMMEND ? 'var(--matched)' : m.overall >= SCORE.WEAK ? 'var(--info)' : 'var(--gap)';
    const ep = essayProgress(p);
    return `<article class="jobcard" data-pid="${p.id}" tabindex="0" role="button" aria-label="${esc(p.company)} ${esc(p.position)} 상세 보기" data-note="GET /api/postings?active=true">
      <div class="jc-top">
        <span class="jc-logo" aria-hidden="true">${esc(p.company.slice(0, 1))}</span>
        <span style="display:flex; align-items:center; gap:7px">
          <span class="jc-match" style="color:${mc}">${Math.round(m.overall * 100)}%</span>
          ${bmBtn(p.id, true)}
        </span>
      </div>

      <div>
        <div class="jc-co">
          ${esc(p.company)}
          ${sameCo > 1 ? `<span class="pill info" style="margin-left:6px" title="이 기업은 지금 ${sameCo}개 직무를 뽑고 있습니다">이 회사 ${sameCo}건</span>` : ''}
        </div>
        <div class="jc-title">${esc(p.position)}</div>
      </div>

      <div class="jc-tags">
        ${top.map(r => `<span class="chip ${CAT[r.comp.category]}">${esc(r.comp.name)}</span>`).join('')}
        ${gaps.map(g => `<span class="chip on-gap">갭 · ${esc(g.comp.name)}</span>`).join('')}
        ${rest > 0 ? `<span class="chip" style="color:var(--faint)">＋${rest}</span>` : ''}
      </div>

      <div class="jc-foot">
        <div>
          <div class="jc-dday ${d <= 7 ? 'soon' : ''}">D-${d}</div>
          <div class="jc-sub">${esc(p.deadline)} 마감</div>
          <div style="display:flex; align-items:center; gap:7px; margin-top:7px">
            ${ep.total
              ? `<div class="meter" style="width:52px"><i style="width:${Math.round(ep.ratio*100)}%"></i></div>
                 <span class="mono" style="font-size:11px; color:${ep.state === 'DONE' ? 'var(--matched)' : 'var(--ink-2)'}">자소서 ${ep.done}/${ep.total}</span>`
              : `<span class="pill ${ESSAY_STATE[ep.state].cls}">${esc(ESSAY_STATE[ep.state].label)}</span>`}
          </div>
        </div>
        <button class="btn primary sm" data-open="${p.id}" data-tab="essay">
          ${ep.state === 'NO_APP' ? '지원하기' : ep.state === 'NO_Q' ? '문항 등록' : '자소서'}
        </button>
      </div>
    </article>`;
  }).join('');

  $('#homeNote').innerHTML = '정렬은 <b>매칭순</b>과 <b>마감 임박순</b> 두 가지다. 지원자 수 기준 인기순은 넣지 않았다 — 공고 사이트가 지원자 수를 공개할 때만 가능한 지표라, 없는 데이터를 있는 척할 수 없다.';

  $('#onlyBm').setAttribute('aria-pressed', String(homeOnlyBm));
  if (!live.length) $('#homeList').innerHTML = '<p class="empty">즐겨찾기한 공고가 없습니다. 카드의 즐겨찾기 버튼을 눌러 담아 두세요.</p>';
  bindBm('#homeList');
  $('#sortMatch').setAttribute('aria-pressed', String(homeSort === 'match'));
  $('#sortDday').setAttribute('aria-pressed', String(homeSort === 'dday'));

  $$('#homeList [data-open]').forEach(b => b.addEventListener('click', e => { e.stopPropagation(); openDetail(+b.dataset.open, b.dataset.tab); }));
  $$('#homeList .jobcard').forEach(c => {
    c.addEventListener('click', () => openDetail(+c.dataset.pid));
    c.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' '){ e.preventDefault(); openDetail(+c.dataset.pid); } });
  });
  $$('#homeList [data-addQ]').forEach(b => b.addEventListener('click', () => openQ(+b.dataset.addq)));
  $$('#homeList [data-mkApp]').forEach(b => b.addEventListener('click', () => {
    const p = DATA.postings.find(x => x.id === +b.dataset.mkapp);
    openApp(); $('#apCo').value = p.company; $('#apPos').value = p.position;
    $('#apDday').value = dday(p.deadline);
  }));
}
$('#sortMatch').addEventListener('click', () => { homeSort = 'match'; renderHome(); });
$('#sortDday').addEventListener('click', () => { homeSort = 'dday'; renderHome(); });
$('#onlyBm').addEventListener('click', () => { homeOnlyBm = !homeOnlyBm; renderHome(); });


/* ============================================================
   개발 참고 — 다른 개발자가 구현하면서 찾아보는 값들.
   전부 DATA 와 SCORE 상수에서 파생시킨다. 손으로 적은 표가 아니다.
   ============================================================ */
const CATDESC = {
  TECH:   '기술 역량 · 공고의 자격요건·우대사항에서 주로 나온다',
  SOFT:   '행동 역량 · 경험 서술에서 드러난다',
  DOMAIN: '도메인 이해 · 산업·업무 맥락',
  VALUE:  '인재상 키워드 · 기업이 쓴 단어를 그대로 써야 매칭에 걸린다',
};

function copyBlock(id, text){
  return `<div class="prewrap"><button class="btn sm copybtn" data-copy="${id}">복사</button><pre class="code" id="${id}">${esc(text)}</pre></div>`;
}


/* ============================================================
   개발 참고 06~08 — 엔드포인트 · ERD · 시드 SQL
   ============================================================ */
const ENDPOINTS = [
  ['공고', 'GET',    '/api/postings?active=true',            '200',      '활성 공고 + 내 매칭. 홈 카드가 이것만으로 그려진다', ''],
  ['공고', 'GET',    '/api/postings/{id}',                   '200 404',  '공고 상세 + required[] + 파싱된 섹션', ''],
  ['공고', 'GET',    '/api/postings/{id}/questions',         '200',      '자소서 문항. 서버가 모르면 빈 배열', ''],
  ['공고', 'POST',   '/internal/ai/extract',                 '202',      'AX-1 · 수집 배치가 호출. 사용자 경로 아님', 'AX-1'],
  ['공고', 'POST',   '/api/postings/{id}/competencies',      '201',      '추출 결과 확정 저장', ''],
  ['경험', 'GET',    '/api/experiences?competencyId=',       '200',      '경험 목록 · 역량 필터', ''],
  ['경험', 'POST',   '/api/experiences',                     '201 400',  '경험 등록. 응답 후 ExperienceCreated 발행', ''],
  ['경험', 'PUT',    '/api/experiences/{id}',                '200 404',  '경험 수정. ExperienceUpdated 발행', ''],
  ['경험', 'POST',   '/api/experiences/ai/intake',           '202',      'AX-4 · 링크에서 후보 추출 + 되물을 질문', 'AX-4'],
  ['AI',   'GET',    '/api/ai/jobs/{jobId}',                 '200 404',  '폴링. PENDING / COMPLETED / FAILED', ''],
  ['지원', 'POST',   '/api/applications',                    '201 409',  '같은 공고 중복 지원은 409', ''],
  ['지원', 'POST',   '/api/applications/{id}/questions',     '201',      '문항 직접 등록', ''],
  ['지원', 'GET',    '/api/applications/{id}/assessment',    '200',      '저장된 평가 조회. 계산하지 않는다', ''],
  ['지원', 'POST',   '/internal/ai/match',                   '202',      'AX-2 · 경험 변경 이벤트가 호출', 'AX-2'],
  ['자소서','PUT',   '/api/answers/{id}',                    '200',      '새 버전 저장. 덮어쓰지 않는다', ''],
  ['자소서','GET',   '/api/questions/{id}/answers',          '200',      '버전 이력', ''],
  ['자소서','POST',  '/api/answers/{id}/ai/draft',           '202',      'AX-3 · 초안 생성', 'AX-3'],
  ['즐겨찾기','POST','/api/postings/{id}/bookmark',          '201 204',  'DELETE 로 해제. bookmark 테이블', ''],
];

const TABLES = [
  ['user',                  'id, name, email',                                                                  '', ''],
  ['competency',            'id, name, category, aliases[]',                                                    '마스터 · 20행 고정', ''],
  ['experience',            'id, user_id, title, period, category, situation, task, action, result, source',    'STAR 4필드', ''],
  ['experience_competency', 'experience_id, competency_id, strength',                                           'N:M', 'strength 가 쌍의 속성이라 @ManyToMany 로는 표현 자체가 안 된다'],
  ['company',               'id, name, career_url',                                                             'career_url 이 수집 대상', ''],
  ['job_posting',           'id, company_id, position, raw_text, deadline, source, collected_at, questions_from_server', '직무 단위 · 기업 단위가 아니다', ''],
  ['posting_competency',    'posting_id, competency_id, weight, evidence_line, evidence_status',                'N:M', 'weight·인용줄·검증상태가 쌍의 속성'],
  ['application',           'id, user_id, posting_id, created_at',                                              '', 'status·submitted_at 없음 — 제출 여부는 관측 불가라 모델에 두지 않는다'],
  ['question',              'id, application_id, seq, prompt_text, char_limit, asks[]',                         '', ''],
  ['answer',                'id, question_id, content, char_count, source, updated_at',                        '문항당 1행', ''],
  ['answer_experience',     'answer_id, experience_id',                                                         'N:M', '이 문장이 어떤 경험을 근거로 썼는가'],
  ['assessment',            'id, application_id, verdict, headline, summary, state, input_sig, computed_at',    '이벤트로 갱신 · 화면은 읽기만', ''],
  ['assessment_action',     'id, assessment_id, seq, effort, tag, title',                                       '', ''],
  ['ai_job',                'id, user_id, job_type, target_id, state, input_hash, request_json, response_json, tokens, latency_ms', '멱등키 = (user_id, job_type, input_hash)', ''],
  ['bookmark',              'user_id, posting_id, created_at',                                                  'N:M', 'created_at 이 붙는 순간 이것도 엔티티다'],
  ['crawl_job',             'id, company_id, state, started_at, finished_at, error',                            '수집 배치 상태', ''],
];

function sq(v){ return v === null || v === undefined ? 'NULL' : `'${String(v).replace(/'/g, "''")}'`; }

function buildSeedSql(){
  const L = [];
  L.push('-- 목업 DATA 에서 생성한 시드. 스키마가 확정되면 컬럼명만 맞추면 된다.');
  L.push('BEGIN;');
  L.push('', '-- competency');
  DATA.competencies.forEach(c =>
    L.push(`INSERT INTO competency (id, name, category, aliases) VALUES (${c.id}, ${sq(c.name)}, ${sq(c.category)}, ARRAY[${(c.aliases||[]).map(sq).join(', ')}]::text[]);`));

  L.push('', '-- experience');
  DATA.experiences.forEach(e => {
    L.push(`INSERT INTO experience (id, user_id, title, period, category, situation, task, action, result, source) VALUES (${e.id}, 1, ${sq(e.title)}, ${sq(e.period)}, ${sq(e.category)}, ${sq(e.situation)}, ${sq(e.task)}, ${sq(e.action)}, ${sq(e.result)}, ${sq(e.source || 'MANUAL')});`);
    e.competencyIds.forEach(cid =>
      L.push(`INSERT INTO experience_competency (experience_id, competency_id, strength) VALUES (${e.id}, ${cid}, ${(e.strength?.[cid] ?? SCORE.DEFAULT_STRENGTH).toFixed(2)});`));
  });

  L.push('', '-- company · job_posting · posting_competency');
  const companies = [...new Set(DATA.postings.map(p => p.company))];
  companies.forEach((n, i) => L.push(`INSERT INTO company (id, name) VALUES (${i + 1}, ${sq(n)});`));
  DATA.postings.forEach(p => {
    L.push(`INSERT INTO job_posting (id, company_id, position, raw_text, deadline, source, collected_at, questions_from_server) VALUES (${p.id}, ${companies.indexOf(p.company) + 1}, ${sq(p.position)}, ${sq(p.rawText)}, DATE ${sq(p.deadline)}, ${sq(p.source)}, ${p.collectedAt ? `TIMESTAMP ${sq(p.collectedAt)}` : 'NULL'}, ${p.questionsFromServer !== false});`);
    p.required.forEach(r =>
      L.push(`INSERT INTO posting_competency (posting_id, competency_id, weight, evidence_line, evidence_status) VALUES (${p.id}, ${r.competencyId}, ${r.weight}, NULL, 'UNVERIFIED');  -- ${r.evidence.slice(0, 40)}`));
  });

  L.push('', '-- application · question · answer');
  DATA.applications.filter(a => a.id).forEach(a =>
    L.push(`INSERT INTO application (id, user_id, posting_id) VALUES (${a.id}, 1, ${a.postingId ?? 'NULL'});`));
  DATA.questions.forEach((q, i) => {
    L.push(`INSERT INTO question (id, application_id, seq, prompt_text, char_limit) VALUES (${q.id}, ${q.applicationId}, ${i + 1}, ${sq(q.prompt)}, ${q.charLimit});`);
    if ((q.draft || '').trim())
      L.push(`INSERT INTO answer (question_id, content, char_count, source) VALUES (${q.id}, ${sq(q.draft)}, ${q.draft.length}, 'MANUAL');`);
  });

  L.push('', 'COMMIT;');
  return L.join('\n');
}

function specExtraHtml(){
  const grp = [...new Set(ENDPOINTS.map(e => e[0]))];
  return `
    <div class="spec-sec">
      <h3><span class="no">06</span>API 엔드포인트</h3>
      <p class="lead">
        화면 곳곳의 설계 주석에 흩어져 있는 계약을 한 표로 모았다. <code>/internal/</code> 은 <b>사용자 경로가 아니다</b> —
        배치와 이벤트 컨슈머만 호출한다. <code>202</code> 인 것만 사용자가 기다린다.
      </p>
      <div class="card"><div class="tw"><table class="tb">
        <thead><tr><th style="width:74px">묶음</th><th style="width:64px">메서드</th><th style="width:250px">경로</th><th style="width:78px">상태</th><th>설명</th></tr></thead>
        <tbody>${ENDPOINTS.map(([g, m, path, st, desc, ax]) => `<tr>
          <td style="color:var(--faint); font-size:11.5px">${esc(g)}</td>
          <td class="mono" style="color:${m === 'GET' ? 'var(--info)' : m === 'DELETE' ? 'var(--gap)' : 'var(--accent)'}">${m}</td>
          <td class="mono" style="font-size:11.5px">${esc(path)}</td>
          <td class="mono" style="font-size:11px; color:var(--muted)">${esc(st)}</td>
          <td style="font-size:11.5px">${esc(desc)} ${ax ? `<span class="pill acc">${ax}</span>` : ''}</td>
        </tr>`).join('')}</tbody>
      </table></div></div>
    </div>

    <div class="spec-sec">
      <h3><span class="no">07</span>데이터 모델</h3>
      <p class="lead">
        테이블 ${TABLES.length}개. <b>N:M 4개는 전부 payload 를 가진다</b> — 그래서 <code>@ManyToMany</code> 로 매핑하면
        컬럼을 넣을 자리가 없어 값이 조용히 사라진다. 조인 테이블은 예외 없이 <code>@EmbeddedId</code> + <code>@MapsId</code> 엔티티로 만든다.
      </p>
      <figure class="fig" style="margin-bottom:12px">
        <svg viewBox="0 0 880 440" class="dia" role="img" aria-label="user·experience·competency·job_posting·application·question·answer·assessment 의 관계도. 굵은 선 넷이 payload 를 가진 다대다 관계다">
          <defs><marker id="ar-erd" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
            <path d="M0,0 L10,5 L0,10 z" fill="var(--muted)"/></marker></defs>

          <rect class="node" x="20"  y="24"  width="132" height="38" rx="3"/><text class="n" x="86"  y="48"  text-anchor="middle">user</text>
          <rect class="node" x="20"  y="140" width="132" height="38" rx="3"/><text class="n" x="86"  y="164" text-anchor="middle">experience</text>
          <rect class="hub"  x="374" y="140" width="132" height="38" rx="3"/><text class="n" x="440" y="164" text-anchor="middle">competency</text>
          <rect class="node" x="700" y="140" width="132" height="38" rx="3"/><text class="n" x="766" y="164" text-anchor="middle">job_posting</text>
          <rect class="node" x="150" y="256" width="132" height="38" rx="3"/><text class="n" x="216" y="280" text-anchor="middle">answer</text>
          <rect class="node" x="374" y="256" width="132" height="38" rx="3"/><text class="n" x="440" y="280" text-anchor="middle">question</text>
          <rect class="node" x="620" y="256" width="132" height="38" rx="3"/><text class="n" x="686" y="280" text-anchor="middle">application</text>
          <rect class="node" x="620" y="372" width="132" height="38" rx="3"/><text class="n" x="686" y="396" text-anchor="middle">assessment</text>

          <line class="rel" x1="86" y1="62" x2="86" y2="134" marker-end="url(#ar-erd)"/>
          <text class="e" x="94" y="105">1:N</text>
          <polyline class="nmline" points="152,36 300,20 640,20 748,134" fill="none"/>
          <text class="k" x="470" y="14" text-anchor="middle">N:M · bookmark</text>

          <line class="nmline" x1="152" y1="159" x2="374" y2="159"/>
          <text class="k" x="263" y="151" text-anchor="middle">N:M</text>
          <text class="b" x="263" y="176" text-anchor="middle">experience_competency</text>

          <line class="nmline" x1="506" y1="159" x2="700" y2="159"/>
          <text class="k" x="603" y="151" text-anchor="middle">N:M</text>
          <text class="b" x="603" y="176" text-anchor="middle">posting_competency</text>

          <line class="rel" x1="740" y1="178" x2="702" y2="250" marker-end="url(#ar-erd)"/>
          <text class="e" x="750" y="218">1:N</text>
          <line class="rel" x1="620" y1="275" x2="512" y2="275" marker-end="url(#ar-erd)"/>
          <text class="e" x="566" y="267" text-anchor="middle">1:N</text>
          <line class="rel" x1="374" y1="275" x2="288" y2="275" marker-end="url(#ar-erd)"/>
          <text class="e" x="331" y="267" text-anchor="middle">1:N</text>
          <line class="rel" x1="686" y1="294" x2="686" y2="366" marker-end="url(#ar-erd)"/>
          <text class="e" x="694" y="336">1:1</text>

          <line class="nmline" x1="216" y1="256" x2="110" y2="178"/>
          <text class="k" x="228" y="222">N:M</text>
          <text class="b" x="228" y="237">answer_experience</text>
        </svg>
        <figcaption>굵은 선이 payload 를 가진 N:M 이다. <code>competency</code> 가 허브 — 내 경험과 채용공고가 <b>같은 어휘</b>를 공유하기 때문에 매칭이 문자열 비교가 아니라 조인으로 성립한다. <code>ai_job</code>·<code>crawl_job</code>·<code>company</code>·<code>assessment_action</code> 은 아래 표에만 있다.</figcaption>
      </figure>
      <div class="card"><div class="tw"><table class="tb">
        <thead><tr><th style="width:190px">테이블</th><th>주요 컬럼</th><th style="width:30%">비고</th></tr></thead>
        <tbody>${TABLES.map(([t, cols, note, warn]) => `<tr>
          <td class="mono" style="color:${note === 'N:M' ? 'var(--accent)' : 'var(--ink)'}">${esc(t)}${note === 'N:M' ? ' <span class="pill acc">N:M</span>' : ''}</td>
          <td class="mono" style="font-size:11px; color:var(--muted)">${esc(cols)}</td>
          <td style="font-size:11.5px; color:${warn ? 'var(--gap)' : 'var(--muted)'}">${esc(warn || (note === 'N:M' ? '' : note))}</td>
        </tr>`).join('')}</tbody>
      </table></div></div>
    </div>

    <div class="spec-sec">
      <h3><span class="no">08</span>시드 데이터</h3>
      <p class="lead">
        이 목업이 지금 쓰고 있는 데이터를 그대로 <code>INSERT</code> 로 뽑는다 —
        역량 ${DATA.competencies.length} · 경험 ${DATA.experiences.length} · 공고 ${DATA.postings.length} ·
        요구역량 ${DATA.postings.reduce((a, p) => a + p.required.length, 0)} · 문항 ${DATA.questions.length}행.
        <b>시드는 코딩이 아니라 타이핑이라 계속 밀린다</b> — 그래서 화면에서 뽑을 수 있게 해 둔다.
        컬럼명은 위 07 표 기준이니 스키마가 확정되면 이름만 맞추면 된다.
      </p>
      ${copyBlock('cpSeed', buildSeedSql())}
    </div>`;
}

function renderSpec(){
  const byCat = c => DATA.competencies.filter(x => x.category === c);
  const aiVocab = DATA.competencies.map(c => ({ id: c.id, name: c.name, category: c.category }));

  const enums = [
    ['competency.category', ['TECH','SOFT','DOMAIN','VALUE'], '역량 범주'],
    ['posting.source', ['CRAWLED','MANUAL'], '공고가 어떻게 들어왔나'],
    ['(파생) 자소서 진행', Object.keys(ESSAY_STATE), '컬럼이 아니다 — questions 의 draft 유무에서 계산한다'],
    ['assessment.state', ['FRESH','QUEUED','RUNNING'], '평가 재계산 상태'],
    ['assessment.verdict', ['RECOMMEND','CONDITIONAL','HOLD'], '지원 판정'],
    ['action.effort', Object.keys(EFFORT), '액션 난이도'],
    ['experience.source', ['MANUAL','AI_INTAKE'], '경험을 어떻게 만들었나'],
    ['answer.source', ['MANUAL','AI_DRAFT'], '답변 본문 출처'],
    ['intake.question.field', ['task','result'], 'AI 가 되물을 STAR 필드'],
  ];

  const ax = [
    { id:'AX-1', name:'요구역량 추출', trigger:'공고 수집 배치', temp:'0.0', sync:'배치 · 사용자 대기 없음',
      ep:'POST /internal/ai/extract',
      out:{ required:[{ competencyId:1, weight:0.9, evidenceLine:5 }], newCompetencies:['Kafka 운영'] } },
    { id:'AX-2', name:'매칭 평가 요약', trigger:'경험 변경 이벤트 · 야간 배치', temp:'0.2', sync:'이벤트 · 사용자 대기 없음',
      ep:'POST /internal/ai/match',
      out:{ verdict:'CONDITIONAL', headline:'기술 역량은 채웠지만 인재상 키워드가 비어 있습니다',
            summary:'요구 역량 10개 중 8개를 덮어 매칭 73%입니다. …',
            actions:[{ effort:'LOW', tag:'AI 도구 활용', title:'MSA 프로젝트에서 AI 도구를 어떻게 썼는지 한 문단 보강하세요.' }] } },
    { id:'AX-3', name:'자소서 초안', trigger:'사용자 버튼', temp:'0.6', sync:'202 + 폴링',
      ep:'POST /api/answers/{id}/ai/draft',
      out:{ draft:'…', charCount:668, usedExperienceIds:[1], cautions:['결과에 정량 수치가 없습니다.'] } },
    { id:'AX-4', name:'포트폴리오 인테이크', trigger:'사용자가 링크 제출', temp:'0.2', sync:'202 + 폴링',
      ep:'POST /api/experiences/ai/intake',
      out:{ candidates:[{ title:'오픈소스 라이브러리 버그 수정 기여', situation:'…', action:'…',
            evidence:[{ type:'PR', ref:'PR #412 · merged', quote:'fix: preserve nested generic type info' }],
            missing:['task','result'],
            questions:[{ field:'task', q:'이 버그를 고치기로 한 계기는 무엇이었나요?', why:'PR 에는 무엇을 고쳤는지는 있지만 왜 골랐는지는 없습니다.' }],
            suggestedCompetencyIds:[5,11,9,12] }] } },
  ];

  $('#specBody').innerHTML = `
    <div class="spec-sec">
      <h3><span class="no">01</span>AI 가 고를 수 있는 요구역량 태그</h3>
      <p class="lead">
        AX-1(공고) · AX-2(평가) · AX-4(인테이크) 가 역량을 지목할 때 <b>반드시 이 ${DATA.competencies.length}개 안에서만</b> 고른다.
        사전 밖 표현은 <code>newCompetencies</code> 로 격리해 사람이 승인해야 들어온다 — 그래야 “협업”과 “팀워크”가 따로 쌓이지 않는다.
        <code>aliases</code> 는 자소서 본문에서 키워드 포함 여부를 볼 때 쓰는 동의어다(“클라우드 인프라 운영” ← AWS · 쿠버네티스 · Docker · 리눅스).
      </p>
      ${['TECH','SOFT','DOMAIN','VALUE'].map(cat => `
        <div class="card" style="margin-bottom:12px">
          <div class="cardhead">
            <h3><span class="chip ${CAT[cat]}">${cat}</span></h3>
            <span style="font-size:12px; color:var(--muted)">${esc(CATDESC[cat])}</span>
            <span class="n">${byCat(cat).length}개</span>
          </div>
          <div class="tw"><table class="tb">
            <thead><tr><th style="width:52px">id</th><th style="width:150px">name</th><th>aliases · 본문 매칭용 동의어</th></tr></thead>
            <tbody>${byCat(cat).map(c => `<tr>
              <td class="mono">${c.id}</td>
              <td><span class="chip ${CAT[c.category]}">${esc(c.name)}</span></td>
              <td style="color:var(--muted); font-size:11.5px">${(c.aliases || []).map(esc).join(' · ') || '—'}</td>
            </tr>`).join('')}</tbody>
          </table></div>
        </div>`).join('')}
      <p class="lead" style="margin-top:10px">프롬프트에 넣을 어휘 목록 — 이대로 복사해서 시스템 메시지에 붙인다.</p>
      ${copyBlock('cpVocab', JSON.stringify(aiVocab, null, 1))}
    </div>

    <div class="spec-sec">
      <h3><span class="no">02</span>상태 값 전체</h3>
      <p class="lead">
        화면에 쓰이는 모든 enum. <b>DB 에는 문자열로 저장한다</b>(<code>@Enumerated(EnumType.STRING)</code>) —
        순서로 저장하면 상수를 하나 끼워 넣는 순간 기존 행이 전부 다른 뜻이 된다.
        AI 응답의 enum 필드는 JSON Schema 로 강제하고, 목록 밖 값이 오면 화면에 <code>undefined</code> 가 그려지므로 서버에서 폐기하거나 폴백한다.
      </p>
      <div class="card"><div class="tw"><table class="tb">
        <thead><tr><th style="width:200px">필드</th><th>허용 값</th><th style="width:34%">설명</th></tr></thead>
        <tbody>${enums.map(([f, vs, d]) => `<tr>
          <td class="mono">${esc(f)}</td>
          <td>${vs.map(v => `<span class="chip" style="font-family:var(--mono); font-size:10.5px">${esc(v)}</span>`).join(' ')}</td>
          <td style="color:var(--muted); font-size:11.5px">${esc(d)}</td>
        </tr>`).join('')}</tbody>
      </table></div></div>
    </div>

    <div class="spec-sec">
      <h3><span class="no">03</span>AI 확장 지점 4개 · 응답 계약</h3>
      <p class="lead">
        트리거가 다르면 <b>사용자가 기다리는지</b>가 갈린다. AX-1·AX-2 는 배치·이벤트라 화면이 기다리지 않고,
        AX-3·AX-4 만 <code>202</code> + 폴링이다. 아래 JSON 은 그대로 목 응답으로 써도 된다.
      </p>
      <div class="card" style="margin-bottom:12px"><div class="tw"><table class="tb">
        <thead><tr><th>지점</th><th>이름</th><th>트리거</th><th>temp</th><th>동기성</th><th>엔드포인트</th></tr></thead>
        <tbody>${ax.map(a => `<tr>
          <td><span class="pill acc">${a.id}</span></td><td style="color:var(--ink)">${esc(a.name)}</td>
          <td style="font-size:11.5px">${esc(a.trigger)}</td>
          <td class="mono">${a.temp}</td>
          <td style="font-size:11.5px; color:${a.sync.startsWith('202') ? 'var(--gap)' : 'var(--matched)'}">${esc(a.sync)}</td>
          <td class="mono" style="font-size:11px">${esc(a.ep)}</td>
        </tr>`).join('')}</tbody>
      </table></div></div>
      ${ax.map((a, i) => `
        <div style="margin-bottom:10px">
          <div style="font-size:11.5px; color:var(--muted); margin-bottom:5px">${a.id} 응답 <code>result</code></div>
          ${copyBlock('cpAx' + i, JSON.stringify(a.out, null, 1))}
        </div>`).join('')}
    </div>

    <div class="spec-sec">
      <h3><span class="no">04</span>점수 산식과 임계값</h3>
      <p class="lead">
        <b>이 값들은 코드에 한 곳(<code>SCORE</code>)에만 있다.</b> 화면 색·판정·문구가 전부 같은 상수를 본다 —
        표시값과 판정값이 다른 데서 나오면 “매칭 85%”와 “조건부 지원”이 한 카드에 같이 뜬다.
        점수는 <b>서버가 계산해 응답에 실어 보내고 프론트는 그리기만 한다.</b>
      </p>
      <div class="card pad" style="margin-bottom:12px">
        <dl class="rule">
          <dt>커버리지</dt><dd class="mono">score = min(1, Σ strength(경험, 역량))</dd>
          <dt>갭 판정</dt><dd class="mono">근거 0건 이거나 score &lt; ${SCORE.GAP}</dd>
          <dt>얕음</dt><dd class="mono">score &lt; ${SCORE.WEAK}</dd>
          <dt>강점</dt><dd class="mono">score ≥ ${SCORE.STRONG}</dd>
          <dt>전체</dt><dd class="mono">overall = Σ(weight × score) / Σ weight</dd>
          <dt>지원 권장</dt><dd class="mono">overall ≥ ${SCORE.RECOMMEND} 그리고 갭 0</dd>
          <dt>조건부</dt><dd class="mono">overall ≥ ${SCORE.CONDITIONAL}</dd>
          <dt>강도 기본값</dt><dd class="mono">태그 시 ${SCORE.PICK_STRENGTH} · 값이 없으면 ${SCORE.DEFAULT_STRENGTH} · 약 0.4 / 중 0.7 / 강 0.9</dd>
        </dl>
      </div>
      <div class="callout warn" style="margin-top:0">
        <div class="lab">알려진 한계</div>
        <p>① <code>min(1, Σ)</code> 는 포화한다 — 약한 경험을 여러 개 태그하면 강한 경험 하나보다 높아지고, 경험이 늘면 모든 공고가 100%로 수렴한다. 실제 구현에서는 <code>1 − Π(1 − sᵢ)</code> 를 권한다.<br>
        ② 임계값 ${SCORE.RECOMMEND}/${SCORE.CONDITIONAL} 은 캘리브레이션된 값이 아니다. 합격 데이터가 쌓이기 전까지는 <b>절대 점수보다 상대 순위</b>가 정직하다.</p>
      </div>
    </div>

    <div class="spec-sec">
      <h3><span class="no">05</span>문장 점검 규칙</h3>
      <p class="lead">
        자소서 에디터의 점검 ${DATA.bannedPhrases.length > 0 ? '7' : ''}종 중 <b>여섯은 문자열 규칙</b>이고 LLM 이 필요 없다.
        근거 수치는 취업캠프 자료의 감점 순위다.
      </p>
      <div class="card" style="margin-bottom:12px"><div class="tw"><table class="tb">
        <thead><tr><th style="width:110px">항목</th><th style="width:170px">근거</th><th>규칙</th><th style="width:80px">AI 필요</th></tr></thead>
        <tbody>
          <tr><td>분량</td><td class="mono">감점 1위 · 85%</td><td>요구 글자 수의 80% 미만이면 경고, 초과하면 오류</td><td>아니오</td></tr>
          <tr><td>기업명 오기</td><td class="mono">감점 2위 · 75%</td><td>내가 아는 모든 기업 − 지금 쓰는 기업 이 본문에 있으면 오류</td><td>아니오</td></tr>
          <tr><td>금지 표현</td><td class="mono">감점 3위 · 60%</td><td>${DATA.bannedPhrases.length}개 문구 포함 검사</td><td>아니오</td></tr>
          <tr><td>정량 근거</td><td class="mono">감점 4위 · 45%</td><td>본문에 숫자가 하나도 없으면 경고</td><td>아니오</td></tr>
          <tr><td>비교 기준</td><td class="mono">감점 4위 · 45%</td><td>수치는 있는데 “기존/대비/평균/에서” 가 없으면 경고</td><td>아니오</td></tr>
          <tr><td>요구사항</td><td class="mono">§33 그룹핑</td><td>문항이 묻는 개수만큼 답했는지 자가 체크</td><td>아니오</td></tr>
          <tr><td>두괄식</td><td class="mono">검토 5분 미만 62%</td><td>첫 문장이 70자를 넘으면 경고</td><td>아니오</td></tr>
          <tr><td>직무 키워드</td><td class="mono">§21 ③ · AI 검사 18%</td><td>가중치 0.8 이상 요구 역량이 본문에 있는지 (aliases 포함)</td><td>아니오</td></tr>
        </tbody>
      </table></div></div>
      <p class="lead">금지 표현 목록</p>
      ${copyBlock('cpBanned', JSON.stringify(DATA.bannedPhrases, null, 1))}
    </div>

    ${specExtraHtml()}`;

  $$('#specBody [data-copy]').forEach(b => b.addEventListener('click', async () => {
    const el = document.getElementById(b.dataset.copy);
    try {
      await navigator.clipboard.writeText(el.textContent);
      toast('복사했습니다');
    } catch (e) {
      const rg = document.createRange(); rg.selectNodeContents(el);
      const sel = getSelection(); sel.removeAllRanges(); sel.addRange(rg);
      toast('클립보드를 쓸 수 없어 선택만 했습니다 — ⌘C 로 복사하세요');
    }
  }));
}

/* ============================================================
   부트
   ============================================================ */
renderS1(); renderHome(); renderSpec();
(() => {
  const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  $('#themeBtn').setAttribute('aria-pressed', String(prefersDark));
  document.documentElement.setAttribute('data-theme', prefersDark ? 'dark' : 'light');
})();
let start = 'home';
try { start = localStorage.getItem('cm.screen') || 'home'; } catch(e) {}
if (!document.getElementById(start)) start = 'home';
go(start);

/* ============================================================
   등록 다이얼로그 — 경험 / 지원
   목업이지만 DATA 를 실제로 바꾸고, 바뀐 값이 매칭 계산에 그대로 반영된다.
   ============================================================ */
function toast(msg, ms = 2600){
  document.querySelectorAll('.toast').forEach(x => x.remove());
  const t = document.createElement('div');
  t.className = 'toast'; t.textContent = msg; t.setAttribute('role', 'status');
  document.body.appendChild(t);
  setTimeout(() => t.remove(), ms);
}

const STR = [
  { v:0.4, lab:'약' },
  { v:0.7, lab:'중' },
  { v:0.9, lab:'강' },
];
/* 라벨은 값에서 파생한다 — 시드에 0.5·0.8 같은 중간값이 있어도 라벨이 어긋나지 않는다.
   숫자 자체는 정밀해 보이지만 근거가 없으므로 설계 주석 모드에서만 노출한다. */
const strLabel = v => v >= 0.85 ? '강' : v >= 0.60 ? '중' : '약';

/* ---------- 경험 등록 ---------- */
let exPick = {};       // { competencyId: strength }
let exEditId = null;   // null 이면 신규, 아니면 수정 중인 경험 id

function openExp(editId){
  exEditId = editId ?? null;
  const e = exEditId != null ? DATA.experiences.find(x => x.id === exEditId) : null;

  exPick = e ? { ...(e.strength || {}) } : {};
  if (e) e.competencyIds.forEach(id => { if (!(id in exPick)) exPick[id] = SCORE.PICK_STRENGTH; });

  $('#exTitle').value  = e?.title  || '';
  $('#exPeriod').value = e?.period || '';
  $('#exS').value = e?.situation || '';
  $('#exT').value = e?.task      || '';
  $('#exA').value = e?.action    || '';
  $('#exR').value = e?.result    || '';
  $('#exCat').value = e?.category || $('#exCat').options[0].value;
  $('#exErr').innerHTML = '';
  $('#exRHint').textContent = '';

  $('#expDlgH').textContent = e ? '경험 수정' : '경험 등록';
  $('#expDlgSub').textContent = e
    ? `${e.title}${e.source === 'AI_INTAKE'
        ? ((e.evidenceRefs || []).length ? ' · 포폴 인테이크로 만든 경험'
                                         : ' · 포폴 인테이크에서 시작 · 문장은 본인이 다시 씀')
        : ''}`
    : 'STAR 4필드 · 역량 태그';
  $('#exSave').textContent = e ? '저장' : '등록';
  $('#exTabs').hidden = !!e;   // 수정 중에는 인테이크 탭 자체가 없다

  // exGapText 를 switchTab 앞에서 정한다 — switchTab → paintFooter 가 이 값을 읽는다.
  const gaps = computeMatch().rows.filter(r => r.isGap).map(r => r.comp.name);
  exGapText = gaps.length
    ? `지금 비어 있는 요구 역량 — <b style="color:var(--gap)">${gaps.join(', ')}</b>. 이 경험이 그걸 증명한다면 꼭 태그하세요.`
    : '요구 역량이 모두 덮여 있습니다.';

  paintExPool(); paintStar();
  resetIntake();
  switchTab('manual');
  $('#expDlg').showModal();
  $('#exTitle').focus();
}

/* 수동 폼(#exPicked/#exPool)과 인테이크 에디터(#inPicked/#inPool)가 같은 코드를 쓴다.
   $$ 가 root 인자를 받으므로 셀렉터만 상대화하면 된다. */
function paintPickInto(pick, pickedEl, poolEl, after){
  pickedEl.innerHTML = Object.keys(pick).length
    ? Object.entries(pick).map(([id, st]) => {
        const c = byId(+id);
        return `<span class="strchip">${esc(c.name)}
          <button type="button" data-cyc="${id}" title="약 / 중 / 강 전환 · 내부값 ${st}">${strLabel(st)}<span class="noteonly-i" style="font-family:var(--mono); opacity:.75"> ${st}</span></button>
          <button type="button" class="rm" data-rm="${id}" aria-label="${esc(c.name)} 제거">×</button></span>`;
      }).join('')
    : '<span class="hint">아래에서 역량을 고르세요 · 최소 1개</span>';

  poolEl.innerHTML = DATA.competencies
    .filter(c => !(c.id in pick))
    .map(c => `<button type="button" class="chip ${CAT[c.category]}" data-add="${c.id}">${esc(c.name)}</button>`)
    .join('');

  $$('[data-add]', poolEl).forEach(b => b.addEventListener('click', () => { pick[+b.dataset.add] = SCORE.PICK_STRENGTH; after(); }));
  $$('[data-rm]', pickedEl).forEach(b => b.addEventListener('click', () => { delete pick[+b.dataset.rm]; after(); }));
  $$('[data-cyc]', pickedEl).forEach(b => b.addEventListener('click', () => {
    const id = +b.dataset.cyc;
    const cur = STR.findIndex(x => x.lab === strLabel(pick[id]));
    pick[id] = STR[(cur + 1) % STR.length].v;   // 어떤 값이든 약→중→강 3단계로 스냅된다
    after();
  }));
}

function paintExPool(){ paintPickInto(exPick, $('#exPicked'), $('#exPool'), paintExPool); }

function paintStar(){
  const vals = ['exS','exT','exA','exR'].map(id => $('#' + id).value.trim().length > 0);
  const n = vals.filter(Boolean).length;
  $$('#exStarBar span').forEach((s, i) => s.classList.toggle('on', vals[i]));
  $('#exStarN').textContent = `${n} / 4`;
  $('#exRHint').innerHTML = rHintHTML($('#exR').value.trim());
}

/* 수동 폼과 인테이크 에디터가 같은 문구를 쓴다. 두 곳에 복사해 두면 한쪽만 고쳐진다. */
const rHintHTML = r => !r ? ''
  : /[0-9]/.test(r)
    ? '<span style="color:var(--matched)">수치가 있습니다. 가능하면 비교 대상도 함께 쓰세요 — “다른 조는 평균 10%인데 우리는 45%”</span>'
    : '<span style="color:var(--gap)">숫자가 없습니다. 성과를 잘 못 쓰는 것이 감점 4위(45%)입니다.</span>';

function saveExp(){
  const title = $('#exTitle').value.trim();
  const errs = [];
  if (!title) errs.push('제목은 필수입니다.');
  if (!$('#exR').value.trim()) errs.push('결과(R)는 필수입니다. 성과 없는 경험은 자소서에서 쓸 수 없습니다.');
  if (!Object.keys(exPick).length) errs.push('역량을 최소 하나 태그해야 매칭에 쓰입니다.');
  if (errs.length){
    $('#exErr').innerHTML = errs.map(e => `<div class="err">⚠ ${esc(e)}</div>`).join('');
    return;
  }

  const patch = {
    title,
    period: $('#exPeriod').value.trim() || '기간 미입력',
    category: $('#exCat').value,
    situation: $('#exS').value.trim(),
    task: $('#exT').value.trim(),
    action: $('#exA').value.trim(),
    result: $('#exR').value.trim(),
    competencyIds: Object.keys(exPick).map(Number),
    strength: { ...exPick },
  };

  const editing = exEditId != null;
  let targetId;
  if (editing){
    const e = DATA.experiences.find(x => x.id === exEditId);
    Object.assign(e, patch);
    targetId = e.id;
  } else {
    targetId = Math.max(0, ...DATA.experiences.map(e => e.id)) + 1;
    DATA.experiences.push({ id: targetId, ...patch, usedInAnswers: 0 });
  }

  $('#expDlg').close();
  renderS1(); renderHome(); if ($('#detail').classList.contains('on')) renderDetail();
  onExperienceChanged(editing ? 'ExperienceUpdated' : 'ExperienceCreated', [targetId]);
  toast(editing ? '경험을 수정했습니다 · 평가 재계산을 요청했습니다'
                : '경험을 등록했습니다 · 평가 재계산을 요청했습니다');
  exEditId = null;
}

/* ---------- 지원 등록 ---------- */
function openApp(){
  ['apCo','apPos'].forEach(id => $('#' + id).value = '');
  $('#apDday').value = 14; $('#apQ').value = 4;
  $('#apErr').innerHTML = '';
  $('#appDlg').showModal();
  $('#apCo').focus();
}

function saveApp(){
  const company = $('#apCo').value.trim(), position = $('#apPos').value.trim();
  const errs = [];
  if (!company) errs.push('기업명은 필수입니다.');
  if (!position) errs.push('직무는 필수입니다.');
  if (DATA.applications.some(a => a.company === company && a.position === position))
    errs.push(`이미 등록된 지원입니다 — ${company} · ${position}. (API 라면 409 Conflict)`);
  if (errs.length){
    $('#apErr').innerHTML = errs.map(e => `<div class="err">⚠ ${esc(e)}</div>`).join('');
    return;
  }
  const q = Math.max(1, +$('#apQ').value || 4);
  DATA.applications.push({
    id: Math.max(100, ...DATA.applications.map(a => a.id || 0)) + 1,
    postingId: DATA.postings.find(p => p.company === company)?.id ?? null,
    company, position,
    dday: Math.max(0, +$('#apDday').value || 0),
    match: null,
  });
  $('#appDlg').close();
  renderHome(); if ($('#detail').classList.contains('on')) renderDetail();
  toast(`${company} 지원을 등록했습니다 · 회사명 오기 감지 목록에도 추가됨`);
}

/* ---------- 배선 ---------- */
$('#s1add').addEventListener('click', openExp);
// 인테이크 탭에서는 saveExp 가 hidden 인 #exErr 에 에러를 써서 죽은 버튼이었다.
$('#exSave').addEventListener('click', () => $('#exIntake').hidden ? saveExp() : commitIntake());
$('#apSave').addEventListener('click', saveApp);
$('#exCancel').addEventListener('click', () => tryCloseExp());
$('#apCancel').addEventListener('click', () => $('#appDlg').close());
['exS','exT','exA','exR'].forEach(id => $('#' + id).addEventListener('input', paintStar));

/* ============================================================
   AX-4 · 포트폴리오 인테이크 (좌 후보 레일 + 우 STAR 에디터)
   코드·문서에서 확인되는 것만 채우고, 본인만 아는 것은 되묻는다.
   STAR 의 T(목표)와 R(수치)는 저장소에 없다 — 지어내지 않고 질문한다.

   질문지가 아니라 에디터다. AI 가 쓴 S·A 도 그 자리에서 고칠 수 있다.
   대신 "누가 쓴 문장인가" 를 네 겹으로 표시한다 — 스트라이프 · 배지 ·
   붙는 보조정보의 종류(근거 vs 질문) · 등록 후 경험 카드의 배지.
   ============================================================ */
let inStep    = 1;       // 1 후보 선택 / 2 에디터
let inChosen  = new Set();
let inDraft   = {};      // { key: {title, period, category, situation, task, action, result, comp:{id:strength}} }
let inActive  = null;    // 지금 #inEd 에 열린 후보 key
let inPoolOpen  = false;
let inLinksOpen = true;
let inCommitArmed = null;
let inMatchCache = { sig:'', html:'' };
let exGapText = '';      // .dlgfoot 문구 (탭 전환 시 복원용)

const AI_FIELDS = ['situation', 'action'];      // 후보가 값을 들고 오는 칸
const MINE_FALLBACK = ['task', 'result'];       // AX-4 계약의 missing 기본값
const FDESC = { situation:'어떤 상황이었나', task:'무엇을 목표로 삼았나',
                action:'내가 한 행동과 적용한 방식', result:'결과 (숫자로)' };

const candOf = k => DATA.intakeCandidates.find(c => c.key === k);
const cLen   = s => (s || '').trim().length;
const aiOf   = (c, f) => (c[f] || '').trim();
const isAIField = (c, f) => AI_FIELDS.includes(f) && !!aiOf(c, f);
const qsOf   = (c, f) => (c.questions || []).filter(q => q.field === f);

/* 저장소에 없어 본인이 채워야 하는 칸 — 필드명을 박아 넣지 않고
   AI 가 실제로 되물은 것에서 파생한다. AX-4 가 언젠가 R 을 저장소에서
   찾아내 questions 에서 빼면 UI 규칙이 저절로 따라간다. */
function mineOf(c){
  const asked = [...new Set((c.questions || []).map(q => q.field))]
                  .filter(f => FIELDS.includes(f) && !isAIField(c, f));
  if (asked.length) return asked;
  return (c.missing || MINE_FALLBACK).filter(f => FIELDS.includes(f) && !isAIField(c, f));
}
/* 수동 폼(saveExp)의 필수값과 합집합 — 두 경로가 같은 "유효한 경험" 정의를 쓴다 */
const requiredTextOf = c => [...new Set([...mineOf(c), 'result'])];

/* "고쳤다" 플래그를 저장하지 않고 매번 원문과 비교한다.
   고쳤다가 원문 그대로 되돌리면 표시도 저절로 원상복구되고,
   편집해 놓고 AI 표식만 남기는 세탁도 불가능하다. */
const editedFieldsOf = k => {
  const c = candOf(k), d = inDraft[k];
  if (!d) return [];
  return AI_FIELDS.filter(f => isAIField(c, f) && d[f].trim() !== aiOf(c, f));
};

function seedDraft(k){
  if (inDraft[k]) return inDraft[k];
  const c = candOf(k);
  const comp = {};
  (c.suggestedCompetencyIds || []).forEach(id => comp[id] = SCORE.PICK_STRENGTH);
  return inDraft[k] = {
    title: c.title, period: c.period || '', category: c.category,
    situation: c.situation || '', task: '', action: c.action || '', result: '',
    comp,
  };
}

function missingOf(k){
  const c = candOf(k), d = inDraft[k], m = [];
  if (!d) return [{ f:'seed', lab:'초안 없음' }];
  if (!cLen(d.title)) m.push({ f:'title', lab:'제목' });
  requiredTextOf(c).forEach(f => { if (!cLen(d[f])) m.push({ f, lab:FLAB[f] }); });
  if (!Object.keys(d.comp).length) m.push({ f:'comp', lab:'역량' });
  return m;
}
const isReady = k => missingOf(k).length === 0;

function resetIntakeState(){
  inStep = 1; inChosen = new Set(); inDraft = {}; inActive = null;
  inPoolOpen = false; inCommitArmed = null; inMatchCache = { sig:'', html:'' };
}

function paintLinkBlock(){
  $('#inLinkBox').hidden  = !inLinksOpen;
  $('#inLinkFold').hidden =  inLinksOpen;
}

function resetIntake(){
  resetIntakeState();
  inLinksOpen = true; paintLinkBlock();
  $('#inOut').innerHTML = '';
  $('#inLog').innerHTML = '<div class="t">요청 없음</div>';
  $('#inState').className = 'pill mut'; $('#inState').textContent = '대기';
  $('#inRun').disabled = false; $('#inRun').textContent = '분석';
}

$$('#exTabs .tab').forEach(t => t.addEventListener('click', () => switchTab(t.dataset.tab)));
$('#inLinkEdit').addEventListener('click', () => { inLinksOpen = true; paintLinkBlock(); });

function switchTab(name){
  $$('#exTabs .tab').forEach(t => t.classList.toggle('on', t.dataset.tab === name));
  $('#exIntake').hidden = name !== 'intake';
  $('#exManual').hidden = name !== 'manual';
  paintFooter();
}

$('#inRun').addEventListener('click', async e => {
  const btn = e.currentTarget, log = $('#inLog'), st = $('#inState');
  const links = $('#inUrl').value.split('\n').map(x => x.trim()).filter(Boolean);
  if (!links.length){ toast('링크를 한 줄에 하나씩 입력하세요'); return; }

  btn.disabled = true; btn.innerHTML = '<span class="spin"></span>분석 중';
  st.className = 'pill warn'; st.textContent = 'PENDING';
  log.innerHTML = '';
  $('#inOut').innerHTML = `<p class="empty">${links.length}개 소스를 읽는 중…</p>`;

  logLine(log, 'm', `POST /api/experiences/ai/intake  { sources: ${links.length} }`);
  await sleep(430);
  logLine(log, 's2', '202 Accepted  { jobId: "ij_2c9", status: "PENDING" }');
  for (let i = 0; i < 2; i++){
    await sleep(780);
    logLine(log, 'm', 'GET /api/ai/jobs/ij_2c9');
    logLine(log, 's1', '200  { status: "PENDING" }');
  }
  await sleep(700);
  logLine(log, 'm', 'GET /api/ai/jobs/ij_2c9');
  logLine(log, 's2', `200  { status: "COMPLETED", candidates: ${DATA.intakeCandidates.length} }`);

  st.className = 'pill ok'; st.textContent = 'COMPLETED';
  btn.disabled = false; btn.textContent = '다시 분석';
  // inDraft 까지 비운다. 후보 key 가 'oss' 같은 고정 문자열이라
  // 안 비우면 재분석 후 옛 초안이 조용히 되살아난다.
  resetIntakeState();
  inLinksOpen = false; paintLinkBlock();
  $('#inLinkN').textContent = links.length;
  paintIntake();
});

function paintIntake(){ return inStep === 1 ? paintStep1() : paintStep2(); }

/* ---- 1단계 · 후보 선택 ---- */
function paintStep1(){
  const dupes = DATA.intakeCandidates.filter(c => c.duplicateOfExperienceId).length;
  $('#inOut').innerHTML = `
    <div style="display:flex; align-items:center; gap:9px; margin-bottom:10px">
      <span class="pill acc">1 / 2 · 후보 선택</span>
      <span class="hint">여러 개를 한 번에 고를 수 있습니다${dupes ? ` · 이미 등록된 ${dupes}건은 제외됨` : ''}</span>
    </div>
    <p class="hint" style="margin-bottom:12px">
      저장소와 첨부에서 확인된 것만 채웠습니다. <b>목표와 수치는 코드에 없어 비워 두고 다음 화면에서 직접 씁니다</b> — 지어내면 면접에서 그대로 무너집니다.
    </p>
    ${DATA.intakeCandidates.map(c => {
      const dup = !!c.duplicateOfExperienceId;
      const on = inChosen.has(c.key);
      const started = !!inDraft[c.key];
      return `<label class="cand ${on ? 'sel' : ''}" style="margin-bottom:9px; ${dup ? 'opacity:.55' : 'cursor:pointer'}">
        <div style="display:flex; align-items:flex-start; gap:9px">
          <input type="checkbox" data-cand="${c.key}" ${on ? 'checked' : ''} ${dup ? 'disabled' : ''} style="margin-top:3px; accent-color:var(--accent)">
          <div style="flex:1">
            <div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap">
              <b style="color:var(--ink); font-size:13px">${esc(c.title)}</b>
              ${dup ? `<span class="pill mut">이미 등록됨</span>`
                    : `<span class="pill warn">직접 쓸 칸 ${mineOf(c).length}</span>`}
              ${started ? `<span class="pill acc" title="체크를 풀어도 쓰던 내용은 남아 있습니다">작성 중</span>` : ''}
              <span class="pill mut">${esc(c.category)}</span>
            </div>
            <div style="font-size:11.5px; color:var(--muted); margin-top:5px"><b>S</b> ${esc(c.situation)}</div>
            <div style="font-size:11.5px; color:var(--muted)"><b>A</b> ${esc(c.action)}</div>
            <div class="chips" style="margin-top:7px">
              ${c.evidence.map(e => `<span class="chip domain" title="${esc(e.quote)}">${esc(e.type)} · ${esc(e.ref)}</span>`).join('')}
            </div>
          </div>
        </div>
      </label>`;
    }).join('')}
    <div style="display:flex; gap:8px; align-items:center; padding-top:11px; border-top:1px dashed var(--border)">
      <span class="hint">${inChosen.size}건 선택</span>
      <button type="button" class="btn primary sm" id="inNext" style="margin-left:auto" ${inChosen.size ? '' : 'disabled'}>
        선택한 ${inChosen.size}건 편집하기 →
      </button>
    </div>`;

  $$('#inOut [data-cand]').forEach(cb => cb.addEventListener('change', () => {
    cb.checked ? inChosen.add(cb.dataset.cand) : inChosen.delete(cb.dataset.cand);
    paintStep1();
  }));
  $('#inNext').addEventListener('click', enterStep2);
  paintFooter();
}

/* ---- 2단계 · 에디터 ---- */
function enterStep2(){
  if (!inChosen.size){ inStep = 1; return paintStep1(); }
  inStep = 2;
  [...inChosen].forEach(seedDraft);          // 일괄 시드 — missingOf/patchRailRow 의 전제
  if (!inChosen.has(inActive))
    inActive = [...inChosen].find(k => !isReady(k)) || [...inChosen][0] || null;
  paintIntake();
  focusFirstGap(inActive);
}

function fieldHTML(k, f){
  const c = candOf(k), d = inDraft[k];
  const ai = isAIField(c, f), mine = mineOf(c).includes(f);
  const edited = ai && d[f].trim() !== aiOf(c, f);
  const empty = !cLen(d[f]);
  const badge = ai && !edited ? ['mut', 'AI · 근거 확인됨']
              : ai            ? ['acc', empty ? 'AI 문장 지움' : 'AI 문장 → 내가 고침']
              : empty         ? ['warn', '저장소에 없음 · 본인만 아는 것']
              :                 ['ok', '내가 씀'];
  const qs = qsOf(c, f);
  // 질문이 없는데 본인 몫인 칸 — AX-4 가 되묻지 않았어도 지어내지 않는다는 것은 같다.
  const qBlock = mine
    ? `<div class="qrow" style="border-top:none; padding:0; gap:4px">
         ${qs.length
           ? qs.map(q => `<div class="q">${esc(q.q)}</div><div class="why">왜 묻는가 — ${esc(q.why)}</div>`).join('')
           : `<div class="q">이 칸은 저장소에서 확인되지 않습니다.</div>
              <div class="why">왜 묻는가 — AI 는 없는 것을 지어내지 않습니다. 직접 채워 주세요.</div>`}
       </div>`
    : '';
  const rows = f === 'action' ? 3 : 2;
  const ph = f === 'result' ? '숫자를 포함해 써 주세요' : mine ? '한두 문장이면 충분합니다' : '';
  return `
    <div class="field fsrc${ai ? '' : ' fsrc-you'}${edited ? ' fsrc-edited' : ''}${mine && empty ? ' fsrc-todo' : ''}" data-fw="${f}">
      <div style="display:flex; align-items:center; gap:7px; flex-wrap:wrap">
        <label for="in_${f}">${FLAB[f]} · ${FDESC[f]}</label>
        <span class="pill ${badge[0]}" data-badge>${badge[1]}</span>
        <button type="button" class="btn sm" data-revert="${f}" style="margin-left:auto" ${edited ? '' : 'hidden'}>AI 원문으로</button>
      </div>
      ${qBlock}
      <textarea class="inp" id="in_${f}" data-f="${f}" rows="${rows}" placeholder="${ph}">${esc(d[f])}</textarea>
      ${f === 'result' ? `<div class="hint" id="inRHint">${rHintHTML(d.result.trim())}</div>` : ''}
      <div class="hint" data-short style="color:var(--gap)" ${mine && cLen(d[f]) > 0 && cLen(d[f]) < 6 ? '' : 'hidden'}>너무 짧습니다 — 한 문장으로 써야 자소서에서 쓸 수 있습니다.</div>
    </div>`;
}

function railHTML(){
  const ks = [...inChosen];
  return `<aside id="inRail">
    <div class="hint">후보 <b style="color:var(--ink-2)">${ks.length}건</b> · 등록 가능 <b id="inReadyN">${ks.filter(isReady).length}</b></div>
    ${ks.map(k => {
      const ok = isReady(k), ed = editedFieldsOf(k);
      return `<button type="button" class="cand ${k === inActive ? 'sel' : ''}" data-k="${k}"
              style="width:100%; text-align:left; cursor:pointer; padding:9px 10px; gap:5px" title="${esc(candOf(k).title)}">
        <b style="color:var(--ink); font-size:12px; display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap">${esc(inDraft[k].title || candOf(k).title)}</b>
        <span style="display:flex; align-items:center; gap:5px; flex-wrap:wrap">
          <span class="pill ${ok ? 'ok' : 'warn'}" data-rp>${ok ? '등록 가능' : `${missingOf(k).map(m => m.lab).join('·')} 남음`}</span>
          <span class="pill acc" data-re ${ed.length ? '' : 'hidden'}>${ed.map(f => FLAB[f]).join('·')} 수정함</span>
        </span>
      </button>`;
    }).join('')}
    <button type="button" class="btn sm" id="inBack" style="margin-top:3px">← 후보 다시 고르기</button>
  </aside>`;
}

function edHTML(k){
  const c = candOf(k), d = inDraft[k], mine = mineOf(c);
  return `<div id="inEd" style="min-width:0; display:flex; flex-direction:column; gap:12px">
    <div class="field">
      <label for="inTt">제목 *</label>
      <input class="inp" id="inTt" data-f="title" value="${esc(d.title)}">
    </div>
    <div class="frow">
      <div class="field"><label for="inPd">기간</label>
        <input class="inp" id="inPd" data-f="period" value="${esc(d.period)}" placeholder="2026.08"></div>
      <div class="field"><label for="inCt">분류</label>
        <select class="inp" id="inCt" data-f="category">
          ${['팀 프로젝트','개인 프로젝트','실습 프로젝트','대외활동','인턴·근무','수상·자격']
            .map(o => `<option ${o === d.category ? 'selected' : ''}>${o}</option>`).join('')}
        </select></div>
    </div>

    <div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap">
      <span class="pill info">AI_INTAKE</span>
      <span class="starbar" id="inStarBar">${FIELDS.map(f => `<span class="${cLen(d[f]) ? 'on' : ''}"></span>`).join('')}</span>
      <span class="hint" id="inStarN">STAR ${FIELDS.filter(f => cLen(d[f])).length} / 4</span>
      <span class="hint" id="inMineN" style="color:var(--gap)">내가 쓴 것 ${mine.filter(f => cLen(d[f])).length} / ${mine.length}</span>
    </div>

    <div class="fieldset" data-note="experience (STAR) · AI_INTAKE">
      <div class="lg">STAR <span class="pill mut">AI 가 읽은 근거 ${c.evidence.length}건</span></div>
      <div class="hint" id="inEvNote"></div>
      ${c.evidence.map(e => `<div class="hint evq" title="${esc(e.quote)}">
        <span class="mono">${esc(e.type)} · ${esc(e.ref)}</span> — “${esc(e.quote)}”</div>`).join('')}
      ${FIELDS.map(f => fieldHTML(k, f)).join('')}
    </div>

    <div class="fieldset" data-note="experience_competency">
      <div class="lg">이 경험이 증명하는 역량
        <button type="button" class="btn sm" id="inPoolBtn" style="margin-left:auto">${inPoolOpen ? '역량 닫기' : '＋ 역량 고치기'}</button></div>
      <div class="chips" id="inPicked"></div>
      <div class="hint" id="inCompNote"></div>
      <div class="chips" id="inPool" ${inPoolOpen ? '' : 'hidden'} style="padding-top:9px; border-top:1px dashed var(--border)"></div>
    </div>

    <div style="display:flex; gap:8px; align-items:center; padding-top:11px; border-top:1px dashed var(--border)">
      <button type="button" class="btn sm" id="inPrev">← 이전 후보</button>
      <button type="button" class="btn sm" id="inNextGap" style="margin-left:auto">다음 미완 후보 →</button>
    </div>
  </div>`;
}

/* #inOut 전체 재생성 — 클릭 시점에만 부른다. 타이핑 중에는 절대 부르지 않는다(캐럿이 날아간다). */
function paintStep2(){
  if (!inActive || !inDraft[inActive]){ inStep = 1; return paintStep1(); }
  $('#inOut').innerHTML = `
    <div style="display:flex; align-items:center; gap:9px; margin-bottom:12px">
      <span class="pill acc">2 / 2 · 편집</span>
      <span class="hint">AI 가 쓴 문장도 그 자리에서 고칠 수 있습니다 — 고치면 표시가 남습니다</span>
    </div>
    <div class="inwrap${inChosen.size === 1 ? ' solo' : ''}">${railHTML()}${edHTML(inActive)}</div>`;
  bindStep2();
  paintEvidenceNote(inActive);
  paintFooter();
}

function bindStep2(){
  $$('#inRail [data-k]').forEach(b => b.addEventListener('click', () => openCand(b.dataset.k)));
  $('#inBack').addEventListener('click', () => { inStep = 1; paintIntake(); });
  $('#inPrev')?.addEventListener('click', () => stepCand(-1));
  $('#inNextGap')?.addEventListener('click', () => { const n = nextGapKey(); if (n) openCand(n); });
  $('#inPoolBtn').addEventListener('click', () => {
    inPoolOpen = !inPoolOpen;
    $('#inPool').hidden = !inPoolOpen;
    $('#inPoolBtn').textContent = inPoolOpen ? '역량 닫기' : '＋ 역량 고치기';
  });
  $$('#inEd [data-f]').forEach(el =>
    el.addEventListener('input', e => applyField(inActive, e.target.dataset.f, e.target.value, true)));
  $('#inCt').addEventListener('change', e => applyField(inActive, 'category', e.target.value, true));
  $$('#inEd [data-revert]').forEach(b => b.addEventListener('click', () => {
    const f = b.dataset.revert;
    applyField(inActive, f, aiOf(candOf(inActive), f), false);   // 되돌리기도 같은 경로를 탄다
    $(`#in_${f}`)?.focus({ preventScroll:true });
  }));
  paintInPool();
  const gapBtn = $('#inNextGap');
  if (gapBtn && !nextGapKey()){ gapBtn.disabled = true; gapBtn.textContent = '모두 채웠습니다 · 아래에서 등록'; }
}

function paintInPool(){
  paintPickInto(inDraft[inActive].comp, $('#inPicked'), $('#inPool'), () => {
    paintInPool();
    $('#inCompNote').innerHTML = compNoteHTML(inActive);
    patchLive(inActive);
  });
  $('#inCompNote').innerHTML = compNoteHTML(inActive);
}

/* 값 반영의 유일한 경로. 타이핑과 되돌리기가 같은 코드를 탄다. */
function applyField(k, f, val, fromInput){
  inDraft[k][f] = val;
  if (!fromInput){
    const el = $(`#inEd [data-f="${f}"]`); if (el) el.value = val;
  }
  // title/period/category 는 data-fw 래퍼가 없다 — null 가드가 필수다.
  const wrap = $(`#inEd .field[data-fw="${f}"]`);
  if (wrap) paintFieldState(k, f, wrap);
  if (f === 'result') $('#inRHint').innerHTML = rHintHTML(inDraft[k].result.trim());
  patchLive(k);
}

function paintFieldState(k, f, wrap){
  const c = candOf(k), d = inDraft[k];
  const ai = isAIField(c, f), mine = mineOf(c).includes(f);
  const edited = ai && d[f].trim() !== aiOf(c, f);
  const empty = !cLen(d[f]);
  wrap.classList.toggle('fsrc-you', !ai);
  wrap.classList.toggle('fsrc-edited', edited);
  wrap.classList.toggle('fsrc-todo', mine && empty);
  const b = wrap.querySelector('[data-badge]');
  if (b){
    const st = ai && !edited ? ['mut', 'AI · 근거 확인됨']
             : ai            ? ['acc', empty ? 'AI 문장 지움' : 'AI 문장 → 내가 고침']
             : empty         ? ['warn', '저장소에 없음 · 본인만 아는 것']
             :                 ['ok', '내가 씀'];
    b.className = 'pill ' + st[0]; b.textContent = st[1];
  }
  const rev = wrap.querySelector('[data-revert]'); if (rev) rev.hidden = !edited;
  const sh = wrap.querySelector('[data-short]');
  if (sh) sh.hidden = !(mine && cLen(d[f]) > 0 && cLen(d[f]) < 6);
}

/* 타이핑 경로가 건드리는 노드는 전부 id 로 지목한다.
   위치 의존 셀렉터($('#inOut .hint') 같은)는 한 줄만 추가돼도 조용히 깨진다. */
function patchLive(k){
  const d = inDraft[k], mine = mineOf(candOf(k));
  $$('#inStarBar span').forEach((s, i) => s.classList.toggle('on', cLen(d[FIELDS[i]]) > 0));
  $('#inStarN').textContent = `STAR ${FIELDS.filter(f => cLen(d[f])).length} / 4`;
  $('#inMineN').textContent = `내가 쓴 것 ${mine.filter(f => cLen(d[f])).length} / ${mine.length}`;
  patchRailRow(k);
  paintEvidenceNote(k);
  inCommitArmed = null;              // 초안이 바뀌면 2단 확인 무장은 풀린다
  paintFooter();
}

function patchRailRow(k){
  const row = $(`#inRail [data-k="${k}"]`); if (!row) return;
  row.classList.toggle('sel', k === inActive);
  const ok = isReady(k);
  const p = row.querySelector('[data-rp]');
  p.className = 'pill ' + (ok ? 'ok' : 'warn');
  p.textContent = ok ? '등록 가능' : `${missingOf(k).map(m => m.lab).join('·')} 남음`;
  const e = row.querySelector('[data-re]'), ed = editedFieldsOf(k);
  e.hidden = !ed.length;
  if (ed.length) e.textContent = `${ed.map(f => FLAB[f]).join('·')} 수정함`;
  const t = row.querySelector('b'); if (t) t.textContent = inDraft[k].title || candOf(k).title;
  const rn = $('#inReadyN'); if (rn) rn.textContent = [...inChosen].filter(isReady).length;
}

/* 근거 표시가 편집을 따라 움직인다. evidence 에 필드 귀속 정보가 없으므로
   박스 단위로 한 번만 말한다 — 없는 인과를 지어내지 않는다. */
function paintEvidenceNote(k){
  const c = candOf(k), ed = editedFieldsOf(k), el = $('#inEvNote'); if (!el) return;
  const kept = AI_FIELDS.filter(f => isAIField(c, f) && !ed.includes(f));
  el.innerHTML = !kept.length
    ? '<span style="color:var(--gap)">AI 가 쓴 문장이 남아 있지 않습니다 — 등록해도 아래 근거는 이 경험에 붙지 않습니다.</span>'
    : ed.length
      ? `아래 근거는 <b>${kept.map(f => FLAB[f]).join('·')}</b> 에 대한 것입니다.
         <span style="color:var(--gap)">${ed.map(f => FLAB[f]).join('·')} 는 본인이 고쳤습니다 — 근거는 AI 원문 기준입니다.</span>`
      : '아래 근거에서 <b>S·A</b> 를 뽑았습니다. 사실과 다르면 그 자리에서 고치세요 — 고치면 표시가 남습니다.';
}

/* 매칭 %를 실제로 움직이는 것은 STAR 텍스트가 아니라 역량 태그다.
   문장에만 출처 규율을 걸고 점수 입력에 안 거는 비일관을 이 한 줄로 막는다. */
function compNoteHTML(k){
  const sug = new Set(candOf(k).suggestedCompetencyIds || []);
  const now = new Set(Object.keys(inDraft[k].comp).map(Number));
  const kept  = [...now].filter(id => sug.has(id)).length;
  const added = [...now].filter(id => !sug.has(id)).length;
  const gone  = [...sug].filter(id => !now.has(id)).length;
  return (added || gone)
    ? `AI 제안 ${sug.size}개 중 ${kept}개 유지 · 직접 ${added}개 추가 · ${gone}개 제거`
    : `<span style="color:var(--gap)">${sug.size}개 모두 AI 제안 그대로입니다 — 매칭 점수를 움직이는 값이니 한 번 확인하세요.</span>`;
}

function focusFirstGap(k){
  if (!k) return;
  const first = missingOf(k).find(m => m.f !== 'comp' && m.f !== 'seed');
  const el = first ? $(`#inEd [data-f="${first.f}"]`) : $('#inEd [data-f="task"]');
  el?.focus({ preventScroll:true });
}
function openCand(k){
  inActive = k;
  paintStep2();
  $('#inEd')?.scrollIntoView({ block:'start' });
  focusFirstGap(k);
}
function nextGapKey(){
  const ks = [...inChosen], i = ks.indexOf(inActive);
  for (let n = 1; n <= ks.length; n++){ const k = ks[(i + n) % ks.length]; if (!isReady(k)) return k; }
  return null;
}
function stepCand(d){
  const ks = [...inChosen], i = ks.indexOf(inActive);
  openCand(ks[(i + d + ks.length) % ks.length]);
}

/* ---- 등록 — 3단계 확인 화면 대신 .dlgfoot 이 상시 상태를 진다 ---- */
function buildExp(k){
  const c = candOf(k), d = inDraft[k];
  const edited = editedFieldsOf(k);
  const aiKept = AI_FIELDS.filter(f => isAIField(c, f) && !edited.includes(f));
  return {
    title: d.title.trim(),
    period: d.period.trim() || '기간 미입력',
    category: d.category,
    situation: d.situation.trim(), task: d.task.trim(),
    action: d.action.trim(), result: d.result.trim(),
    competencyIds: Object.keys(d.comp).map(Number),
    strength: { ...d.comp },
    usedInAnswers: 0,
    source: 'AI_INTAKE',
    editedFields: edited,
    // AI 원문이 남은 칸이 하나도 없으면 근거가 따라가지 않는다.
    evidenceRefs: aiKept.length ? c.evidence.map(e => `${e.type} · ${e.ref}`) : [],
  };
}

function matchDeltaHTML(ready){
  if (!ready.length) return '';
  const drafts = ready.map(buildExp);
  const before = computeMatch().overall;
  const backup = DATA.experiences.slice();
  let after = before;
  try {
    DATA.experiences = backup.concat(drafts.map((d, i) => ({ ...d, id: -1 - i })));
    after = computeMatch().overall;
  } finally { DATA.experiences = backup; }     // 예외가 나도 전역을 되돌린다
  const b = Math.round(before * 100), a = Math.round(after * 100);
  return a > b ? `${esc(P().company)} 매칭 ${b}% → <b style="color:var(--matched)">${a}%</b>` : '';
}

function commitHintHTML(ready){
  const skipped = inChosen.size - ready.length;
  // 서명에 comp 를 넣는다 — computeMatch 는 competencyIds·strength 만 읽으므로
  // ready 를 유지한 채 역량 칩만 바꿔도 숫자가 실제로 움직인다.
  const sig = ready.map(k => k + ':' + Object.entries(inDraft[k].comp).sort()
                                        .map(([i, v]) => i + '=' + v).join('|')).join(',');
  if (sig !== inMatchCache.sig) inMatchCache = { sig, html: matchDeltaHTML(ready) };
  const parts = [];
  if (skipped) parts.push(`<span style="color:var(--gap)">아직 비어 있는 ${skipped}건은 등록되지 않습니다</span>`);
  if (inMatchCache.html) parts.push(inMatchCache.html);
  return parts.join(' · ') || '되물은 칸을 채우면 등록 대상이 됩니다.';
}

function paintFooter(){
  const btn = $('#exSave'), gap = $('#exGapHint');
  if ($('#exIntake').hidden){                    // 직접 입력 탭
    btn.textContent = exEditId != null ? '저장' : '등록';
    btn.disabled = false; gap.innerHTML = exGapText; return;
  }
  if (inStep === 1 || !inChosen.size){
    btn.textContent = '등록'; btn.disabled = true;
    gap.innerHTML = '후보를 고르고 직접 쓸 칸을 채우면 등록할 수 있습니다.'; return;
  }
  const ready = [...inChosen].filter(isReady);
  btn.textContent = ready.length ? `${ready.length}건 등록` : '등록할 건 없음';
  btn.disabled = ready.length === 0;
  gap.innerHTML = commitHintHTML(ready);
}

function commitIntake(){
  const ready = [...inChosen].filter(isReady);   // 클릭 시점에 다시 계산한다
  if (!ready.length) return;
  const skipped = inChosen.size - ready.length;
  // 경험 삭제 경로가 없어 등록은 되돌릴 수 없다. 버리는 건이 있으면 한 번 멈춘다.
  if (skipped && inCommitArmed !== ready.length){
    inCommitArmed = ready.length;
    $('#exSave').textContent = `${ready.length}건만 등록 · 한 번 더`;
    $('#exGapHint').innerHTML =
      `<span style="color:var(--gap)">비어 있는 ${skipped}건은 저장되지 않고 사라집니다. 다시 누르면 확정합니다.</span>`;
    return;
  }
  const drafts = ready.map(buildExp), newIds = [];
  drafts.forEach(d => {
    const id = Math.max(0, ...DATA.experiences.map(e => e.id)) + 1;
    DATA.experiences.push({ ...d, id });
    newIds.push(id);
  });
  inCommitArmed = null;
  $('#expDlg').close();
  renderS1(); renderHome(); if ($('#detail').classList.contains('on')) renderDetail();
  onExperienceChanged('ExperienceCreated', newIds);
  toast(`경험 ${drafts.length}건을 등록했습니다 · 평가 재계산을 요청했습니다`);
}

/* 16칸을 채우다 Esc 한 번에 날리지 않게 */
const intakeDirty = () => !$('#exTabs').hidden && !$('#exIntake').hidden && [...inChosen].some(k => {
  const d = inDraft[k]; if (!d) return false;
  const c = candOf(k);
  return FIELDS.some(f => d[f].trim() !== (c[f] || '').trim())
      || d.title.trim() !== c.title || d.period.trim() !== (c.period || '') || d.category !== c.category;
});
let exCloseArmed = false;
function tryCloseExp(){
  if (intakeDirty() && !exCloseArmed){
    exCloseArmed = true;
    toast('작성 중인 인테이크 초안이 있습니다 — 닫으려면 한 번 더 누르세요');
    setTimeout(() => exCloseArmed = false, 4000);
    return;
  }
  exEditId = null; exCloseArmed = false; $('#expDlg').close();
}
$('#exClose').addEventListener('click', tryCloseExp);
$('#expDlg').addEventListener('cancel', e => {          // Esc
  if (intakeDirty() && !exCloseArmed){ e.preventDefault(); tryCloseExp(); }
});

/* ---------- 문항 추가 — application 1:N question 의 생성 경로 ---------- */
let qDlgApp = null;

function openQ(appId){
  qDlgApp = appId;
  const a = DATA.applications.find(x => x.id === appId);
  $('#qDlgSub').textContent = `${a.company} · ${a.position}`;
  $('#qPrompt').value = ''; $('#qLimit').value = 700; $('#qAsks').value = ''; $('#qErr').innerHTML = '';
  $('#qDlg').showModal(); $('#qPrompt').focus();
}

$('#qSave').addEventListener('click', () => {
  const prompt = $('#qPrompt').value.trim();
  if (!prompt){ $('#qErr').innerHTML = '<div class="err">⚠ 문항은 필수입니다.</div>'; return; }
  const asks = $('#qAsks').value.split(',').map(x => x.trim()).filter(Boolean);
  DATA.questions.push({
    id: Math.max(0, ...DATA.questions.map(q => q.id)) + 1,
    applicationId: qDlgApp,
    charLimit: Math.max(100, +$('#qLimit').value || 700),
    prompt,
    intent: '직접 등록한 문항입니다. 공고의 평가 포인트를 확인해 의도를 채워 두면 점검이 정확해집니다.',
    asks: asks.length ? asks : ['이 문항이 묻는 것'],
    usedExperienceIds: [],
    draft: '',
    aiDraft: '',
  });
  $('#qDlg').close();
  s4qid = null; dtTab = 'essay';
  renderDetail(); renderHome(); go('detail');
  toast('문항을 추가했습니다 · 자소서 에디터로 이동합니다');
});
$('#qCancel').addEventListener('click', () => $('#qDlg').close());
