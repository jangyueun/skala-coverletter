"""테스트는 절대 실제 Claude 를 부르지 않는다.

ai/.env 에 키가 있으면 app.core.config 가 import 되면서 키를 읽고, main.build_provider 가 ClaudeAiProvider 를 고른다 —
그 상태로 계약 테스트가 돌면 진짜 API 를 부르며 돈이 나가고 web_fetch 로 몇 분씩 걸린다. 실제로 그랬다.
그래서 config 가 import 되기 전에 AI_FORCE_MOCK 을 켠다(conftest 는 테스트 모듈보다 먼저 import 된다)."""

import os

os.environ["AI_FORCE_MOCK"] = "1"
