package com.smartshift.scheduler;

import com.smartshift.service.LarkIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LarkDeliveryScheduler {

    private final LarkIntegrationService larkIntegrationService;

    @Scheduled(
        initialDelayString = "${app.integrations.lark.worker.initial-delay-ms:5000}",
        fixedDelayString = "${app.integrations.lark.worker.fixed-delay-ms:5000}"
    )
    public void deliverPendingMessages() {
        try {
            larkIntegrationService.processPending();
        } catch (RuntimeException exception) {
            log.error("Unable to process pending Lark deliveries", exception);
        }
    }
}
