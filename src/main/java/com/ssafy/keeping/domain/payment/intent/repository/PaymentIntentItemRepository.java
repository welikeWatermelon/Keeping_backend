package com.ssafy.keeping.domain.payment.intent.repository;

import com.ssafy.keeping.domain.payment.intent.model.PaymentIntentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentIntentItemRepository extends JpaRepository<PaymentIntentItem, Long> {
    List<PaymentIntentItem> findByIntent_IntentId(Long intentId);
}