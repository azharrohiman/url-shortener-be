package dev.azhar.url_shortener.service;

import dev.azhar.url_shortener.model.AliasCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Domain probe (D17): the service reports what happened; this class decides how it is recorded.
@Slf4j
@Component
public class AliasInstrumentation {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aliasCreated(AliasCreatedEvent event) {
        log.atInfo()
                .setMessage("Alias created")
                .addKeyValue("alias", event.alias())
                .log();
    }

    public void aliasLengthExceeded(String alias) {
        log.atInfo()
                .setMessage("Alias length exceeded")
                .addKeyValue("alias", alias)
                .log();
    }

    public void aliasNotFound(String alias) {
        log.atInfo()
                .setMessage("Alias not found")
                .addKeyValue("alias", alias)
                .log();
    }

    public void linkBroken(String alias) {
        log.atError()
                .setMessage("Link broken")
                .addKeyValue("alias", alias)
                .log();
    }
}
