# MartPick (마트픽)

장바구니에 담은 상품을 기준으로, 선택한 지역의 판매점별 총액을 비교하는 웹서비스입니다.
한국소비자원 생필품 가격 정보(공공데이터)를 수집해 PostgreSQL에 저장하고, 그 데이터로 비교합니다.

> **상태**: 개발 중입니다. 운영 배포는 하지 않았고, 이 문서의 실행 방법은 로컬 개발 환경 기준입니다.
> 기능 요구사항의 기준은 [docs/PROJECT_SPEC.md](docs/PROJECT_SPEC.md), 공공데이터 API에서 실제 확인한 사실은 [docs/API_RESEARCH.md](docs/API_RESEARCH.md), AI Agent와의 협업 기록은 [AI_COLLABORATION.md](AI_COLLABORATION.md)에 있습니다.

## 가격 데이터에 대한 안내

- **조사 가격이며 실시간 판매 가격·재고를 보장하지 않습니다.** 화면에도 같은 문구를 표시합니다.
- 모든 가격은 **기준 조사일 2026-09-25** 하나로 고정되어 있습니다(설정 `martpick.inspect-day`). 다른 조사일의 가격은 섞지 않습니다.
- 데이터 출처는 한국소비자원 생필품 가격 정보 공공데이터 API이며, 사용자 요청 시 외부 API를 호출하지 않고 **미리 수집해 DB에 저장한 데이터**로 비교합니다.
- 비교 지역은 시·도 단위입니다.
- 기준 조사일에 가격 데이터가 없는 상품도 검색할 수 있습니다. 이런 상품은 비교 결과에서 가격 정보 없음으로 처리됩니다.

### 데이터 규모 (스냅샷)

아래 수치는 **2026-10-01에 확인한 값**이며 고정된 값이 아닙니다. 다시 수집하거나 조사일이 바뀌면 달라집니다.

| 항목 | 값 | 확인 방법 |
|---|---|---|
| 상품 | 604개 | 개발 DB 조회, `docs/PROJECT_SPEC.md`의 구현 현황과 일치 |
| 가격 | 113,843건 | 개발 DB 조회, `docs/PROJECT_SPEC.md`의 구현 현황과 일치 |
| 기준 조사일에 가격이 있는 상품 | 583개 | `docs/API_RESEARCH.md` 10장의 첫 전체 수집 기록 |

### `(본사)` 판매점 제외 규칙과 한계

- 판매점명에 `(본사)`가 붙은 판매점은 매장별 가격 비교 대상에서 제외합니다. 판별 기준은 판매점명 패턴 `(본사)`와, 개발자가 직접 확인한 제외 ID 목록(`62`, `115`, `150`, `2160`)입니다. 지역 목록을 만들 때도 제외합니다.
- 이 4곳(CU, 세븐일레븐, GS25, 이마트24의 본사)에는 기준 조사일 가격이 있습니다. 그 가격이 전국 공통 가격인지, 권장 가격인지, 본사가 직접 판매하는 가격인지는 **확인되지 않았습니다.** 그래서 "본사는 판매하지 않는다"고 단정하지 않고, **개별 방문 가능 매장으로 확인되지 않아 비교 대상에서 제외**합니다.
- **한계**
  - 판매점 데이터의 편의점 업태(`CS`)는 위 4곳뿐이고 개별 편의점 매장은 등록되어 있지 않습니다. 따라서 이 규칙을 적용하면 **결과적으로 편의점이 비교 결과에 나타나지 않습니다.** 편의점 업태 전체를 제외하는 규칙은 아닙니다.
  - 판별 규칙이 판매점명 패턴과 사람이 확인한 ID 목록에 의존하므로, 같은 성격의 다른 판매점이 데이터에 추가되면 자동으로 제외되지 않을 수 있습니다.
  - `본점`(예: 신세계백화점본점)이나 `(주)`가 들어간 판매점은 개별 매장이므로 제외하지 않습니다.
  - 광주·전남은 (구)코드와 새 코드가 함께 있어, 조회한 상품 기준으로 가격이 있는 새 코드만 지역 목록에 표시합니다. 서로 다른 판매점 ID를 같은 매장으로 병합하지는 않습니다.
