package com.fundit.order.infrastructure.persistence.funding;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingLineItemOption;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FundingMapper {

    Funding toDomain(FundingJpaEntity entity, List<FundingLineItemJpaEntity> lineItemEntities,
                      Map<Long, List<FundingLineItemOptionJpaEntity>> optionsByLineItemId) {
        List<FundingLineItem> lineItems = lineItemEntities.stream()
                .map(li -> toLineItemDomain(li, optionsByLineItemId.getOrDefault(li.getId(), List.of())))
                .toList();
        return Funding.builder()
                .id(entity.getId())
                .publicId(entity.getPublicId())
                .memberId(entity.getMemberId())
                .projectId(entity.getProjectId())
                .projectTitle(entity.getProjectTitle())
                .liveSessionId(entity.getLiveSessionId())
                .status(FundingStatus.valueOf(entity.getStatus()))
                .shippingAddress(toShippingAddressDomain(entity.getShippingAddress()))
                .shippingFee(entity.getShippingFee())
                .paymentExpiresAt(entity.getPaymentExpiresAt())
                .decidedAt(entity.getDecidedAt())
                .lineItems(lineItems)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    FundingJpaEntity toEntity(Funding domain) {
        return FundingJpaEntity.builder()
                .id(domain.getId())
                .publicId(domain.getPublicId())
                .memberId(domain.getMemberId())
                .projectId(domain.getProjectId())
                .projectTitle(domain.getProjectTitle())
                .liveSessionId(domain.getLiveSessionId())
                .status(domain.getStatus().name())
                .shippingAddress(toShippingAddressJson(domain.getShippingAddress()))
                .shippingFee(domain.getShippingFee())
                .paymentExpiresAt(domain.getPaymentExpiresAt())
                .decidedAt(domain.getDecidedAt())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    FundingLineItemJpaEntity toLineItemEntity(Long fundingId, FundingLineItem domain) {
        return FundingLineItemJpaEntity.builder()
                .fundingId(fundingId)
                .rewardId(domain.rewardId())
                .rewardName(domain.rewardName())
                .quantity(domain.quantity())
                .unitPrice(domain.unitPrice())
                .build();
    }

    FundingLineItemOptionJpaEntity toOptionEntity(Long lineItemId, FundingLineItemOption domain) {
        return FundingLineItemOptionJpaEntity.builder()
                .fundingLineItemId(lineItemId)
                .optionGroupId(domain.optionGroupId())
                .optionGroupName(domain.optionGroupName())
                .optionValueId(domain.optionValueId())
                .optionValue(domain.optionValue())
                .build();
    }

    private FundingLineItem toLineItemDomain(FundingLineItemJpaEntity entity,
                                              List<FundingLineItemOptionJpaEntity> optionEntities) {
        List<FundingLineItemOption> options = optionEntities.stream().map(this::toOptionDomain).toList();
        return new FundingLineItem(entity.getId(), entity.getRewardId(), entity.getRewardName(),
                entity.getQuantity(), entity.getUnitPrice(), options);
    }

    private FundingLineItemOption toOptionDomain(FundingLineItemOptionJpaEntity entity) {
        return new FundingLineItemOption(entity.getId(), entity.getOptionGroupId(), entity.getOptionGroupName(),
                entity.getOptionValueId(), entity.getOptionValue());
    }

    private ShippingAddressJson toShippingAddressJson(ShippingAddress domain) {
        return new ShippingAddressJson(domain.recipientName(), domain.phoneNumber(), domain.zipcode(),
                domain.addressLine1(), domain.addressLine2());
    }

    private ShippingAddress toShippingAddressDomain(ShippingAddressJson json) {
        return new ShippingAddress(json.recipientName(), json.phoneNumber(), json.zipcode(),
                json.addressLine1(), json.addressLine2());
    }
}
