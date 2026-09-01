package com.fundit.project.application.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardRepository;
import com.fundit.project.support.ConcurrentRunner;
import com.fundit.project.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * sku 중복은 "있는지 보고 없으면 넣는" 방식이라, 두 요청이 동시에 조회를 통과하면 둘 다 INSERT까지 간다.
 * 유니크 인덱스가 최후의 방어선인데, 그때 나가는 응답이 500이 아니라 409여야 한다.
 */
@DisplayName("RewardOptionService 동시성")
@Sql("/sql/insert-categories.sql")
class RewardOptionServiceConcurrencyTest extends IntegrationTestSupport {

    private static final String SKU = "SKU-RACE-001";
    private static final int THREAD_COUNT = 8;

    @Autowired
    private RewardOptionService rewardOptionService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private RewardRepository rewardRepository;

    /** 별도 스레드에는 HTTP 요청 컨텍스트가 없어 헤더 기반 어댑터를 쓸 수 없다. */
    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private UUID projectPublicId;
    private Long rewardId;

    @BeforeEach
    void setUp() {
        UUID sellerId = UUID.randomUUID();
        CurrentUser seller = new CurrentUser(sellerId, Role.MEMBER);
        when(currentUserProvider.require()).thenReturn(seller);
        when(currentUserProvider.find()).thenReturn(Optional.of(seller));

        Project project = projectRepository.save(Project.createDraft(sellerId));
        projectPublicId = project.getPublicId();
        rewardId = rewardRepository.save(Reward.create(
                project.getId(), "가습기 기본형", null, 39_000L,
                false, false, false, null, Map.of())).getId();
    }

    @Test
    void 같은_sku로_동시에_등록하면_하나만_성공한다() throws Exception {
        // given & when
        ConcurrentRunner.Result result = ConcurrentRunner.runAll(THREAD_COUNT,
                () -> rewardOptionService.create(projectPublicId, rewardId, "화이트", SKU, 10));

        // then
        assertThat(result.successCount()).isEqualTo(1);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reward_options WHERE sku = ?", Integer.class, SKU);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void 경합에서_밀린_요청도_sku_중복으로_응답한다() throws Exception {
        // given & when
        ConcurrentRunner.Result result = ConcurrentRunner.runAll(THREAD_COUNT,
                () -> rewardOptionService.create(projectPublicId, rewardId, "화이트", SKU, 10));

        // then — DataIntegrityViolationException이 그대로 새어나가면 500이 된다
        List<Throwable> failures = result.failures();
        assertThat(failures).hasSize(THREAD_COUNT - 1);
        assertThat(failures).allSatisfy(failure -> assertThat(failure)
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
                .isEqualTo(ProjectErrorCode.DUPLICATE_SKU));
    }
}
