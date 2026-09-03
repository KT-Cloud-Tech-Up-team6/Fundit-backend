package com.fundit.member.application.address;

import com.fundit.member.infrastructure.persistence.address.AddressJpaEntity;
import com.fundit.member.infrastructure.persistence.address.AddressJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceUnitTest {

    @Mock
    private AddressJpaRepository addressJpaRepository;

    @InjectMocks
    private AddressService addressService;

    @Test
    void 배송지_등록시_회원소유로_저장한다() {
        // given
        UUID memberId = UUID.randomUUID();
        ArgumentCaptor<AddressJpaEntity> captor = ArgumentCaptor.forClass(AddressJpaEntity.class);
        when(addressJpaRepository.save(captor.capture())).thenAnswer(invocation -> {
            AddressJpaEntity entity = invocation.getArgument(0);
            return AddressJpaEntity.builder()
                    .id(1L).memberId(entity.getMemberId()).recipientName(entity.getRecipientName())
                    .phoneNumber(entity.getPhoneNumber()).zipcode(entity.getZipcode())
                    .addressLine1(entity.getAddressLine1()).addressLine2(entity.getAddressLine2())
                    .isDefault(entity.getIsDefault()).build();
        });

        // when
        AddressService.AddressItem result = addressService.register(memberId, new AddressService.RegisterCommand(
                "홍길동", "01012345678", "12345", "테헤란로 1", null, true));

        // then
        assertThat(captor.getValue().getMemberId()).isEqualTo(memberId);
        assertThat(result.isDefault()).isTrue();
    }

    @Test
    void 배송지_목록조회시_해당_회원의_배송지만_반환한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(addressJpaRepository.findByMemberId(memberId)).thenReturn(List.of(
                AddressJpaEntity.builder().id(1L).memberId(memberId).recipientName("홍길동")
                        .phoneNumber("01012345678").zipcode("12345").addressLine1("테헤란로 1")
                        .isDefault(true).build()));

        // when
        List<AddressService.AddressItem> result = addressService.list(memberId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).recipientName()).isEqualTo("홍길동");
    }
}
