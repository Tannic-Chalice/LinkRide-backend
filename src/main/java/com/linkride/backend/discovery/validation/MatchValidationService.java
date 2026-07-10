package com.linkride.backend.discovery.validation;

import com.linkride.backend.discovery.PassengerSearchContext;
import com.linkride.backend.discovery.corridor.CorridorMatchCandidate;

import java.util.List;

/**
 * Phase 2B.6: applies the hard constraints from design doc §5.4 to each corridor-run candidate
 * that survived shared-corridor computation (Phase 2B.5) — seat capacity, drop ordering,
 * drop-before-driver's-destination, and pickup time-window compatibility. A candidate either
 * survives unchanged or is discarded; no scoring happens here (Phase 2B.7).
 */
public interface MatchValidationService {

    List<CorridorMatchCandidate> validate(PassengerSearchContext context, List<CorridorMatchCandidate> candidates);
}
