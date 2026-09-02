#!/bin/bash
# 목업 빌드 — shell + data + render 를 합치고, 브라우저에서 못 쓰는 코드를 차단한다.
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
S="$ROOT/src"
M="$ROOT/index.html"

# ① Node 전용 코드 제거 (검증용으로 붙었다가 남는다)
perl -0pi -e 's/^\s*module\.exports[^\n]*\n//mg' "$S/data.js" "$S/render.js"

# ② 브라우저에서 죽는 코드가 남아 있으면 빌드 실패
if grep -nE '(^|[^a-zA-Z_.])(module\.|require\(|exports\.|__dirname|process\.env)' "$S/data.js" "$S/render.js"; then
  echo "❌ 브라우저에서 못 쓰는 코드가 있다"; exit 1
fi

# ③ 조립
{ cat "$S/shell.html"; echo '<script>'; cat "$S/data.js"; cat "$S/render.js"; echo '</script>'; echo '</body>'; echo '</html>'; } > "$M"

# ④ 구문 검사 + 정의 없는 CSS 클래스 검사
node --input-type=module -e "
const fs=await import('fs');
const html=fs.readFileSync('$M','utf8');
const js=html.match(/<script>([\s\S]*)<\/script>/)[1];
try { new Function(js); } catch(e) { console.error('❌ JS 구문 오류:', e.message); process.exit(1); }
const css=html.slice(0, html.indexOf('</style>'));
const used=[...html.matchAll(/class=\"([^\"\$]+)\"/g)].flatMap(m=>m[1].split(/\s+/)).filter(Boolean);
const missing=[...new Set(used)].filter(c=>!css.includes('.'+c));
if (missing.length) console.warn('⚠ CSS 정의가 없는 클래스:', missing.join(', '));
console.log('✅ 빌드 OK ·', html.length, 'bytes');
"
