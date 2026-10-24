package dev.dailycareer.competency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses a competency sheet: front matter plus fixed `## ` sections (design §3.2). */
record SheetDocument(String code, String title, int version, String review, String body, Map<String, String> sections,
                     List<String> criteriaIds, List<Source> sources) {
    static final List<String> REQUIRED = List.of("핵심 개념", "판단 기준", "흔한 실수와 반례", "수준별 기대 답변",
            "꼬리질문 후보", "시나리오 재료", "공식 출처");
    private static final Pattern CRITERION = Pattern.compile("^- \\[(K[0-9]{1,2})]", Pattern.MULTILINE);
    private static final Pattern SOURCE = Pattern.compile("^- \\[([^]]+)]\\((https://[^)\\s]+)\\)", Pattern.MULTILINE);

    record Source(String title, String url) {}

    static SheetDocument parse(String text) {
        String normalized = text.replace("\r\n", "\n").replace("﻿", "");
        if (!normalized.startsWith("---\n")) throw new IllegalArgumentException("Sheet front matter missing");
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) throw new IllegalArgumentException("Sheet front matter not closed");
        Map<String, String> meta = new LinkedHashMap<>();
        for (String line : normalized.substring(4, end).split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) meta.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
        String body = normalized.substring(end + 5).strip() + "\n";
        Map<String, String> sections = new LinkedHashMap<>();
        String current = null;
        StringBuilder buffer = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            if (line.startsWith("## ")) {
                if (current != null) sections.put(current, buffer.toString().strip());
                current = line.substring(3).strip();
                buffer.setLength(0);
            } else if (current != null) buffer.append(line).append('\n');
        }
        if (current != null) sections.put(current, buffer.toString().strip());
        for (String required : REQUIRED) {
            if (!sections.containsKey(required) || sections.get(required).isBlank())
                throw new IllegalArgumentException("Sheet section missing: " + required);
        }
        var criteria = new LinkedHashSet<String>();
        Matcher criterion = CRITERION.matcher(sections.get("판단 기준"));
        while (criterion.find()) criteria.add(criterion.group(1));
        if (criteria.size() < 3) throw new IllegalArgumentException("Sheet needs at least three judgment criteria");
        var sources = new ArrayList<Source>();
        Matcher source = SOURCE.matcher(sections.get("공식 출처"));
        while (source.find()) sources.add(new Source(source.group(1), source.group(2)));
        if (sources.isEmpty()) throw new IllegalArgumentException("Sheet needs an official source");
        String code = required(meta, "code"), title = required(meta, "title");
        return new SheetDocument(code, title, Integer.parseInt(required(meta, "version")), required(meta, "review"), body,
                sections, List.copyOf(criteria), List.copyOf(sources));
    }

    /** The parts grading relies on; the full sheet is only sent when generating new tasks. */
    String gradingExcerpt() {
        return "## 판단 기준\n" + sections.get("판단 기준") + "\n\n## 흔한 실수와 반례\n" + sections.get("흔한 실수와 반례")
                + "\n\n## 수준별 기대 답변\n" + sections.get("수준별 기대 답변");
    }

    String followupExcerpt() {
        return "## 판단 기준\n" + sections.get("판단 기준") + "\n\n## 꼬리질문 후보\n" + sections.get("꼬리질문 후보");
    }

    private static String required(Map<String, String> meta, String key) {
        String value = meta.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Sheet front matter missing " + key);
        return value;
    }
}
