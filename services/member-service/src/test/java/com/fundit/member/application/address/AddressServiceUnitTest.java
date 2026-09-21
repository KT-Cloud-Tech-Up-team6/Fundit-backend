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

    private AddressJpaEntity address(long id, UUID memberId, boolean isDefault) {
        return AddressJpaEntity.builder().id(id).memberId(memberId).recipientName("홍길동")
                .phoneNumber("01012345678").zipcode("12345").addressLine1("테헤란로 1").isDefault(isDefault).build();
    }

    @Test
    void 기본_배송지로_등록하면_기존_기본을_먼저_해제한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(addressJpaRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        addressService.register(memberId, new AddressService.RegisterCommand(
                "홍길동", "01012345678", "12345", "테헤란로 1", null, true));

        // then — 부분 유니크 인덱스에 걸리지 않으려면 INSERT 전에 해제해야 한다
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(addressJpaRepository);
        order.verify(addressJpaRepository).clearDefault(memberId);
        order.verify(addressJpaRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 수정하면서_기본으로_지정하면_다른_기본을_해제하고_이_배송지를_기본으로_한다() {
        // given
        UUID memberId = UUID.randomUUID();
        AddressJpaEntity target = address(2L, memberId, false);
        when(addressJpaRepository.findByIdAndMemberId(2L, memberId)).thenReturn(java.util.Optional.of(target));

        // when
        AddressService.AddressItem result = addressService.update(memberId, 2L, new AddressService.RegisterCommand(
                "김철수", "01099998888", "54321", "강남대로 2", "101호", true));

        // then
        org.mockito.Mockito.verify(addressJpaRepository).clearDefaultExcept(memberId, 2L);
        assertThat(result.recipientName()).isEqualTo("김철수");
        assertThat(result.isDefault()).isTrue();
    }

    @Test
    void 기본_배송지를_바꾸면_이_배송지를_제외한_기본을_해제한다() {
        // given
        UUID memberId = UUID.randomUUID();
        AddressJpaEntity target = address(3L, memberId, false);
        when(addressJpaRepository.findByIdAndMemberId(3L, memberId)).thenReturn(java.util.Optional.of(target));

        // when
        AddressService.AddressItem result = addressService.changeDefault(memberId, 3L);

        // then
        org.mockito.Mockito.verify(addressJpaRepository).clearDefaultExcept(memberId, 3L);
        assertThat(result.isDefault()).isTrue();
    }

    @Test
    void 기본_배송지를_삭제해도_다른_배송지를_기본으로_올리지_않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        AddressJpaEntity target = address(4L, memberId, true);
        when(addressJpaRepository.findByIdAndMemberId(4L, memberId)).thenReturn(java.util.Optional.of(target));

        // when
        addressService.delete(memberId, 4L);

        // then
        org.mockito.Mockito.verify(addressJpaRepository).delete(target);
        org.mockito.Mockito.verify(addressJpaRepository, org.mockito.Mockito.never())
                .clearDefaultExcept(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(addressJpaRepository, org.mockito.Mockito.never()).findByMemberId(memberId);
    }
}
