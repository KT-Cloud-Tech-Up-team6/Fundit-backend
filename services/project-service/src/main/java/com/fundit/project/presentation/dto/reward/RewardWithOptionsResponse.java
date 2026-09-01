package com.fundit.project.presentation.dto.reward;

import com.fundit.project.application.reward.RewardQueryService.OptionStock;
import com.fundit.project.application.reward.RewardQueryService.RewardWithOptions;
import com.fundit.project.domain.reward.Reward;

import java.util.List;

public record RewardWithOptionsResponse(Long rewardId,
                                        String name,
                                        Long price,
                                        boolean isEarlyBird,
                                        boolean isUnlimited,
                                        List<Option> options) {

    /** availableStock이 null이면 무제한이거나 재고를 조회하지 못한 경우다. */
    public record Option(Long rewardOptionId, String optionName, Integer availableStock, boolean soldOut) {

        static Option from(OptionStock stock) {
            return new Option(stock.option().getId(), stock.option().getOptionName(),
                    stock.availableStock(), stock.soldOut());
        }
    }

    public static RewardWithOptionsResponse from(RewardWithOptions source) {
        Reward reward = source.reward();
        return new RewardWithOptionsResponse(
                reward.getId(),
                reward.getName(),
                reward.getPrice(),
                reward.isEarlyBird(),
                reward.isUnlimited(),
                source.options().stream().map(Option::from).toList());
    }
}
