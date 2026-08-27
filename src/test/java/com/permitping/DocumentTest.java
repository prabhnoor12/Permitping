package com.permitping;

import com.permitping.domain.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentTest {
    private final Clock clock=Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"),ZoneOffset.UTC);
    @Test void statusUsesConfiguredClockAndBoundaries(){
        assertEquals(ComplianceStatus.EXPIRED, document(-1).status(clock));
        assertEquals(ComplianceStatus.EXPIRING_SOON, document(30).status(clock));
        assertEquals(ComplianceStatus.CURRENT, document(31).status(clock));
    }
    private Document document(int days){return new Document(0,"Test","Permit","ACME","Job",LocalDate.of(2026,8,26).plusDays(days),"","");}
}
