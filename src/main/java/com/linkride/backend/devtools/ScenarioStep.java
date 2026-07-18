package com.linkride.backend.devtools;

import lombok.Builder;
import lombok.Data;

/** One step of {@link DemoScenarioService}'s narrated end-to-end walkthrough. */
@Data
@Builder
public class ScenarioStep {
    private int stepNumber;
    private String title;
    private String detail;
}
