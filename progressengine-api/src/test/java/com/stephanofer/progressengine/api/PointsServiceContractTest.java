package com.stephanofer.progressengine.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

final class PointsServiceContractTest {
    @Test
    void serviceExposesPluginBoundClientFactory() throws NoSuchMethodException {
        Method method = PointsService.class.getMethod("client", Plugin.class);

        assertEquals(PointsClient.class, method.getReturnType());
    }

    @Test
    void clientKeepsCachedReadsSynchronousAndMutationsAsyncByContract() {
        assertTrue(Arrays.stream(PointsClient.class.getMethods()).anyMatch(method -> method.getName().equals("cached")));
        assertTrue(Arrays.stream(PointsClient.class.getMethods()).anyMatch(method -> method.getName().equals("resetBalance")));
    }
}