- 자세한 근거는 `docs/PROJECT_SPEC.md`의 R8·R12와 `docs/API_RESEARCH.md` 3-4, 6-6을 참고하세요.

## 주요 기능

- 상품 탐색: 상품명 검색과 30개 단위 페이지 이동. 검색어를 공백으로 나눈 단어가 모두 (공백을 뺀) 상품명에 들어 있으면 찾습니다(`서울우유 흰우유`·`서울우유흰우유`·`우유 1L`, 대소문자 무시, 앞뒤 공백 제거).
- 장바구니
  - 비회원: 세션 장바구니
  - 회원: DB 장바구니
  - 상품당 수량은 1~99개
  - 로그인해도 비회원 장바구니를 회원 장바구니에 자동으로 합치지 않습니다. 로그아웃해도 회원의 DB 장바구니는 유지됩니다.
  - 비회원 장바구니에 상품이 있으면 장바구니 화면에 "로그인하면 지금 담은 상품은 회원 장바구니로 옮겨지지 않습니다" 안내와 로그인·회원가입 링크가 표시됩니다(로그인한 회원에게는 표시하지 않음).
- 가격 비교: 선택한 지역의 판매점별 총액 비교
  - 장바구니 상품의 가격이 모두 확인된 판매점은 **완전 비교** 목록(총액순)
  - 일부 상품의 가격만 있는 판매점은 **부분 비교** 목록(확인된 상품 합계와 누락 상품 표시)
  - 각 목록은 기존 정렬(완전 비교는 총액순, 부분 비교는 판매점 번호순) 그대로 **처음 10곳만 펼쳐 보여 주고**, 나머지는 "나머지 N곳 보기"(`<details>`)로 접어 둡니다. 10곳 이하이면 접기 표시가 없고, 판매점은 빠지지 않습니다.
  - 장바구니 상품 중 가격이 없는 상품은 결과 화면에서 "기준 조사일에 전국 어디에도 가격이 없는 상품"과 "선택한 지역에만 가격이 없는 상품"으로 나누어 안내합니다. 장바구니 화면에도 전국에 가격이 없는 상품에 "기준 조사일 가격 데이터 없음"이 표시됩니다. 상품은 자동으로 빼지 않으며 총액·비교 방식은 바뀌지 않습니다.
  - 결과 화면 상단에서 같은 장바구니로 다른 지역을 골라 "다시 비교"할 수 있습니다. 선택한 지역은 요청 주소(`/compare?areaCode=`)에만 쓰고 저장하지 않습니다.
- 회원: 아이디·비밀번호 회원가입, 세션 기반 로그인·로그아웃. 장바구니에서 회원가입으로 이동해도 로그인 후 원래 화면(장바구니 등)으로 돌아옵니다. 가입 후 자동 로그인은 하지 않습니다.

## 기술 스택과 실행 환경

| 구분 | 내용 |
|---|---|
| 언어 | Java 17 (Gradle Toolchain으로 고정) |
| 프레임워크 | Spring Boot 3.5.16 (Web MVC, Validation, Security), Spring Data JPA |
| 화면 | Thymeleaf 서버 사이드 렌더링, HTML/CSS (프론트엔드 프레임워크 없음) |
| DB | PostgreSQL 16 (로컬은 `compose.yaml`), 스키마는 Flyway |
| 인증 | Spring Security 세션 기반 폼 로그인, BCrypt, CSRF 기본값 유지 |
| 테스트 | JUnit 5, AssertJ, Mockito, Spring Security Test |
| 빌드 | Gradle 8.14.5 Wrapper |

로컬 실행에는 JDK(Gradle이 Java 17 Toolchain을 사용), Docker(PostgreSQL 컨테이너)가 필요합니다.
아래 명령은 Windows(`gradlew.bat`) 기준이며, 이 환경에서 실행해 확인한 것입니다.

