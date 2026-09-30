-- 성공한 전체 수집 이력. 가격 교체와 같은 트랜잭션에서 기록하며 실패한 수집은 남지 않는다
CREATE TABLE collection_run (
    id                   BIGSERIAL   PRIMARY KEY,
    inspect_day          DATE        NOT NULL,
    product_count        INTEGER     NOT NULL CHECK (product_count >= 0),
    priced_product_count INTEGER     NOT NULL CHECK (priced_product_count >= 0),
    price_count          INTEGER     NOT NULL CHECK (price_count >= 0),
    completed_at         TIMESTAMPTZ NOT NULL,
    -- 가격 보유 상품은 상품 응답에 속하고, 가격 보유 상품마다 가격이 1건 이상 있다
    CONSTRAINT ck_collection_run_priced_products CHECK (priced_product_count <= product_count),
    CONSTRAINT ck_collection_run_price_count CHECK (price_count >= priced_product_count)
);

CREATE INDEX idx_collection_run_day_completed ON collection_run (inspect_day, completed_at);
