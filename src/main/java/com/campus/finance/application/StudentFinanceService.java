package com.campus.finance.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import com.campus.finance.domain.*;
import com.campus.shared.application.time.BusinessCalendar;
import com.campus.student.application.StudentAccountDirectory;
import com.campus.student.application.StudentProfileNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudentFinanceService {
    private final StudentAccountDirectory accounts;
    private final FinanceRepository charges;
    private final PaymentRepository payments;
    private final Clock clock;
    private final BusinessCalendar calendar;

    public StudentFinanceService(StudentAccountDirectory accounts, FinanceRepository charges,
                                 PaymentRepository payments, Clock clock, BusinessCalendar calendar) {
        this.accounts = accounts;
        this.charges = charges;
        this.payments = payments;
        this.clock = clock;
        this.calendar = calendar;
    }

    public FinanceSummary summary(UUID account) {
        UUID studentId = accounts.findByAccount(account).orElseThrow(StudentProfileNotFoundException::new).studentId();
        var rows = charges(studentId);
        var byCharge = payments.findByCharges(rows.stream().map(StudentCharge::id).toList()).stream()
                .collect(Collectors.groupingBy(ManualPayment::chargeId, LinkedHashMap::new, Collectors.toList()));
        var today = calendar.today();
        BigDecimal totalCharged = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        var views = new ArrayList<ChargeView>();
        for (var charge : rows) {
            var history = byCharge.getOrDefault(charge.id(), List.of());
            var paid = history.stream().filter(value -> value.status() == PaymentStatus.RECORDED)
                    .map(ManualPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean open = charge.status() == ChargeStatus.OPEN;
            if (open) {
                totalCharged = totalCharged.add(charge.amount());
                totalPaid = totalPaid.add(paid);
            }
            views.add(new ChargeView(charge.id(), charge.chargeNumber(), charge.feeCode(), charge.feeName(), charge.amount(),
                    charge.currency(), charge.dueDate(), charge.status(), paid, open ? charge.amount().subtract(paid) : BigDecimal.ZERO,
                    open && charge.dueDate().isBefore(today), history.stream().map(StudentFinanceService::payment).toList()));
        }
        return new FinanceSummary(studentId, "VND", Instant.now(clock), totalCharged, totalPaid,
                totalCharged.subtract(totalPaid), views);
    }

    private List<StudentCharge> charges(UUID studentId) {
        var result = new ArrayList<StudentCharge>();
        for (int page = 0; ; page++) {
            var values = charges.charges(new FinanceSearch(page, 100, null, null, studentId, null, "dueDate", true));
            result.addAll(values.content());
            if (values.content().isEmpty() || result.size() >= values.totalElements()) return result;
        }
    }

    private static PaymentView payment(ManualPayment value) {
        return new PaymentView(value.id(), value.receiptNumber(), value.amount(), value.status(), value.recordedAt(),
                value.reversedAt(), value.reversalReason());
    }

    public record FinanceSummary(UUID studentId, String currency, Instant asOf, BigDecimal totalCharged,
                                 BigDecimal totalPaid, BigDecimal outstanding, List<ChargeView> charges) { }
    public record ChargeView(UUID id, String chargeNumber, String feeCode, String feeName, BigDecimal amount, String currency,
                             LocalDate dueDate, ChargeStatus status, BigDecimal paid, BigDecimal outstanding, boolean overdue,
                             List<PaymentView> payments) { }
    public record PaymentView(UUID id, String receiptNumber, BigDecimal amount, PaymentStatus status, Instant recordedAt,
                              Instant reversedAt, String reversalReason) { }
}
