package com.campus.shared.application.reporting;

import java.time.Instant;
import com.campus.shared.application.PageResult;

/** Called inside the Reporting read-only snapshot transaction. */
public interface ReportQueryPort {
    ReportKind kind();
    PageResult<ReportRow> read(ReportSearch search, Instant asOf, boolean export);
    final class ExportLimitException extends RuntimeException { }
}
