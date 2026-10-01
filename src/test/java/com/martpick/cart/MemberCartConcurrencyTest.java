package com.martpick.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.martpick.catalog.Product;
import com.martpick.catalog.ProductRepository;
import com.martpick.member.Member;
import com.martpick.member.MemberRepository;

/**
 * 실제 PostgreSQL에서 여러 스레드가 같은 회원의 장바구니를 동시에 바꿀 때의 결과를 확인한다.
 * 스레드마다 실제로 커밋해야 하므로 {@code @Transactional} 롤백을 쓰지 않고, 테스트 데이터를 직접 지운다(테스트 DB 전용).
 *
 * <p>검증 방식과 한계:
 * <ul>
 * <li>잠금 규칙은 결정적으로 검증한다: 다른 트랜잭션이 회원 행 잠금을 쥔 동안 같은 회원의 담기·수량 변경·삭제는 끝나지 않고,
 * 다른 회원의 작업은 막히지 않으며, 잠금이 풀리면 끝난다.</li>
 * <li>경쟁 결과(수량 합계, 99 제한, 중복 행 없음)는 스레드 실행 순서에 달려 있어 <b>잠금이 없을 때 실패하는 것을
 * 확률적으로만</b> 잡는다. 잠금이 있을 때는 어떤 순서에서도 같은 결과여야 하므로 통과는 결정적이다.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class MemberCartConcurrencyTest {

	private static final long CUP_ID = 9_000_601L;
	private static final long MILK_ID = 9_000_602L;
	private static final int THREADS = 8;
	private static final int ROUNDS = 10;

	@Autowired
	private MemberCartService service;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private ExecutorService pool;
	private long memberA;
	private long memberB;

	@BeforeEach
	void setUp() {
		cleanUp();
		memberA = memberRepository.save(new Member("cartConcA", "$2a$10$hash")).getId();
		memberB = memberRepository.save(new Member("cartConcB", "$2a$10$hash")).getId();
		productRepository.save(new Product(CUP_ID, "마트픽동시성 컵라면(110g)", "110", "G"));
		productRepository.save(new Product(MILK_ID, "마트픽동시성 흰우유(1L)", "1000", "ML"));
		pool = Executors.newFixedThreadPool(THREADS);
	}

	@AfterEach
	void cleanUp() {
		if (pool != null) {
			pool.shutdownNow();
		}
		jdbc.update("delete from cart_item where member_id in (select id from member where username like 'cartConc%')");
		jdbc.update("delete from member where username like 'cartConc%'");
		jdbc.update("delete from product where good_id in (?, ?)", CUP_ID, MILK_ID);
	}

	@Test
	void 같은_상품을_동시에_담아도_중복_행_없이_모든_요청이_수량에_반영된다() throws Exception {
		List<Outcome> outcomes = runConcurrently(repeat(THREADS, task(() -> service.add(memberA, CUP_ID))));

		assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error()).isNull());
		assertThat(rowCount(memberA, CUP_ID)).isEqualTo(1);
		assertThat(quantity(memberA, CUP_ID)).isEqualTo(THREADS);
	}

	@Test
	void 수량_98에서_동시에_두_번_담으면_한_번만_성공하고_99를_넘지_않는다() throws Exception {
		service.add(memberA, CUP_ID);
		for (int round = 0; round < ROUNDS; round++) {
			service.changeQuantity(memberA, CUP_ID, 98);

			List<Outcome> outcomes = runConcurrently(repeat(2, task(() -> service.add(memberA, CUP_ID))));

			assertThat(outcomes).as("라운드 %d", round).filteredOn(outcome -> outcome.error() == null).hasSize(1);
			assertThat(outcomes).filteredOn(outcome -> outcome.error() != null)
					.singleElement().satisfies(outcome -> assertThat(outcome.error())
							.isInstanceOf(InvalidCartRequestException.class));
			assertThat(quantity(memberA, CUP_ID)).as("라운드 %d", round).isEqualTo(99);
			assertThat(rowCount(memberA, CUP_ID)).isEqualTo(1);
		}
	}

	@Test
	void 담기와_수량_변경이_동시에_와도_한쪽_결과가_사라지지_않는다() throws Exception {
		service.add(memberA, CUP_ID);
		for (int round = 0; round < ROUNDS; round++) {
			service.changeQuantity(memberA, CUP_ID, 1);

			List<Outcome> outcomes = runConcurrently(List.of(
					task(() -> service.changeQuantity(memberA, CUP_ID, 50)),
					task(() -> service.add(memberA, CUP_ID))));

			assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error()).isNull());
			// 직렬화되면 변경 후 담기(51) 또는 담기 후 변경(50)뿐이다. 수량 갱신이 누락되면 2 같은 값이 나온다.
			assertThat(quantity(memberA, CUP_ID)).as("라운드 %d", round).isIn(50, 51);
			assertThat(rowCount(memberA, CUP_ID)).isEqualTo(1);
		}
	}

	@Test
	void 담기와_삭제가_동시에_와도_오류_없이_행은_최대_하나다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			service.add(memberA, CUP_ID);

			List<Outcome> outcomes = runConcurrently(List.of(
					task(() -> service.remove(memberA, CUP_ID)),
					task(() -> service.add(memberA, CUP_ID))));

			assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error()).isNull());
			assertThat(rowCount(memberA, CUP_ID)).isLessThanOrEqualTo(1);
			service.remove(memberA, CUP_ID);
		}
	}

	@Test
	void 서로_다른_회원이_같은_상품을_동시에_담아도_각자_독립적으로_반영된다() throws Exception {
		List<Callable<Object>> tasks = new ArrayList<>();
		tasks.addAll(repeat(4, task(() -> service.add(memberA, CUP_ID))));
		tasks.addAll(repeat(3, task(() -> service.add(memberB, CUP_ID))));

		List<Outcome> outcomes = runConcurrently(tasks);

		assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error()).isNull());
		assertThat(quantity(memberA, CUP_ID)).isEqualTo(4);
		assertThat(quantity(memberB, CUP_ID)).isEqualTo(3);
	}

	@Test
	void 회원_행_잠금을_쥔_동안_같은_회원의_담기는_끝나지_않고_풀리면_끝난다() throws Exception {
		assertBlockedWhileMemberLocked(task(() -> service.add(memberA, CUP_ID)));

		assertThat(quantity(memberA, CUP_ID)).isEqualTo(1);
	}

	@Test
	void 회원_행_잠금을_쥔_동안_같은_회원의_수량_변경은_끝나지_않고_풀리면_끝난다() throws Exception {
		service.add(memberA, CUP_ID);

		assertBlockedWhileMemberLocked(task(() -> service.changeQuantity(memberA, CUP_ID, 9)));

		assertThat(quantity(memberA, CUP_ID)).isEqualTo(9);
	}

	@Test
	void 회원_행_잠금을_쥔_동안_같은_회원의_삭제는_끝나지_않고_풀리면_끝난다() throws Exception {
		service.add(memberA, CUP_ID);

		assertBlockedWhileMemberLocked(task(() -> service.remove(memberA, CUP_ID)));

		assertThat(rowCount(memberA, CUP_ID)).isZero();
	}

	@Test
	void 한_회원이_잠금을_쥐어도_다른_회원의_작업과_모든_회원의_조회는_막히지_않는다() throws Exception {
		service.add(memberA, CUP_ID);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Future<?> holder = holdMemberLock(memberA, locked, release);
		try {
			assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

			pool.submit(() -> service.add(memberB, CUP_ID)).get(5, TimeUnit.SECONDS);
			pool.submit(() -> service.changeQuantity(memberB, CUP_ID, 4)).get(5, TimeUnit.SECONDS);
			assertThat(pool.submit(() -> service.items(memberA)).get(5, TimeUnit.SECONDS)).hasSize(1);
			assertThat(pool.submit(() -> service.lines(memberA)).get(5, TimeUnit.SECONDS)).hasSize(1);
		} finally {
			release.countDown();
			holder.get(10, TimeUnit.SECONDS);
		}
		assertThat(quantity(memberB, CUP_ID)).isEqualTo(4);
	}

	@Test
	void 트랜잭션_밖에서_조회해도_지연_로딩_오류_없이_화면용_정보를_돌려준다() {
		service.add(memberA, CUP_ID);
		service.add(memberA, MILK_ID);

		assertThat(service.items(memberA)).extracting(CartItemView::name)
				.containsExactly("마트픽동시성 컵라면(110g)", "마트픽동시성 흰우유(1L)");
	}

	private void assertBlockedWhileMemberLocked(Callable<Object> operation) throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Future<?> holder = holdMemberLock(memberA, locked, release);
		Future<Object> blocked = null;
		try {
			assertThat(locked.await(5, TimeUnit.SECONDS)).as("잠금 획득").isTrue();
			blocked = pool.submit(operation);

			Future<Object> pending = blocked;
			assertThatThrownBy(() -> pending.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
			assertThat(blocked.isDone()).isFalse();
		} finally {
			release.countDown();
			holder.get(10, TimeUnit.SECONDS);
		}
		blocked.get(10, TimeUnit.SECONDS);
	}

	private Future<?> holdMemberLock(long memberId, CountDownLatch locked, CountDownLatch release) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		return pool.submit(() -> template.executeWithoutResult(status -> {
			memberRepository.findWithLockById(memberId).orElseThrow();
			locked.countDown();
			try {
				release.await(15, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}));
	}

	private List<Outcome> runConcurrently(List<? extends Callable<?>> tasks) throws Exception {
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Outcome>> futures = new ArrayList<>();
		for (Callable<?> task : tasks) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				start.await();
				try {
					task.call();
					return new Outcome(null);
				} catch (Exception e) {
					return new Outcome(e);
				}
			}));
		}
		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		List<Outcome> outcomes = new ArrayList<>();
		for (Future<Outcome> future : futures) {
			try {
				outcomes.add(future.get(30, TimeUnit.SECONDS));
			} catch (ExecutionException e) {
				throw new AssertionError("작업 실행 실패", e);
			}
		}
		return outcomes;
	}

	private static Callable<Object> task(Runnable action) {
		return () -> {
			action.run();
			return null;
		};
	}

	private static List<Callable<Object>> repeat(int count, Callable<Object> task) {
		List<Callable<Object>> tasks = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			tasks.add(task);
		}
		return tasks;
	}

	private int quantity(long memberId, long goodId) {
		return jdbc.queryForObject("select quantity from cart_item where member_id = ? and good_id = ?", Integer.class,
				memberId, goodId);
	}

	private int rowCount(long memberId, long goodId) {
		return jdbc.queryForObject("select count(*) from cart_item where member_id = ? and good_id = ?", Integer.class,
				memberId, goodId);
	}

	private record Outcome(Exception error) {
	}
}
