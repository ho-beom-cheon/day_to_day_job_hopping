# 운영형 Docker 로컬 실행·백업·복원

운영형 구성은 `compose.yaml`에 `compose.prod.yaml`을 겹쳐 사용한다. Frontend는 production runtime 이미지를 사용하고 소스 mount가 없으며, Backend는 `prod` profile, 개발 인증 우회 비활성, secure session cookie를 강제한다. 운영 비밀값은 Git에 넣지 않은 별도 env 파일로 주입한다.

```powershell
docker compose --env-file .env.prod.local -f compose.yaml -f compose.prod.yaml config --quiet
docker compose --env-file .env.prod.local -f compose.yaml -f compose.prod.yaml up --build -d --wait
docker compose --env-file .env.prod.local -f compose.yaml -f compose.prod.yaml ps
```

`.env.prod.local`에는 고유한 `COMPOSE_PROJECT_NAME`, `DB_VOLUME_NAME`, 강한 `DATABASE_PASSWORD`, 실제 `APP_ORIGIN`과 OAuth/외부 공급자 secret reference만 둔다. 파일은 커밋하지 않는다. 이 저장소의 Nginx는 로컬 인수용 HTTP origin이다. `SESSION_COOKIE_SECURE=true`이므로 로그인까지 검증하려면 앞단 HTTPS 종단을 붙여야 한다. HTTP에서 임시로 secure cookie를 끄는 구성은 운영 인수로 계산하지 않는다.

## PostgreSQL 백업과 격리 복원 검증

현재 공개 서비스는 `compose.yaml`과 `compose.public.yaml`을 사용한다. 다음 명령은 실행 중인 PostgreSQL을 custom-format으로 읽기 전용 백업한다. PowerShell의 `>`는 바이너리 덤프를 손상시킬 수 있으므로 사용하지 않는다. 스크립트는 컨테이너 내부에서 `pg_dump`한 뒤 `docker cp`로 복사하고, 원본·복사본 SHA-256 및 `pg_restore --list`를 확인한다. 결과는 Git 추적에서 제외된 `backups/`에 `.dump`와 `.dump.json`으로 보관한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\database-backup.ps1 -Target Public
```

출력된 **실제 백업 경로**를 아래 `-BackupFile`에 넣어 격리 복원을 검증한다. 복원 스크립트는 새 임시 Docker volume과 포트를 열지 않은 PostgreSQL 컨테이너를 만들고, SHA-256 확인 후 `pg_restore --exit-on-error`와 Flyway·사용자·과정·학습 항목 조회를 실행한다. 성공·실패와 관계없이 임시 컨테이너와 volume을 정리하며 공개 DB에는 복원 명령을 실행하지 않는다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\database-restore-drill.ps1 -BackupFile .\backups\daily-career-public-YYYYMMDDTHHMMSS-xxxxxxxx.dump
```

별도 학습 QA DB를 검증할 때는 백업 명령의 `-Target QA`만 사용한다. 이 수동 명령만으로는 자동 주기·외부 보관·암호화·실제 장애 시 교체 절차까지 제공하지 않는다. `backups/`에는 실제 사용자 데이터가 있으므로 Git이나 채팅에 파일 내용을 넣지 않는다. 일반 재기동·보존 검증에는 `docker compose down -v`를 사용하지 않는다.

## Web Push 서버 키 준비

공개 HTTPS 환경의 `.env`에서 `APP_ORIGIN`을 확인한 뒤 아래 명령을 실행한다. 스크립트는 P-256 VAPID 키 쌍과 별도 구독 암호화 키를 생성하고 비밀값을 출력하지 않는다. 이미 키가 모두 있으면 유지하며 일부만 있으면 자동 교체를 거부한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-web-push-keys.ps1
docker compose -f compose.yaml -f compose.public.yaml up -d --no-deps --force-recreate --wait backend
docker compose -f compose.yaml -f compose.public.yaml exec -T nginx nginx -s reload
```

키와 `.env`는 DB 백업과 별도로 안전하게 보관한다. 구독을 만든 뒤 키를 교체하면 기존 브라우저 구독을 다시 만들어야 할 수 있다. 서버 준비는 [외부 연동 설정](https://hocheon.tail4ddad6.ts.net/settings/integrations)의 버튼 활성화로 확인한다. 실제 수신 검증에는 사용자가 HTTPS 브라우저에서 **이 브라우저 연결**을 누르고 알림 권한을 허용한 뒤, 본인 기기에서 알림을 확인해야 한다. 서버 키 준비만으로 사용자 구독이나 전달 성공을 주장하지 않는다.

## 접속

- 대표 화면: <http://localhost:8080/>
- 설정한 `HTTP_PORT`가 다르면 같은 경로에서 포트만 바꾼다.
- 실제 운영 배포에서는 HTTPS origin과 Google callback을 동일한 외부 origin으로 맞춘다.