## 로컬 실행

1. DB를 실행합니다.

   ```bash
   docker compose up -d
   ```

   `compose.yaml`은 PostgreSQL 16 컨테이너 `martpick-postgres`를 만들고 DB `martpick`(개발용)과 `martpick_test`(테스트용)를 둡니다.
   `martpick_test`는 `docker/postgres/init/01-create-test-database.sql`이 **데이터 볼륨이 비어 있는 최초 기동 때만** 만듭니다.

2. 앱을 실행합니다. 스키마는 시작할 때 Flyway가 적용합니다.

   ```bash
   ./gradlew.bat bootRun
   ```

   이 방식은 **개발 DB(`martpick`)** 에 연결합니다. 기본 주소는 `http://localhost:8080`입니다.

### 환경변수

| 이름 | 기본값 | 설명 |
|---|---|---|
| `MARTPICK_DB_HOST` | `localhost` | DB 호스트 |
| `MARTPICK_DB_PORT` | `5432` | DB 포트 |
| `MARTPICK_DB_NAME` | `martpick` | 개발 DB 이름 |
| `MARTPICK_DB_USER` | `martpick` | DB 사용자 |
| `MARTPICK_DB_PASSWORD` | `martpick` | DB 비밀번호 |
| `DATA_GO_KR_SERVICE_KEY` | (비어 있음) | 공공데이터포털 API 인증키. 수집할 때만 필요합니다. |
| `MARTPICK_TEST_DB_NAME` | `martpick_test` | 테스트가 사용하는 DB 이름 (`src/test/resources/application-test.yml`) |

- DB 기본값(`martpick`/`martpick`)은 **로컬 개발 전용**이며 `application.yml`과 `compose.yaml`에 그대로 적혀 있습니다. 운영에서 그대로 쓰면 안 되고, 운영 값은 환경변수로 주입해야 합니다.
- 인증키와 운영 DB 비밀번호는 코드, 문서, 로그, Git에 넣지 않습니다. 설정 파일(`application*.yml`)은 커밋하고 `.env`, `application-local.*`는 `.gitignore`에 있습니다.
- Spring Boot 표준 방식(`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_PASSWORD` 등)으로도 DB 접속 정보를 지정할 수 있습니다(`bootRun` 기준). `bootRunTest`는 이 값들을 전달하지 않습니다(아래 참고). 비밀번호는 명령행 인자가 아니라 환경변수로 넘기세요.

## 공공데이터 수집

수집은 스케줄러 없이 **개발자가 수동으로, 웹 서버 없이 1회** 실행합니다. 사용자 요청과 무관합니다.

```bash
./gradlew.bat bootRun --args="--martpick.collect.run=true --spring.main.web-application-type=none"
```

- 상품 API 응답의 **전체 상품**을 대상으로 기준 조사일 가격을 조회합니다. 1회 수집에 약 607회(예상값)의 API 호출이 필요하며, 확인된 호출 제한은 하루 2,000회입니다.
- `DATA_GO_KR_SERVICE_KEY` 환경변수가 필요합니다.
- 응답을 모두 받아 검증한 뒤 **단일 트랜잭션으로 저장**합니다. 기준 조사일 가격은 전체 교체되므로, 실행 전에 대상 DB를 확인하세요. 대상 DB는 `--spring.datasource.url=…`(명령행 인자) 또는 `MARTPICK_DB_*` 환경변수로 지정하며 명령행 인자가 우선합니다.
- 이전 성공 이력과 크게 다르면(가격 건수 급감 등) 저장하지 않고 중단하는 안전장치가 있습니다.
- 수집 후 프로세스가 스스로 종료되는지는 실제 실행으로 **아직 확인하지 않았습니다**(컨텍스트 시작과 수집 실행기 동작은 `NonWebCollectRunTest`로 확인).
- 상세 절차와 정책은 `docs/PROJECT_SPEC.md` 4장을 따릅니다.

