package com.fundit.member.application.address;

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

    @Transactional
    public AddressItem register(UUID memberId, RegisterCommand command) {
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
