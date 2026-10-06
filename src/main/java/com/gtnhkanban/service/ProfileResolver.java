package com.gtnhkanban.service;

import java.util.UUID;

/** Resolves known server player profiles without coupling the service to Forge player classes. */
public interface ProfileResolver {

    UUID resolveUsername(String username);

    String usernameFor(UUID playerId);
}
