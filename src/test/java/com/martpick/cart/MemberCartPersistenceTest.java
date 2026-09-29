package com.martpick.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.martpick.catalog.Product;
import com.martpick.catalog.ProductRepository;
import com.martpick.member.Member;
import com.martpick.member.MemberRepository;

/**
 * 로컬 PostgreSQL(compose.yaml)에 Flyway 스키마를 적용한 상태로 실행한다.
 * 각 테스트는 롤백되며, 수집된 실제 데이터와 겹치지 않도록 테스트 전용 ID를 사용한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MemberCartPersistenceTest {

	private static final long TEST_GOOD_ID = 9_000_001L;
	private static final long OTHER_GOOD_ID = 9_000_002L;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private CartItemRepository cartItemRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private TestEntityManager em;

	private Product product;
	private Product otherProduct;

	@BeforeEach
	void setUp() {
		product = productRepository.save(new Product(TEST_GOOD_ID, "테스트 컵라면(110g)", "110", "G"));
		otherProduct = productRepository.save(new Product(OTHER_GOOD_ID, "테스트 우유(1L)", "1000", "ML"));
	}

	@Test
	void 회원을_저장하면_생성일이_기록된다() {
		Member saved = memberRepository.saveAndFlush(new Member("tester01", "$2a$10$hash"));
		em.clear();

		Member found = memberRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getUsername()).isEqualTo("tester01");
		assertThat(found.getPasswordHash()).isEqualTo("$2a$10$hash");
		assertThat(found.getCreatedAt()).isNotNull();
	}

	@Test
	void 로그인_아이디는_중복_저장할_수_없다() {
		memberRepository.saveAndFlush(new Member("tester01", "$2a$10$hash"));

		assertThatThrownBy(() -> memberRepository.saveAndFlush(new Member("tester01", "$2a$10$other")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 회원의_장바구니_상품을_저장하고_조회한다() {
		Member member = memberRepository.save(new Member("tester01", "$2a$10$hash"));
		CartItem saved = cartItemRepository.saveAndFlush(new CartItem(member, product, 3));
		em.clear();

		CartItem found = cartItemRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getMember().getId()).isEqualTo(member.getId());
		assertThat(found.getProduct().getGoodId()).isEqualTo(TEST_GOOD_ID);
		assertThat(found.getQuantity()).isEqualTo(3);
	}

	@Test
	void 같은_회원의_같은_상품은_중복_저장할_수_없다() {
		Member member = memberRepository.save(new Member("tester01", "$2a$10$hash"));
		cartItemRepository.saveAndFlush(new CartItem(member, product, 1));

		assertThatThrownBy(() -> cartItemRepository.saveAndFlush(new CartItem(member, product, 2)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 다른_회원은_같은_상품을_각자_저장할_수_있다() {
		Member memberA = memberRepository.save(new Member("tester01", "$2a$10$hash"));
		Member memberB = memberRepository.save(new Member("tester02", "$2a$10$hash"));

		cartItemRepository.saveAndFlush(new CartItem(memberA, product, 1));
		cartItemRepository.saveAndFlush(new CartItem(memberB, product, 1));

		assertThat(cartItemRepository.findAll())
				.filteredOn(item -> item.getProduct().getGoodId() == TEST_GOOD_ID)
				.hasSize(2);
	}

	@Test
	void 수량은_1과_99를_저장할_수_있다() {
		Member member = memberRepository.save(new Member("tester01", "$2a$10$hash"));

		cartItemRepository.saveAndFlush(new CartItem(member, product, 1));
		cartItemRepository.saveAndFlush(new CartItem(member, otherProduct, 99));

		assertThat(cartItemRepository.findAll())
				.filteredOn(item -> item.getMember().getId().equals(member.getId()))
				.extracting(CartItem::getQuantity)
				.containsExactlyInAnyOrder(1, 99);
	}

	@Test
	void 수량이_0이면_저장할_수_없다() {
		Member member = memberRepository.save(new Member("tester01", "$2a$10$hash"));

		assertThatThrownBy(() -> cartItemRepository.saveAndFlush(new CartItem(member, product, 0)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 수량이_100이면_저장할_수_없다() {
		Member member = memberRepository.save(new Member("tester01", "$2a$10$hash"));

		assertThatThrownBy(() -> cartItemRepository.saveAndFlush(new CartItem(member, product, 100)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
