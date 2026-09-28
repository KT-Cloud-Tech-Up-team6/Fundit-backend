-- fundings.project_id가 UUID(public_id)로 바뀐 뒤(V5·V6) 새 주문에는 레거시 Long projectId가 없다.
-- 참여 취소(FundingCancelledByMember)·결제 조정(PAYMENT_RECONCILIATION_REQUIRED)은 넣을 값이 없어
-- null로 적재하는데 NOT NULL이라 INSERT가 실패했다. 성립·미달 판정만 값을 채우고 워커도 성립에서만 쓴다.
ALTER TABLE funding_event_outbox ALTER COLUMN project_id DROP NOT NULL;
