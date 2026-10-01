package com.martpick.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.martpick.catalog.Product;
import com.martpick.catalog.ProductRepository;
import com.martpick.compare.CartLine;
import com.martpick.member.Member;
import com.martpick.member.MemberRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * 실제 PostgreSQL에서 회원 장바구니 서비스의 저장·조회·격리를 확인한다. 테스트 데이터는 트랜잭션 롤백으로 남기지 않는다.
 * 동시성은 실제 커밋이 필요해 {@link MemberCartConcurrencyTest}에서 따로 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberCartServiceTest {

	private static final long CUP_ID = 9_000_401L;
	private static final long MILK_ID = 9_000_402L;
	private static final long RICE_ID = 9_000_403L;
	private static final long NOT_COLLECTED_ID = 9_000_499L;
	private static final long NO_SUCH_MEMBER_ID = 9_999_999_999L;

	@Autowired
	private MemberCartService service;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private CartItemRepository cartItemRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager em;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private long memberA;
	private long memberB;

	@BeforeEach
	void setUp() {
		memberA = memberRepository.save(new Member("cartTestA", "$2a$10$hash")).getId();
		memberB = memberRepository.save(new Member("cartTestB", "$2a$10$hash")).getId();
		productRepository.save(new Product(CUP_ID, "마트픽테스트 컵라면(110g)", "110", "G"));
		productRepository.save(new Product(MILK_ID, "마트픽테스트 흰우유(1L)", "1000", "ML"));
		productRepository.save(new Product(RICE_ID, "마트픽테스트 햇반(210g)", "", ""));
		productRepository.flush();
	}

	@Test
	void 새_상품을_담으면_수량_1로_저장하고_상품명을_반환한다() {
		String name = service.add(memberA, CUP_ID);

		assertThat(name).isEqualTo("마트픽테스트 컵라면(110g)");
		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(1);
	}

	@Test
	void 같은_상품을_다시_담으면_수량이_1_늘고_행은_하나다() {
		service.add(memberA, CUP_ID);
		service.add(memberA, CUP_ID);
		service.add(memberA, CUP_ID);

		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(3);
		assertThat(rowCount(memberA, CUP_ID)).isEqualTo(1);
	}

	@Test
	void 수량이_99인_상품을_더_담으면_거부하고_수량은_그대로다() {
		service.add(memberA, CUP_ID);
		service.changeQuantity(memberA, CUP_ID, 99);

		assertThatThrownBy(() -> service.add(memberA, CUP_ID))
				.isInstanceOf(InvalidCartRequestException.class)
				.hasMessage("상품당 최대 99개까지 담을 수 있습니다.");
		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(99);
	}

	@Test
	void 수량_변경은_1과_99를_허용한다() {
		service.add(memberA, CUP_ID);

		service.changeQuantity(memberA, CUP_ID, 99);
		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(99);
		service.changeQuantity(memberA, CUP_ID, 1);
		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(1);
	}

	@Test
	void 수량_0_100_음수는_거부하고_기존_수량을_유지한다() {
		service.add(memberA, CUP_ID);
		service.changeQuantity(memberA, CUP_ID, 7);

		for (int invalid : new int[] {0, 100, -1, Integer.MAX_VALUE}) {
			assertThatThrownBy(() -> service.changeQuantity(memberA, CUP_ID, invalid))
					.isInstanceOf(InvalidCartRequestException.class)
					.hasMessage(GuestCart.QUANTITY_RANGE_MESSAGE);
		}
		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(7);
	}

	@Test
	void 담기지_않은_상품의_수량_변경과_삭제는_아무것도_만들거나_지우지_않는다() {
		service.add(memberA, CUP_ID);

		service.changeQuantity(memberA, MILK_ID, 5);
		service.remove(memberA, MILK_ID);

		assertThat(rowCount(memberA, MILK_ID)).isZero();
		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(1);
	}

	@Test
	void 삭제하면_조회에서_사라지고_다른_상품은_남는다() {
		service.add(memberA, CUP_ID);
		service.add(memberA, MILK_ID);

		service.remove(memberA, CUP_ID);

		assertThat(service.items(memberA)).extracting(CartItemView::goodId).containsExactly(MILK_ID);
		assertThat(rowCount(memberA, CUP_ID)).isZero();
	}

	@Test
	void 삭제한_상품을_다시_담으면_수량_1로_새로_시작한다() {
		service.add(memberA, CUP_ID);
		service.changeQuantity(memberA, CUP_ID, 9);
		service.remove(memberA, CUP_ID);

		service.add(memberA, CUP_ID);

		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(1);
	}

	@Test
	void 수집되지_않은_상품은_담을_수_없다() {
		for (long goodId : new long[] {NOT_COLLECTED_ID, 0L, -1L}) {
			assertThatThrownBy(() -> service.add(memberA, goodId))
					.isInstanceOf(InvalidCartRequestException.class)
					.hasMessage("담을 수 없는 상품입니다.");
		}
		assertThat(service.items(memberA)).isEmpty();
	}

	@Test
	void 존재하지_않는_회원은_담거나_바꾸거나_지울_수_없다() {
		assertThatThrownBy(() -> service.add(NO_SUCH_MEMBER_ID, CUP_ID)).isInstanceOf(InvalidCartRequestException.class);
		assertThatThrownBy(() -> service.changeQuantity(NO_SUCH_MEMBER_ID, CUP_ID, 2))
				.isInstanceOf(InvalidCartRequestException.class);
		assertThatThrownBy(() -> service.remove(NO_SUCH_MEMBER_ID, CUP_ID)).isInstanceOf(InvalidCartRequestException.class);
	}

	@Test
	void 존재하지_않는_회원의_조회는_빈_목록이다() {
		assertThat(service.items(NO_SUCH_MEMBER_ID)).isEmpty();
		assertThat(service.lines(NO_SUCH_MEMBER_ID)).isEmpty();
	}

	@Test
	void 담은_순서대로_조회하고_수량_변경과_재담기는_순서를_바꾸지_않는다() {
		service.add(memberA, MILK_ID);
		service.add(memberA, CUP_ID);
		service.add(memberA, RICE_ID);

		service.add(memberA, MILK_ID);
		service.changeQuantity(memberA, CUP_ID, 4);

		assertThat(service.items(memberA)).extracting(CartItemView::goodId).containsExactly(MILK_ID, CUP_ID, RICE_ID);
	}

	@Test
	void 삭제했다가_다시_담은_상품은_맨_뒤로_간다() {
		service.add(memberA, CUP_ID);
		service.add(memberA, MILK_ID);

		service.remove(memberA, CUP_ID);
		service.add(memberA, CUP_ID);

		assertThat(service.items(memberA)).extracting(CartItemView::goodId).containsExactly(MILK_ID, CUP_ID);
	}

	@Test
	void 화면용_정보는_상품명_규격_수량이고_규격_값이_없으면_null이다() {
		service.add(memberA, CUP_ID);
		service.add(memberA, RICE_ID);
		service.changeQuantity(memberA, CUP_ID, 3);

		assertThat(service.items(memberA)).containsExactly(
				new CartItemView(CUP_ID, "마트픽테스트 컵라면(110g)", "110 G", 3),
				new CartItemView(RICE_ID, "마트픽테스트 햇반(210g)", null, 1));
	}

	@Test
	void 가격_비교_입력은_담은_순서의_상품_ID와_수량이다() {
		service.add(memberA, MILK_ID);
		service.add(memberA, CUP_ID);
		service.changeQuantity(memberA, CUP_ID, 5);

		assertThat(service.lines(memberA)).containsExactly(new CartLine(MILK_ID, 1), new CartLine(CUP_ID, 5));
	}

	@Test
	void 빈_장바구니는_빈_목록이다() {
		assertThat(service.items(memberA)).isEmpty();
		assertThat(service.lines(memberA)).isEmpty();
	}

	@Test
	void 저장한_내용은_영속성_컨텍스트를_비워도_DB에서_다시_읽힌다() {
		service.add(memberA, CUP_ID);
		service.changeQuantity(memberA, CUP_ID, 6);
		em.flush();
		em.clear();

		assertThat(service.items(memberA)).extracting(CartItemView::goodId, CartItemView::quantity)
				.containsExactly(tuple(CUP_ID, 6));
		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(6);
	}

	@Test
	void 회원마다_장바구니가_분리되어_서로_조회되지_않는다() {
		service.add(memberA, CUP_ID);
		service.add(memberB, MILK_ID);
		service.add(memberB, MILK_ID);

		assertThat(service.items(memberA)).extracting(CartItemView::goodId, CartItemView::quantity)
				.containsExactly(tuple(CUP_ID, 1));
		assertThat(service.items(memberB)).extracting(CartItemView::goodId, CartItemView::quantity)
				.containsExactly(tuple(MILK_ID, 2));
		assertThat(service.lines(memberA)).containsExactly(new CartLine(CUP_ID, 1));
		assertThat(service.lines(memberB)).containsExactly(new CartLine(MILK_ID, 2));
	}

	@Test
	void 같은_상품을_담아도_회원_A의_변경과_삭제는_회원_B의_행에_영향을_주지_않는다() {
		service.add(memberA, CUP_ID);
		service.add(memberB, CUP_ID);
		service.changeQuantity(memberB, CUP_ID, 8);

		service.changeQuantity(memberA, CUP_ID, 3);
		assertThat(quantityInDb(memberA, CUP_ID)).isEqualTo(3);
		assertThat(quantityInDb(memberB, CUP_ID)).isEqualTo(8);

		service.remove(memberA, CUP_ID);
		assertThat(rowCount(memberA, CUP_ID)).isZero();
		assertThat(quantityInDb(memberB, CUP_ID)).isEqualTo(8);
	}

	@Test
	void 회원_A가_B만_가진_상품_ID로_변경하거나_삭제해도_B의_데이터는_그대로다() {
		service.add(memberB, MILK_ID);
		service.changeQuantity(memberB, MILK_ID, 8);

		service.changeQuantity(memberA, MILK_ID, 1);
		service.remove(memberA, MILK_ID);
		assertThatThrownBy(() -> service.add(memberA, NOT_COLLECTED_ID)).isInstanceOf(InvalidCartRequestException.class);

		assertThat(quantityInDb(memberB, MILK_ID)).isEqualTo(8);
		assertThat(rowCount(memberB, MILK_ID)).isEqualTo(1);
		assertThat(rowCount(memberA, MILK_ID)).isZero();
		assertThat(service.items(memberA)).isEmpty();
	}

	@Test
	void 한_회원의_장바구니_변경이_다른_회원의_행_수를_바꾸지_않는다() {
		service.add(memberB, CUP_ID);
		service.add(memberB, MILK_ID);
		long before = totalRows(memberB);

		service.add(memberA, CUP_ID);
		service.add(memberA, MILK_ID);
		service.remove(memberA, CUP_ID);
		service.changeQuantity(memberA, MILK_ID, 50);

		assertThat(totalRows(memberB)).isEqualTo(before);
	}

	@Test
	void 항목_수가_늘어도_조회_SQL_실행_수가_늘지_않는다() {
		service.add(memberA, CUP_ID);
		em.flush();
		long small = statementCount(() -> service.items(memberA));
		long smallLines = statementCount(() -> service.lines(memberA));

		service.add(memberA, MILK_ID);
		service.add(memberA, RICE_ID);
		for (int i = 0; i < 5; i++) {
			long goodId = 9_000_410L + i;
			productRepository.save(new Product(goodId, "마트픽테스트 상품" + i, "1", "EA"));
			service.add(memberA, goodId);
		}
		em.flush();
		long large = statementCount(() -> service.items(memberA));
		long largeLines = statementCount(() -> service.lines(memberA));

		assertThat(small).as("테스트 프로필에서 Hibernate 통계가 켜져 있어야 한다").isPositive();
		assertThat(large).isEqualTo(small).isLessThanOrEqualTo(1);
		assertThat(largeLines).isEqualTo(smallLines).isLessThanOrEqualTo(1);
		assertThat(service.items(memberA)).hasSize(8);
	}

	private long statementCount(Runnable call) {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		em.clear();
		statistics.clear();
		call.run();
		long count = statistics.getPrepareStatementCount();
		System.out.println("[statement-count] " + count);
		return count;
	}

	private Integer quantityInDb(long memberId, long goodId) {
		em.flush();
		List<Integer> found = jdbc.queryForList(
				"select quantity from cart_item where member_id = ? and good_id = ?", Integer.class, memberId, goodId);
		assertThat(found).as("행 개수").hasSize(1);
		return found.get(0);
	}

	private int rowCount(long memberId, long goodId) {
		em.flush();
		return jdbc.queryForObject("select count(*) from cart_item where member_id = ? and good_id = ?", Integer.class,
				memberId, goodId);
	}

	private int totalRows(long memberId) {
		em.flush();
		return jdbc.queryForObject("select count(*) from cart_item where member_id = ?", Integer.class, memberId);
	}
}
