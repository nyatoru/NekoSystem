package com.nyarutoru.nekoplugin.features.graves;

import java.util.UUID;

final class GraveAccessPolicy {
    private GraveAccessPolicy() {}

    static boolean canAccess(UUID ownerId, UUID playerId, boolean usePermission, boolean adminPermission) {
        return adminPermission || ownerId.equals(playerId) && usePermission;
    }
}