## 테스트

테스트는 개발 DB가 아니라 **테스트 DB(`martpick_test`)** 를 씁니다. 실행 전에 `docker compose up -d`로 로컬 DB가 떠 있어야 합니다(기본 테스트도 로컬 DB가 필요합니다).

```bash
./gradlew.bat test
```

특정 테스트만 실행하려면 다음과 같이 합니다.

```bash
./gradlew.bat test --tests "*.CartServiceTest"
```

- Gradle은 입력이 바뀌지 않으면 `test`를 실행하지 않고 `UP-TO-DATE`로 건너뜁니다. 실제 재실행이 필요하면 `./gradlew.bat cleanTest test`를 사용하세요.
- DB를 쓰는 테스트(`@SpringBootTest`, `@DataJpaTest` 등)는 `@ActiveProfiles("test")`를 명시해야 합니다. IDE에서 실행해도 개발 DB에 연결되지 않게 하기 위해서입니다. `build.gradle`의 `test` 태스크도 `spring.profiles.active=test`를 지정합니다.
- 대부분의 DB 테스트는 `@Transactional` 롤백으로 정리됩니다. 실제로 커밋하는 테스트는 자기가 만든 데이터만 지웁니다(`LoginGuardWebServerTest`는 테스트 회원 2명, `MemberCartConcurrencyTest`는 `cartConc%` 회원과 자기 상품). `DataCollectorTest`는 매번 테스트 DB의 `region`·`store`·`product`·`price`·`member`·`cart_item`·`collection_run` 테이블을 **전체 삭제**합니다. 테스트 DB를 직접 사용하는 중이면 먼저 확인하세요.
- 전체 삭제 전에 `TestDatabaseGuard`(`src/test/java/com/martpick/testsupport`)가 실제 연결 대상을 확인합니다. 서버가 알려주는 DB 이름과 드라이버 URL의 DB 이름이 모두 `martpick_test`와 정확히 같지 않거나 확인할 수 없으면, 아무것도 삭제하지 않고 테스트가 실패합니다. `SPRING_DATASOURCE_URL`·`MARTPICK_TEST_DB_NAME`·`-Dspring.datasource.url`로 다른 DB를 지정해도 막힙니다.
- 이 검증은 `DataCollectorTest`의 전체 삭제에만 적용됩니다. 다른 테스트에는 연결 대상 검증이 없고, 테스트 컨텍스트가 시작될 때 Flyway가 연결된 DB에 먼저 마이그레이션을 적용합니다. 그러므로 테스트를 실행하는 셸에 `SPRING_DATASOURCE_URL`이나 `MARTPICK_TEST_DB_NAME`을 설정하지 마세요.

### 테스트 DB로 앱 실행: `bootRunTest`와 일반 `bootRun`의 차이

화면 확인처럼 앱을 직접 띄우되 개발 DB를 건드리고 싶지 않을 때는 `bootRunTest`를 씁니다.

```bash
./gradlew.bat bootRunTest -PtestServerPort=18080
```

`-PtestServerPort`는 선택이며, 생략하면 8080입니다. 이미 8080을 쓰는 서버가 있으면 지정하세요.

서버는 `127.0.0.1`에만 바인딩되므로 **같은 PC에서만** 접속할 수 있습니다(`http://localhost:포트`). 다른 기기(예: 휴대폰)에서의 접속은 지원하지 않습니다.

| | `bootRun` | `bootRunTest` |
|---|---|---|
| 연결 DB | 개발 DB `martpick` | 테스트 DB `martpick_test` (고정) |
| DB 지정 | `application.yml` 기본값, 환경변수, 명령행 인자 | 시스템 속성으로 URL을 직접 지정. 이름은 `martpick_test`로 고정 |
| 명령행 인자(`--args`) | 사용 가능 | **거부** (접속 정보가 덮어써질 수 있음) |
| 환경변수 | 모두 전달 | 필요한 것만 전달(`PATH` 등 실행에 필요한 것과 `MARTPICK_DB_USER`, `MARTPICK_DB_PASSWORD`). `SPRING_*`, `DATA_GO_KR_SERVICE_KEY` 등은 전달하지 않음 |
| 서버 접속 | `server.address` 미설정 | `127.0.0.1`에만 바인딩 (이 PC에서만 접속) |
| 설정 파일 | 기본 규칙(작업 디렉터리의 외부 설정 파일 포함) | 위치를 `classpath:/application.yml`로 고정. `application-local.*` 같은 외부 설정 파일은 읽지 않음 |

