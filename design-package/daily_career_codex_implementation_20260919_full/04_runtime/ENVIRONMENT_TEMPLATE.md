# 환경변수 템플릿 — 값은 저장소에 커밋하지 말 것

실제 키/secret 값은 패키지에 포함하지 않는다.

예상 범주:
- PostgreSQL URL / user / password
- Google OIDC client id / secret
- session/cookie 관련 운영 설정
- OpenAI provider key
- Notion integration credential reference
- Slack app/webhook credentials
- Web Push VAPID public/private key

Codex는 `.env.example`에는 변수 이름과 설명만 넣고 실제 secret을 생성하거나 커밋하지 않는다.
