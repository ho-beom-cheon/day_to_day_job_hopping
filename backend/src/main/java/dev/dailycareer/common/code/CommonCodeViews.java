package dev.dailycareer.common.code;

import java.util.List;

public final class CommonCodeViews {
    private CommonCodeViews() {}
    public record CodeItem(String code,String label,Integer sortOrder,Boolean active) {}
    public record CommonCodes(List<CodeItem> categories,String timeZone) {}
}
