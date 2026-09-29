# Project 프로젝트 개요
MartPick(마트픽) — 장바구니 상품 기준으로 선택 지역 판매점별 총액을 비교하는 웹서비스. 한국소비자원 생필품 가격 정보 공공데이터 사용.
기능·비즈니스 요구사항의 기준은 `docs/PROJECT_SPEC.md` (이 문서에 중복 작성하지 않음).

## Tech Stack 기술 스택
- Language: Java 17 (Gradle Toolchain으로 고정, Gradle 실행 JVM과 무관)
- Framework: Spring Boot 3.5.16 (Spring Web MVC, Validation, Security)
- View: Thymeleaf 서버 사이드 렌더링 + `thymeleaf-extras-springsecurity6`, HTML/CSS (프론트엔드 프레임워크 없음)
- ORM: Spring Data JPA (Hibernate), `ddl-auto: validate`
- DB: PostgreSQL 16 (로컬은 `compose.yaml`로 실행), 스키마는 Flyway (`src/main/resources/db/migration`)
- Auth: Spring Security 세션 기반 폼 로그인, BCrypt, CSRF 기본값 유지
- Test: JUnit 5 + AssertJ + Mockito (`spring-boot-starter-test`, `spring-security-test`). Testcontainers는 필요 시 승인 후 도입
- Build: Gradle 8.14.5 Wrapper (Groovy DSL, `build.gradle`)
- Lombok (생성자 주입·Entity 보일러플레이트 한정, 아래 Conventions 참조)

## Architecture 아키텍처
- 단일 Spring Boot 애플리케이션. REST API 서버와 프론트엔드를 분리하지 않음
- Controller → Service → Repository (3-layer)
- Controller: `@Controller`가 뷰 이름을 반환. 폼 검증은 `@Valid` + `BindingResult`로 처리하고, POST 성공 후에는 redirect (PRG 패턴)
- Service: 비즈니스 로직과 트랜잭션 경계(`@Transactional`), 조회 전용 메서드는 `@Transactional(readOnly = true)`
- Repository: Spring Data JPA — Query Method 우선, 필요할 때만 JPQL `@Query`
- Entity를 템플릿 모델에 직접 넘기지 않음 — 화면용 DTO로 변환
- 예외는 커스텀 예외 + `@ControllerAdvice`에서 처리하고 오류 화면/메시지로 안내
- 가격 비교 계산은 인증 로직과 분리하며, 장바구니(상품 ID·수량), 지역, 기준 조사일을 입력으로 사용합니다. 회원·인증 정보에 직접 의존하지 않습니다.
- 외부 공공데이터 API는 사용자 요청 시 호출하지 않음 — 수집해 DB에 저장한 데이터로 비교

## Conventions 코드 컨벤션
- 패키지: `com.martpick.<domain>` 도메인별 구성, 공통 설정은 `com.martpick.config`. 소규모이므로 도메인 내부 계층별 하위 패키지는 강제하지 않음
- DTO: Java `record` 우선. 폼 입력은 `*Form`, 화면 출력용은 `*View`/`*Response` 등 역할이 드러나는 접미사
- URL: 화면 경로는 도메인 명사 (`/products`, `/cart`, `/compare`), 상태 변경은 POST (`<form th:action>`으로 CSRF 토큰 자동 포함)
- Entity: 필요한 범위에서 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 사용. `@Setter`·`@Data` 금지, 상태 변경은 의미 있는 메서드로 (`cartItem.addQuantity(...)`)
- 연관관계는 기본 `FetchType.LAZY`
- 의존성 주입은 생성자 주입만 — Service·Controller는 `@RequiredArgsConstructor` + `final` 필드 (`@Autowired` 금지)
- DB 스키마 변경은 Flyway 마이그레이션(`V{n}__{설명}.sql`)으로만, 적용된 마이그레이션 파일은 수정하지 않음
- 테스트 파일: `*Test.java`, 메서드명은 한글 허용 (`장바구니_총액_계산_성공()`)
- 날짜/시간: `java.time` (조사일은 `LocalDate`)

## Development Process 개발 방식
- 큰 개발 단계 아래 세부 작업 단위로 진행
- 중요한 설계 변경(엔티티·스키마, 화면 흐름, 비즈니스 규칙, 의존성)은 구현 전에 합의
- 승인된 설계 범위 안의 세부 구현은 별도 승인 없이 진행
- 각 세부 작업에 필요한 테스트를 포함하고, 가격 비교 등 핵심 비즈니스 로직은 테스트를 먼저 작성
- 새로운 Spring 개념이나 중요한 설계 선택은 구현 이유를 간단히 설명
- 작업 완료 후 변경 파일, 실제 테스트 결과, 미해결 사항을 보고하고 다음 단계 승인을 기다림
- 공공데이터 API 필드·동작은 실제 응답으로 확인한 뒤 사용 — 추측으로 해석하지 않음
- 라이브러리/설정 문법이 불확실하면 Context7로 확인 (Spring Boot 3.5 기준 — 4.x 문서와 혼동 주의)

## Commit 커밋 메시지
- 커밋은 사용자가 요청한 경우에만 수행
- 형식: `<type>: <요약>` (Conventional Commits)
- type: feat, fix, refactor, test, docs, chore
- 요약은 한글, 50자 이내, 마침표 없음
- 본문(선택): 무엇을 왜 바꿨는지
- 예: `feat: 장바구니 총액 비교 추가`, `docs: PROJECT_SPEC 검증 결과 반영`

## DO NOT 금지
- 승인되지 않은 다음 세부 작업의 기능을 미리 구현하지 않기
- 승인 없이 의존성 추가하지 않기, 불필요한 추상화(단일 구현 인터페이스, 범용 유틸 등) 만들지 않기
- Entity를 템플릿 모델로 직접 넘기지 않기
- `ddl-auto: create`/`update` 쓰지 않기 (스키마는 Flyway)
- API 인증키와 운영 환경의 DB 접속 비밀번호는 환경변수로 관리하며 코드·문서·로그에 노출하지 않습니다. 로컬 개발 전용 DB의 공개 가능한 기본 접속 정보는 예외로 합니다. 회원 비밀번호는 평문으로 저장하거나 로그에 남기지 않고 BCrypt로 해시합니다.
- 설정 파일(`application*.yml`)은 커밋하고, `.env`·`application-local.*`·secret 파일은 `.gitignore`
- 실행하지 않은 테스트나 검증을 완료된 것처럼 보고하지 않기
- 자명한 코드에 주석 달지 않기
- 와일드카드 import 쓰지 않기

## Workflows
- 명령어: DB `docker compose up -d` (기본 테스트도 로컬 DB 필요), Build `./gradlew.bat clean build`, Test single `./gradlew.bat test --tests "*.CartServiceTest"`, Run `./gradlew.bat bootRun`
- 새 기능: `superpowers:brainstorming` → `superpowers:test-driven-development` (사용 가능한 경우)
- 버그: `superpowers:systematic-debugging`
- 완료 보고 전: `superpowers:verification-before-completion`으로 실제 실행 결과 확인
- 코드 탐색·리팩터링: Java LSP로 정의 이동, 참조 찾기, 타입 확인 — 사용 가능하면 grep보다 우선
- 라이브러리/설정 확인: Context7 MCP로 Spring Boot 3.5 공식 문서 조회
- 보안 점검: 로그인/권한 기능 추가 후 `/security-review`
- AI 협업 기록: 의미 있는 기술적 판단·실제 오류 해결 시 `AI_COLLABORATION.md` 상단의 기존 기준에 따라 갱신
