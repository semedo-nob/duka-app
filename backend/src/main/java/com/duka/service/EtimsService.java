package com.duka.service;

import com.duka.domain.EtimsStatus;
import com.duka.domain.EtimsSubmission;
import com.duka.domain.Sale;
import com.duka.integrations.etims.EtimsAdapter;
import com.duka.repo.EtimsSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class EtimsService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.of("Africa/Nairobi"));

    private final EtimsSubmissionRepository submissions;
    private final SubscriptionService subscriptions;
    private final EtimsAdapter adapter;

    public boolean enabledFor(Long businessId) {
        return businessId != null && Boolean.TRUE.equals(subscriptions.entitlements(businessId).get("etims"));
    }

    public void queue(Sale sale) {
        if (!enabledFor(parseBusiness(sale.getBusinessId())) || submissions.findBySaleId(sale.getId()).isPresent()) {
            return;
        }
        EtimsAdapter.SubmissionResult result = adapter.submit(sale.getId());
        EtimsSubmission submission = new EtimsSubmission();
        submission.setSaleId(sale.getId());
        submission.setStatus(EtimsStatus.PENDING);
        submission.setMessage(result.message());
        submission.setExternalRef(result.externalRef());
        submissions.save(submission);
    }

    @Transactional
    public String retryAll() {
        boolean due = submissions.findAll().stream()
                .anyMatch(submission -> submission.getStatus() == EtimsStatus.PENDING || submission.getStatus() == EtimsStatus.FAILED);
        if (!due) {
            return null;
        }
        EtimsAdapter.SubmissionResult probe = adapter.submit(0);
        int stillPending = 0;
        for (EtimsSubmission submission : submissions.findAll()) {
            if (submission.getStatus() == EtimsStatus.PENDING || submission.getStatus() == EtimsStatus.FAILED) {
                submission.setMessage(probe.message());
                submission.setStatus(EtimsStatus.PENDING);
                stillPending++;
            }
        }
        return stillPending + " invoice(s) remain pending. " + probe.message();
    }

    private static Long parseBusiness(String businessId) {
        if (businessId == null || businessId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(businessId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public String labelFor(Long saleId) {
        return submissions.findBySaleId(saleId).map(submission -> submission.getStatus().label()).orElse(null);
    }

    public DateTimeFormatter timeFormat() {
        return TIME;
    }
}
