package com.fundit.order.infrastructure.persistence.funding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Builder
@Table(name = "funding_line_item_options")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FundingLineItemOptionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "funding_line_item_id", nullable = false)
    private Long fundingLineItemId;

    @Column(name = "option_group_id", nullable = false)
    private Long optionGroupId;

    @Column(name = "option_group_name", nullable = false, length = 50)
    private String optionGroupName;

    @Column(name = "option_value_id", nullable = false)
    private Long optionValueId;

    @Column(name = "option_value", nullable = false, length = 50)
    private String optionValue;
}
