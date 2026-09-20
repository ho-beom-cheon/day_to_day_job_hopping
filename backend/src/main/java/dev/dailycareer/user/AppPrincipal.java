package dev.dailycareer.user;

import java.io.Serializable;
import java.security.Principal;

/** The JDBC session retains only an internal ID, never Google credentials or tokens. */
public record AppPrincipal(long userId) implements Principal, Serializable {
    @Override public String getName() { return Long.toString(userId); }
}
