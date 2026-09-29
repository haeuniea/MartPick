-- 시·도 지역 코드 (공통 코드 API classCode=AR 중 시·도만)
CREATE TABLE region (
    code VARCHAR(9)  PRIMARY KEY,
    name VARCHAR(50) NOT NULL
);

-- 수집 대상 상품 (초기 7개). 규격·코드는 API 원문 그대로 저장
CREATE TABLE product (
    good_id        BIGINT       PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    total_cnt      VARCHAR(20)  NOT NULL,
    total_div_code VARCHAR(10)  NOT NULL
);

-- 판매점. 판매점 ID는 10자리 값이 있어 BIGINT 사용
CREATE TABLE store (
    entp_id          BIGINT       PRIMARY KEY,
    name             VARCHAR(200) NOT NULL,
    type_code        VARCHAR(10)  NOT NULL,
    area_code        VARCHAR(9)   NOT NULL REFERENCES region (code),
    area_detail_code VARCHAR(9)   NOT NULL,
    road_addr_basic  VARCHAR(300),
    road_addr_detail VARCHAR(300),
    plmk_addr_basic  VARCHAR(300) NOT NULL,
    plmk_addr_detail VARCHAR(300),
    excluded         BOOLEAN      NOT NULL DEFAULT FALSE,
    excluded_reason  VARCHAR(100)
);

CREATE INDEX idx_store_area_code ON store (area_code);

-- 조사일별 가격. 같은 판매점·상품·조사일 조합은 한 행만 허용
CREATE TABLE price (
    id          BIGSERIAL PRIMARY KEY,
    entp_id     BIGINT    NOT NULL REFERENCES store (entp_id),
    good_id     BIGINT    NOT NULL REFERENCES product (good_id),
    inspect_day DATE      NOT NULL,
    price       INTEGER   NOT NULL CHECK (price > 0),
    CONSTRAINT uk_price_store_product_day UNIQUE (entp_id, good_id, inspect_day)
);

CREATE INDEX idx_price_day_good ON price (inspect_day, good_id);

CREATE TABLE member (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_member_username UNIQUE (username)
);

CREATE TABLE cart_item (
    id        BIGSERIAL PRIMARY KEY,
    member_id BIGINT    NOT NULL REFERENCES member (id),
    good_id   BIGINT    NOT NULL REFERENCES product (good_id),
    quantity  INTEGER   NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT uk_cart_item_member_product UNIQUE (member_id, good_id)
);
