package com.duka.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EtimsRetryJob {
    private static final Logger log = LoggerFactory.getLogger(EtimsRetryJob.class);

    private final EtimsService etims;

    @Scheduled(fixedDelayString = "${duka.etims.retry-ms:60000}", initialDelayString = "${duka.etims.retry-ms:60000}")
    public void retryPending() {
        String message = etims.retryAll();
        if (message != null) {
            log.info("eTIMS retry: {}", message);
        }
    }
}
