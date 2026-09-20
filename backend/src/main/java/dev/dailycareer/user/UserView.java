package dev.dailycareer.user;

import dev.dailycareer.common.json.JsonId;
import java.time.OffsetDateTime;

public record UserView(@JsonId Long userId, String email, String nickname, String profileImageUrl,
                       String role, @JsonId Long curriculumId, int revision,
                       OffsetDateTime joinedAt, OffsetDateTime updatedAt) {}
