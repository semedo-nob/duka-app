package com.duka.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionLifecycleJob {
    private final SubscriptionService subscriptions;

    @Scheduled(fixedDelay = 3600_000)
    public void tick() {
        subscriptions.advanceLifecycle();
    }
}
