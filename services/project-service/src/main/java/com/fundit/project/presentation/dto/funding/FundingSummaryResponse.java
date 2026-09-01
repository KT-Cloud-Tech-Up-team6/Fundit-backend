package com.fundit.project.presentation.dto.funding;

import com.fundit.project.application.funding.FundingSummaryService.FundingSummary;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.domain.project.Project;

import java.time.Instant;
import java.util.List;

public record FundingSummaryResponse(long currentAmount,
                                     Long goalAmount,
                                     int achievementRate,
                                     int participantCount,
                                     long wishCount,
                                     long openNotifyCount,
                                     Integer dDay,
                                     List<RewardSale> rewardSales) {

    public record RewardSale(Long rewardOptionId, String optionName, int soldQuantity) {

        static RewardSale from(FundingPort.RewardSale sale) {
            return new RewardSale(sale.rewardOptionId(), sale.optionName(), sale.soldQuantity());
        }
    }

    public static FundingSummaryResponse from(FundingSummary summary, Instant now) {
        Project project = summary.project();
        long currentAmount = summary.stats().currentAmount();
        return new FundingSummaryResponse(
                currentAmount,
                project.getGoalAmount(),
                project.achievementRate(currentAmount),
                summary.stats().participantCount(),
                summary.wishCount(),
                summary.openNotifyCount(),
                project.dDay(now),
                summary.stats().rewardSales().stream().map(RewardSale::from).toList());
    }
}
