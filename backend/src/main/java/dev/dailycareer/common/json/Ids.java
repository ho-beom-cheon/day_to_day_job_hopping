package dev.dailycareer.common.json;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;

/** Explicit path/query conversion; never globally convert all Long metrics or revisions. */
public final class Ids {
    private Ids() {}
    public static long parse(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { throw new ApiException(ApiErrorCode.INVALID_REQUEST); }
    }
}
