package com.permitping.application;

import com.permitping.domain.DocumentChaseDelivery;
import java.time.LocalDateTime;
import java.util.List;

public interface DocumentChaseDeliveryRepository {
    DocumentChaseDelivery save(DocumentChaseDelivery delivery);
    List<DocumentChaseDelivery> pending(LocalDateTime now, int limit);
}
