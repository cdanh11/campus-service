# ADR 0010: Manual Receipt Lifecycle and Charge Balance Protection

## Status

Accepted for 4B2 implementation. User selected partial payments, retained full-receipt reversal/reason and cancellation only with zero effective payments on 2026-10-04.

## Decision

Finance owns immutable VND receipt identity/charge/amount/currency/recorded time, RECORDED/REVERSED status, receipt version and reversal reason/time. Receipt number is globally unique and cannot be reused after reversal. Partial payments must not exceed remaining balance; reversal cancels the entire recorded amount, not history. No gateway, real refund transfer, FX, ledger or automatic billing.

All payment, reversal and charge-cancellation operations first refresh/lock the charge; reversal then refreshes/locks the receipt. Receipts are not locked before charges. Payment and reversal require expectedChargeVersion; reversal also requires expectedVersion of receipt. Every successful payment/reversal explicitly advances the charge version even if timestamps coincide. Existing charge snapshots and OPEN/CANCELLED lifecycle remain unchanged.

Net paid amount is SUM of RECORDED receipts. Cancellation requires zero; posting requires OPEN and amount ≤ charge amount minus net paid. CANCELLED retains the original amount snapshot but has zero collectible outstanding. Active Student/fee status is not rechecked to settle retained obligations. Reversal is terminal, reason 2–500 trimmed Unicode characters. Balance/version reads use one database query/snapshot; no mutable balance column or occupancy-style counter.

One synchronous PAYMENT audit event (RECORDED/REVERSED) shares each mutation transaction, including charge-version advancement. Audit failure rolls back all rows, versions and balance; reason stays on receipt rather than status-only audit. Existing inventory/obligation audit rules are preserved by V22 policy expansion. V1–V21 are immutable.

## Consequences and Limits

- Charge locking serializes competitors and cancellation with payment. Receipt uniqueness also protects cross-charge races.
- Database constraints enforce local amount/status/reversal shape and references; aggregate overpayment and cancellation rules belong to supported application transactions. Direct SQL bypass is unsupported.
- No nested Student/fee locks, new paid/overdue status, deletion, partial reversal or backdated settlement.
- Historical V21 validation freezes 24 production entities; exact V22 validation includes 25.
- Consumers must use exact VND values and reload charge/balance version before retrying a conflict.

Revisit for accounting, reconciliation, gateway callbacks, refunds or measured lock contention while retaining exact money, immutable history and atomic audit.
