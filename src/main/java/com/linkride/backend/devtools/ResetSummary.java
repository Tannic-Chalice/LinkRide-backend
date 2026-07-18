package com.linkride.backend.devtools;

import lombok.Builder;
import lombok.Data;

/** Result of {@link DemoSeedService#reset()} — counts of everything that was deleted. */
@Data
@Builder
public class ResetSummary {
    private int boardingVerificationsDeleted;
    private int boardingTokensDeleted;
    private int bookingsDeleted;
    private int ridesDeleted;
    private int vehiclesDeleted;
    private int usersDeleted;
}
