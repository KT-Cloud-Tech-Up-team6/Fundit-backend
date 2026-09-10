package com.fundit.auth.infrastructure.member;

import com.fundit.auth.application.signup.MemberServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 이 요청 JSON은 member-service MemberControllerTest(짝 테스트)가 받는 바디와 필드가 맞아야 한다 —
 * 한쪽만 필드명을 바꾸면 다른 쪽 테스트가 깨지도록 의도적으로 동일한 계약을 검증한다.
 */
class MemberServiceRestClientUnitTest {

    private static final String INTERNAL_API_KEY = "test-only-internal-api-key";

    @Test
    void 회원가입_요청을_전송하면_내부API키_헤더와_생성된_회원ID를_확인한다() {
        // given
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8082")
                .defaultHeader("X-Internal-Api-Key", INTERNAL_API_KEY);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MemberServiceRestClient client = new MemberServiceRestClient(builder.build());

        UUID accountId = UUID.randomUUID();
        var command = new MemberServiceClient.CreateMemberProfileCommand(
                accountId, "test@example.com", "홍길동", "01012345678",
                List.of("SERVICE_USE", "PRIVACY", "AGE_OVER_14"),
                Map.of(
                        "recipientName", "홍길동",
                        "phoneNumber", "01012345678",
                        "zipcode", "12345",
                        "addressLine1", "서울시 강남구",
                        "addressLine2", "101동 101호",
                        "isDefault", true));

        server.expect(requestTo("http://localhost:8082/api/v1/members"))
                .andExpect(method(POST))
                .andExpect(header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(content().json("""
                        {
                          "accountId": "%s",
                          "email": "test@example.com",
                          "name": "홍길동",
                          "phoneNumber": "01012345678",
                          "agreedTerms": ["SERVICE_USE", "PRIVACY", "AGE_OVER_14"],
                          "address": {
                            "recipientName": "홍길동",
                            "phoneNumber": "01012345678",
                            "zipcode": "12345",
                            "addressLine1": "서울시 강남구",
                            "addressLine2": "101동 101호",
                            "isDefault": true
                          }
                        }
                        """.formatted(accountId), true))
                .andRespond(withSuccess("""
                        {"memberId": "%s"}
                        """.formatted(accountId), MediaType.APPLICATION_JSON));

        // when
        MemberServiceClient.MemberProfile result = client.createProfile(command);

        // then
        assertThat(result.memberId()).isEqualTo(accountId);
        server.verify();
    }

    @Test
    void 본인확인_요청은_번호를_URL이_아니라_본문에_담아_보낸다() {
        // given — 쿼리스트링에 담으면 휴대폰번호가 액세스 로그에 그대로 남는다(security.md S10)
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8082")
                .defaultHeader("X-Internal-Api-Key", INTERNAL_API_KEY);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MemberServiceRestClient client = new MemberServiceRestClient(builder.build());

        UUID accountId = UUID.randomUUID();
        server.expect(requestTo("http://localhost:8082/api/v1/members/" + accountId + "/phone-verification"))
                .andExpect(method(POST))
                .andExpect(header("X-Internal-Api-Key", INTERNAL_API_KEY))
                .andExpect(content().json("{\"phoneNumber\": \"01012345678\"}"))
                .andRespond(withSuccess("{\"matches\": true}", MediaType.APPLICATION_JSON));

        // when
        boolean matches = client.phoneMatches(accountId, "01012345678");

        // then
        assertThat(matches).isTrue();
        server.verify();
    }
}
