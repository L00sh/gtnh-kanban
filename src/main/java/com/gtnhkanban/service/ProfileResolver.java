package com.gtnhkanban.service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Resolves known server player profiles without coupling the service to Forge player classes. */
public interface ProfileResolver {

    UUID resolveUsername(String username);

    String usernameFor(UUID playerId);

    /** Players worth suggesting when adding members: whitelisted players, or known players without a whitelist. */
    default List<String> suggestedUsernames() {
        return Collections.emptyList();
    }
}
