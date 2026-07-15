package com.stephanofer.progressengine.command;

import java.util.Optional;
import java.util.UUID;

record ResolvedTarget(UUID playerId, Optional<String> username) {
}
