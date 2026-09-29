package com.fundit.project.infrastructure.sellerprofile;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class MemberServiceSellerProfileClientUnitExceptionTest {

    @Test
    void member_오류면_예외_없이_빈_값을_준다() {
        // given — 판매자명은 부가 정보라 member 장애가 상세·목록·색인 이벤트를 막으면 안 된다
        RestClient.Builder builder = MemberServiceClientConfig.builder("http://member", "test-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new MemberServiceSellerProfileClient(builder.build());
        server.expect(requestTo(startsWith("http://member/internal/v1/members/nicknames")))
                .andRespond(withServerError());

        // when
        Map<UUID, String> result = client.getDisplayNames(List.of(UUID.randomUUID()));

        // then
        assertThat(result).isEmpty();
    }
}
