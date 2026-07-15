package com.stephanofer.progressengine.award;

import com.stephanofer.progressengine.api.event.PointsAwardPrepareEvent;
import com.stephanofer.progressengine.api.request.AwardRequest;
import com.stephanofer.progressengine.api.source.OperationSource;
import java.util.concurrent.CompletableFuture;

public interface AwardPrepareEventDispatcher {
    CompletableFuture<PointsAwardPrepareEvent> dispatch(AwardRequest request, OperationSource source);
}
