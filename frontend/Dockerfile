# syntax=docker/dockerfile:1
# 웹(Vue + Vite). 기준 버전은 docs/dev-environment.md — 여기 이미지 태그가 곧 팀 표준이다.
#
# 빌드 단계에서 정적 파일(dist/)을 만들고, nginx 가 그걸 서빙하면서 /api 를 API 컨테이너로 넘긴다.
# 프록시를 두는 이유는 dev 의 vite 프록시와 같다 — 화면과 API 가 같은 오리진이어야 세션 쿠키가 그냥 붙는다.

FROM node:24-alpine AS build
WORKDIR /src

# lock 파일 기준으로 정확히 같은 의존성을 깐다(npm ci). package.json 이 안 바뀌면 이 레이어는 캐시다.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .
# 컨테이너의 웹은 언제나 실제 API 를 본다. 목은 npm run dev 의 기본값일 뿐이다.
# VITE_ 값은 빌드 시점에 번들에 박히므로 여기서 정해야 한다.
ENV VITE_API_MOCK=0
# build = vitest run && vite build — 테스트를 통과해야 dist 가 나온다(package.json).
RUN npm run build

FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
