package com.fundit.live.infrastructure.member;

import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MemberServiceMemberNicknameClientUnitTest {

    @Test
    void 내부_키를_붙여_일괄_조회하고_닉네임_맵으로_돌려준다() {
        // given
        RestClient.Builder builder = MemberServiceClientConfig.builder("http://member", "test-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new MemberServiceMemberNicknameClient(builder.build());
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        server.expect(requestTo("http://member/internal/v1/members/nicknames?ids=" + a + "&ids=" + b))
                .andExpect(header("X-Internal-Api-Key", "test-key"))
                .andRespond(withSuccess("""
                        [{"memberId":"%s","nickname":"쓱쓱생활연구소"},{"memberId":"%s","nickname":null}]
                        """.formatted(a, b), MediaType.APPLICATION_JSON));

        // when
        Map<UUID, String> result = client.findNicknames(List.of(a, b, a));

        // then — 중복 id는 한 번만 보내고, 닉네임이 없는 항목은 버린다
        assertThat(result).containsExactly(Map.entry(a, "쓱쓱생활연구소"));
        server.verify();
    }

    @Test
    void 상한을_넘으면_나눠서_부른다() {
        // given — member 쪽이 101개부터 400을 준다
        RestClient.Builder builder = MemberServiceClientConfig.builder("http://member", "test-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        var client = new MemberServiceMemberNicknameClient(builder.build());
        List<UUID> ids = Stream.generate(UUID::randomUUID)
                .limit(MemberServiceMemberNicknameClient.CHUNK_SIZE + 1).toList();

        server.expect(ExpectedCount.times(2), requestTo(startsWith("http://member/internal/v1/members/nicknames")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // when
        client.findNicknames(ids);

        // then
        server.verify();
    }

    @Test
    void member_오류는_의존성_실패로_올린다() {
        // given
        RestClient.Builder builder = MemberServiceClientConfig.builder("http://member", "test-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new MemberServiceMemberNicknameClient(builder.build());
        server.expect(requestTo(startsWith("http://member/internal/v1/members/nicknames")))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> client.findNicknames(List.of(UUID.randomUUID())))
                .isInstanceOf(DependencyFailureException.class);
    }
}
