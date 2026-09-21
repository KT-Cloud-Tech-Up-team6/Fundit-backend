package com.fundit.member.application.address;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.infrastructure.persistence.address.AddressJpaEntity;
import com.fundit.member.infrastructure.persistence.address.AddressJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressJpaRepository addressJpaRepository;

    @Transactional(readOnly = true)
    public List<AddressItem> list(UUID memberId) {
        return addressJpaRepository.findByMemberId(memberId).stream()
                .map(this::toItem)
                .toList();
    }

    /** 기본 배송지로 등록하면 기존 기본은 해제된다 — 회원당 기본 배송지는 최대 1개다. */
    @Transactional
    public AddressItem register(UUID memberId, RegisterCommand command) {
        if (Boolean.TRUE.equals(command.isDefault())) {
            addressJpaRepository.clearDefault(memberId);
        }
        AddressJpaEntity saved = addressJpaRepository.save(AddressJpaEntity.builder()
                .memberId(memberId)
                .recipientName(command.recipientName())
                .phoneNumber(command.phoneNumber())
                .zipcode(command.zipcode())
                .addressLine1(command.addressLine1())
                .addressLine2(command.addressLine2())
                .isDefault(command.isDefault())
                .build());
        return toItem(saved);
    }

    @Transactional
    public AddressItem update(UUID memberId, Long addressId, RegisterCommand command) {
        AddressJpaEntity address = loadOwned(memberId, addressId);
        address.update(command.recipientName(), command.phoneNumber(), command.zipcode(),
                command.addressLine1(), command.addressLine2());
        if (Boolean.TRUE.equals(command.isDefault())) {
            addressJpaRepository.clearDefaultExcept(memberId, addressId);
            address.markDefault();
        } else {
            address.unmarkDefault();
        }
        return toItem(address);
    }

    @Transactional
    public AddressItem changeDefault(UUID memberId, Long addressId) {
        AddressJpaEntity address = loadOwned(memberId, addressId);
        addressJpaRepository.clearDefaultExcept(memberId, addressId);
        address.markDefault();
        return toItem(address);
    }

    /** 기본 배송지를 삭제해도 다른 배송지를 기본으로 올리지 않는다 — 다음 주문 때 사용자가 고른다. */
    @Transactional
    public void delete(UUID memberId, Long addressId) {
        addressJpaRepository.delete(loadOwned(memberId, addressId));
    }

    private AddressJpaEntity loadOwned(UUID memberId, Long addressId) {
        return addressJpaRepository.findByIdAndMemberId(addressId, memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private AddressItem toItem(AddressJpaEntity entity) {
        return new AddressItem(entity.getId(), entity.getRecipientName(), entity.getPhoneNumber(), entity.getZipcode(),
                entity.getAddressLine1(), entity.getAddressLine2(), entity.getIsDefault());
    }

    public record RegisterCommand(
            String recipientName, String phoneNumber, String zipcode,
            String addressLine1, String addressLine2, Boolean isDefault
    ) {
    }

    public record AddressItem(
            Long id, String recipientName, String phoneNumber, String zipcode,
            String addressLine1, String addressLine2, boolean isDefault
    ) {
    }
}