`bootRunTest`는 DB 사용자·비밀번호로 `MARTPICK_DB_USER`, `MARTPICK_DB_PASSWORD`만 전달합니다. `SPRING_DATASOURCE_PASSWORD`로 지정한 값은 전달되지 않아 기본 비밀번호로 접속을 시도하며, 인증에 실패하면 앱이 시작되지 않고 종료됩니다.

실행 전에 Gradle이 "DB 대상"과 전달하지 않은 환경변수 이름(값은 출력하지 않음)을 출력하고, 다음 경우에는 앱을 시작하지 않고 중단합니다.

- `--args` 또는 JVM 인자를 준 경우
- `MARTPICK_DB_HOST`가 `localhost`/`127.0.0.1`이 아닌 경우
- 포트 값이 숫자가 아닌 경우

**주의사항**

- `bootRun`에 `-Dspring.profiles.active=test` 같은 프로필만 주는 방식은 **테스트 DB를 쓰지 않습니다.** 테스트 DB 설정(`application-test.yml`)은 `src/test/resources`에만 있어서 `bootRun` 클래스패스에 없고, 프로필 이름만 `test`로 활성화되어 개발 DB 기본값(`martpick`)이 그대로 적용됩니다. 실제로 이 방식으로 개발 DB에 연결된 사고가 있었습니다([AI_COLLABORATION.md](AI_COLLABORATION.md) 참고).
- 어떤 방식으로 띄웠든 쓰기 작업 전에는 기동 로그의 Flyway 줄 `Database: jdbc:postgresql://…/<DB 이름>`에서 실제 접속 DB를 확인하세요.
- `bootRunTest`가 Gradle 단계에서 막는 것은 위 경우뿐입니다. 이 태스크를 거치지 않고 직접 띄운 서버(IDE 실행, `java -jar` 등)의 접속 대상은 보장하지 않습니다.

## 보안 현황과 운영 전 결정 사항

**적용된 것 (개발 환경 기준)**

- 비밀번호는 BCrypt로 해시해 저장하고 로그에 남기지 않습니다.
- CSRF 보호(Spring Security 기본값), 로그인 시 세션 ID 재발급, 로그아웃은 POST와 CSRF 토큰으로만 가능합니다.
- 허용 목록 밖의 경로는 거부합니다(`denyAll`, 허용: 상품·장바구니·비교·로그인·가입·정적 CSS). 거부한 요청의 상태 코드는 다음과 같습니다.
  - 처리하는 컨트롤러가 없는 주소(예: `/admin`, `/actuator/*`, `GET /logout`, `/products/`)는 **404**입니다. 로그인 여부와 관계없습니다.
  - 컨트롤러는 있지만 허용 목록에 없는 경로와 CSRF 검증에 실패한 요청은 **403**입니다. CSRF 실패는 경로와 관계없이 항상 403입니다.
  - 상태 코드를 확인할 수 없는 경우에는 403으로 거부합니다. 이 구분은 응답 상태만 정하며 어떤 요청도 새로 허용하지 않습니다.
