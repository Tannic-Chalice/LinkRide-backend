package com.linkride.backend.dto.home;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Wallet balance summary shown on the Home Screen badge.
 *
 * <p>Currently stubbed to {@code 0.00}. Wire to the Wallet module's aggregate
 * query (SUM CREDIT – SUM DEBIT WHERE status = 'SUCCESS') once that module
 * is built.</p>
 */
@Data
@Builder
public class WalletSummaryDto {
    private BigDecimal balance;
}
