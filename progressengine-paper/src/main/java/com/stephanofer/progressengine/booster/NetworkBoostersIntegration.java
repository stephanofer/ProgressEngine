package com.stephanofer.progressengine.booster;

import com.stephanofer.networkboosters.api.NetworkBoostersService;
import java.util.Optional;

public record NetworkBoostersIntegration(Status status, Optional<NetworkBoostersService> service,
                                         AwardBoosterCalculator awardCalculator) {
    public NetworkBoostersIntegration {
        if (status == null) throw new NullPointerException("status cannot be null");
        if (service == null) throw new NullPointerException("service cannot be null");
        if (awardCalculator == null) throw new NullPointerException("awardCalculator cannot be null");
    }

    public enum Status {
        DISABLED,
        ABSENT,
        UNAVAILABLE,
        AVAILABLE
    }
}
