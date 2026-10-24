package dev.dailycareer.integration.notion;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
final class NotionCredentialStore {
    private final JdbcTemplate jdbc;
    private final NotionSecretCipher cipher;
    private final NotionSettings settings;

    NotionCredentialStore(JdbcTemplate jdbc, NotionSecretCipher cipher, NotionSettings settings) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.settings = settings;
    }

    String save(long user, String token) {
        String reference = UUID.randomUUID().toString();
        jdbc.update("""
                insert into daily_career.notion_credential(credential_ref,user_id,key_id,access_token_ciphertext)
                values (?::uuid,?,?,?)
                """, reference, user, settings.keyId(), cipher.encrypt(user, reference, token));
        return reference;
    }

    String load(long user, String reference) {
        String value = jdbc.queryForObject("""
                select access_token_ciphertext from daily_career.notion_credential
                where credential_ref=?::uuid and user_id=?
                """, String.class, reference, user);
        if (value == null) throw new IllegalStateException("Notion credential is unavailable");
        return cipher.decrypt(user, reference, value);
    }

    void delete(long user, String reference) {
        jdbc.update("delete from daily_career.notion_credential where credential_ref=?::uuid and user_id=?", reference, user);
    }
}
