# 집 PC 24/7 배포 (폰 외부망 접속, 도메인 없이)

집에 있는 남는 컴퓨터에서 **DB + 서버**를 Docker로 상시 구동하고, **Tailscale Funnel**로
무료 고정 HTTPS 주소를 받아 폰이 어느 망(WiFi/LTE)에서든 접속하게 한다.
**도메인 불필요 · 공유기 포트포워딩 불필요 · 무료.**

```
[폰/에뮬] → https://damoim.<tailnet>.ts.net → Tailscale(집PC) → localhost:8080 → app → postgres
```

---

## 0. 준비물

- 남는 컴퓨터 1대 (Windows / macOS / Linux) — **항상 켜둘 것**
- **Docker** 설치
  - Windows/macOS: Docker Desktop (설정 → **"시스템 시작 시 실행" 켜기**)
  - Linux: `curl -fsSL https://get.docker.com | sh`
- **Tailscale 계정**(무료, Google/GitHub 로그인) — 도메인·신용카드 불필요
- **카카오 앱 ID**(숫자) — 이미 카카오 로그인 쓰고 있으니 콘솔에 있음

---

## 1. 소스 가져오기 (집 PC에서)

```bash
git clone <이 서버 레포 주소> damoim-server
cd damoim-server
```

## 2. 환경변수 채우기

```bash
cp .env.prod.example .env
```
`.env` 열어서:
- `DB_PASSWORD` — 원하는 강한 값
- `JWT_SECRET` — `openssl rand -base64 48` 로 생성한 값 (**한 번 정하면 바꾸지 말 것** — 바뀌면 로그인 다 풀림)
- `KAKAO_APP_ID` — 카카오 콘솔 → 앱 설정 → 요약 정보 → "앱 ID"(숫자)

## 3. DB + 서버 실행

```bash
docker compose -f docker-compose.prod.yml up -d --build
```
첫 빌드는 몇 분 걸린다(gradle 의존성). 확인:
```bash
docker compose -f docker-compose.prod.yml ps        # postgres, app 둘 다 up
curl http://localhost:8080/actuator/health          # {"status":"UP"}
```

## 4. Tailscale Funnel로 외부 노출 (도메인 없이 고정 주소)

집 PC **호스트에** Tailscale 설치:
- Windows/macOS: https://tailscale.com/download 앱 설치 후 로그인
- Linux: `curl -fsSL https://tailscale.com/install.sh | sh` 후 `sudo tailscale up`

그다음 (한 번만):
```bash
tailscale funnel --bg 8080
```
- 처음이면 "Funnel을 켜라"는 **링크가 출력**된다 → 브라우저로 열어 한 번 승인(무료).
- 성공하면 고정 주소가 표시된다: **`https://<컴퓨터이름>.<tailnet>.ts.net`**
- `--bg` 라서 **재부팅해도 자동 유지**된다. 현재 상태 확인: `tailscale funnel status`

> Funnel은 localhost:8080(위 3번에서 뜬 앱)을 그대로 HTTPS로 공개한다.
> 이 주소는 **바뀌지 않으므로** 클라에 한 번만 넣으면 된다.

## 5. 앱(클라)에서 서버 주소 지정

`~/StudioProjects/Damoim/local.properties`:
```properties
server.base.url=https://<컴퓨터이름>.<tailnet>.ts.net
```
앱 다시 빌드/설치. 이제 폰이 LTE에서도 이 서버에 붙는다.

**외부 접속 확인** (폰 브라우저나 딴 PC에서):
```
https://<컴퓨터이름>.<tailnet>.ts.net/actuator/health  →  {"status":"UP"}
```

---

## 운영 명령어

```bash
# 로그
docker compose -f docker-compose.prod.yml logs -f app
# 코드 업데이트 후 재배포
git pull && docker compose -f docker-compose.prod.yml up -d --build
# 중지 / 재시작
docker compose -f docker-compose.prod.yml stop
docker compose -f docker-compose.prod.yml up -d
# 완전 삭제(DB 데이터 + 업로드 사진까지) — 주의
docker compose -f docker-compose.prod.yml down -v
# Funnel 상태 / 끄기
tailscale funnel status
tailscale funnel --bg off
```
DB 데이터는 `damoim-pgdata` 볼륨에 영구 저장돼 재시작·재배포에도 유지된다.

---

## 업로드 파일(사진·첨부) 보관

`STORAGE_PROVIDER=local`(기본)이면 업로드된 바이트는 **서버 컨테이너의 디스크**에 저장된다.
저장 경로는 `STORAGE_LOCAL_DIR`(compose에서 `/var/lib/damoim/storage`로 고정 주입)이고,
그 경로는 **`damoim-storage` 볼륨**으로 마운트돼 있다 — 볼륨이 없으면 컨테이너 writable layer에
쌓이므로 `up -d --build` 한 번에 업로드 파일이 전부 사라진다(DB에는 이미지 key만 남아 GET이 404).

```bash
# 저장된 파일 확인
docker compose -f docker-compose.prod.yml exec app ls -R /var/lib/damoim/storage
```

- **경고**: `down -v`는 DB뿐 아니라 이 볼륨(= 사용자 사진 전부)도 삭제한다.
- **볼륨 도입 이전(재배포 때마다 파일이 날아가던 시절)에 올라간 사진은 이미 소실됐다.** DB의
  `users.profile_image_key` / `clubs.image_key`는 남아 있어 URL은 계속 발급되지만 바이트가 없어
  영구 404다 → 볼륨을 붙여도 **복구 불가, 사용자가 다시 업로드해야 한다**(앱은 이 경우 이니셜로 폴백).
- 배포 절차: `up -d --build` 후 사진 1건을 새로 업로드 → 위 `ls`로 파일 확인 → 다시
  `up -d --build` 하고 같은 파일이 남아 있으면 볼륨이 정상 동작하는 것.

---

## 자주 겪는 문제

- **`KAKAO_APP_ID 필요` / 부팅 실패**: `.env`의 KAKAO_APP_ID가 비었거나 0. 숫자 앱ID 넣기.
- **로그인이 자꾸 풀림**: `JWT_SECRET`을 매번 바꿨거나 비워둠 → 고정값으로 한 번만 정하기.
- **Funnel 주소가 502/안 열림**: 3번 `curl localhost:8080/actuator/health`가 UP인지 먼저 확인. 앱이 떠야 Funnel도 응답한다.
- **재부팅 후 안 뜸**: Docker Desktop "시스템 시작 시 실행" 미설정(Win/Mac). Linux는 `sudo systemctl enable docker`. Tailscale은 데몬이 부팅 시 자동 실행되고 `--bg` funnel 설정도 유지됨.
- **사진이 회색/이니셜로만 보임**: 예전에 올린 파일이 볼륨 없이 컨테이너에 저장돼 재배포 때 소실된 경우. 다시 업로드하면 정상(위 "업로드 파일 보관" 참고).

---

## 부록: 나중에 도메인이 생기면 (Cloudflare Tunnel)

본인 도메인을 Cloudflare에 붙이면 `https://api.내도메인.com` 같은 주소도 쓸 수 있다.
`cloudflare/cloudflared` 컨테이너를 compose에 추가하고 대시보드에서 터널 토큰을 발급,
Public Hostname의 Service를 `http://app:8080`으로 지정하면 된다(이 경우 app 포트 매핑 불필요).
지금은 도메인이 없으니 위 Tailscale Funnel로 충분하다.
