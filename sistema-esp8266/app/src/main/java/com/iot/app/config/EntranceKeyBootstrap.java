package com.iot.app.config;

import com.iot.app.service.AuthorizedKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Registers the household tag from {@code ENTRADA_UID} when that uid is not already stored.
 */
@Component
public class EntranceKeyBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(EntranceKeyBootstrap.class);

    private final AuthorizedKeyService keys;
    private final String uid;

    public EntranceKeyBootstrap(
            AuthorizedKeyService keys,
            @Value("${entrada.uid:}") String uid) {
        this.keys = keys;
        this.uid = uid;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (uid == null || uid.isBlank()) {
            return;
        }
        if (keys.isRegistered(uid)) {
            logger.info("Entrada: tag do ambiente ja cadastrada");
            return;
        }
        keys.add(uid, "kit");
        logger.info("Entrada: tag do ambiente cadastrada");
    }
}