- 403, 404, 그 밖의 요청 오류(4xx), 서버 오류(5xx)는 기존 화면 레이아웃의 한국어 오류 화면으로 안내하며 HTTP 상태 코드와 화면 내용이 일치합니다. 예외 이름, 스택 트레이스, DB 정보는 표시하지 않습니다.
- 검색어에 제어 문자(NUL 포함)가 있으면 DB를 조회하지 않고 **400**과 안내 문구를 표시하며 전체 상품 화면을 보여 줍니다. PostgreSQL이 NUL을 처리하지 못해 이전에는 500이 났습니다.
- 로그인 후 복귀 주소(`continue`)는 허용 목록(`/products`, `/cart`, `/compare`의 정해진 쿼리)만 받습니다. 회원가입을 거쳐도 화면 표시와 이동 단계마다 같은 검증을 다시 적용합니다.
- 회원 장바구니는 요청 값이 아니라 로그인한 인증 정보로만 식별합니다.

**운영 환경에서 아직 결정하거나 적용하지 않은 것**

- HTTPS, 세션 쿠키의 `Secure`·`SameSite` 속성, 프록시 뒤에서의 forward-headers 설정
- 세션 만료 시간(명시 설정 없음, 서버 기본값 사용)
- 로그인 시도 횟수 제한 (없음)
- CSP 등 추가 보안 헤더
- 운영 DB 접속 정보 주입 방식과 기본 비밀번호 폴백 제거 방식

위 항목은 **적용되어 있지 않습니다.** 운영 배포 전에 정해야 합니다.

## Spring Boot 지원 상태

Spring Boot 3.5.x는 **OSS 지원이 종료되었습니다.** 요구사항에 따라 3.5.x를 유지하며, 확인 시점(2026-10-01)에 Maven Central에 배포된 3.5 라인 최신 버전인 3.5.16에 고정했습니다.

2026-10-01에 공식 프로젝트 API `https://api.spring.io/projects/spring-boot/generations`로 확인한 값입니다.

| 버전 | OSS 지원 종료 | 상용 지원 종료 |
|---|---|---|
| 3.5.x | 2026-06-30 | 2032-06-30 |
| 4.0.x | 2026-12-31 | 2027-12-31 |
| 4.1.x | 2027-07-31 | 2028-07-31 |

OSS 지원이 끝났으므로 3.5 라인의 새 보안 패치를 공개 배포(Maven Central)로 받는 것은 기대할 수 없습니다. 상용 지원은 위 표의 날짜까지 제공됩니다.

**의존성 취약점 (2026-10-10 기준)**

- OSV 조회에서 보고된 Tomcat, Jackson, log4j-api의 취약점은 `build.gradle`에서 `tomcat.version`(10.1.60), `jackson-bom.version`(2.21.7), `log4j2.version`(2.25.5)을 재정의해 수정 버전을 적용했습니다. Spring Boot 3.5.16이 시험한 조합은 아닙니다.
- **해결되지 않은 취약점**: `spring-webmvc` 6.2.19에 보고된 2건(GHSA-j9f9-w8pj-32f8, GHSA-pc63-qcmh-9cmg)은 6.2.x에 수정 버전이 없고 Spring Framework 7.0.9부터 수정되어 있습니다. 정적 분석에서는 이 앱이 발생 조건(SSE 뷰 조각 렌더링, `XsltView`)을 쓰지 않는 것으로 확인했지만, 해결된 것은 아닙니다.
- 3.5.x를 계속 쓰는 한 이런 취약점이 새로 보고되어도 공개 배포로 수정을 받을 수 없으므로, 공개 배포 전에 Spring Boot 4.x(Spring Framework 7.x) 업그레이드가 필요합니다. 요구사항(3.5.x) 변경과 메이저 마이그레이션 작업이 따릅니다.
- 조회 결과와 판단 근거는 `docs/PROJECT_SPEC.md` 6장을 참고하세요.

## 문서

- [docs/PROJECT_SPEC.md](docs/PROJECT_SPEC.md): 서비스 명세(요구사항, 화면, 비즈니스 규칙, 예외 처리)
- [docs/API_RESEARCH.md](docs/API_RESEARCH.md): 공공데이터 API 실제 응답으로 확인한 사실과 미검증 사항
- [AI_COLLABORATION.md](AI_COLLABORATION.md): AI Agent(Claude Code)와의 협업 기록
