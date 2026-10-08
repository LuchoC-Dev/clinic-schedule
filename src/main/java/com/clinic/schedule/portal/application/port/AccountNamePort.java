package com.clinic.schedule.portal.application.port;

import java.util.UUID;

/**
 * The name an account goes by wherever something records who did it (D-81), for what the account does with nobody
 * logged in: confirming a registration forms the family group in its name (D-92).
 */
public interface AccountNamePort {

    String nameOf(UUID accountId);
}
