package com.campus.reporting.application;

import java.time.Instant;
import java.util.*;
import com.campus.shared.application.reporting.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DetailReportService {
    private final Map<ReportKind,ReportQueryPort> owners;
    public DetailReportService(List<ReportQueryPort> ports) {
        var registered=new EnumMap<ReportKind,ReportQueryPort>(ReportKind.class);
        for(var port:ports) if(registered.put(port.kind(),port)!=null) throw new IllegalStateException("Duplicate report owner");
        if(registered.size()!=ReportKind.values().length) throw new IllegalStateException("Missing report owner");
        owners=Collections.unmodifiableMap(registered);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReportPage search(ReportKind kind,ReportSearch search) {
        kind.validate(search); Instant asOf=Instant.now();
        var result=owners.get(kind).read(search,asOf,false);
        return new ReportPage(kind,asOf,kind.columns(),result.content(),search.page(),search.size(),result.totalElements(),
                result.totalElements()/search.size()+(result.totalElements()%search.size()==0?0:1));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public String export(ReportKind kind,ReportSearch search) {
        kind.validate(search);
        var result=owners.get(kind).read(search,Instant.now(),true);
        return CsvReportWriter.write(kind.columns(),result.content());
    }
    public record ReportPage(ReportKind report,Instant asOf,List<String> columns,List<ReportRow> content,int page,int size,long totalElements,long totalPages) { }
}
