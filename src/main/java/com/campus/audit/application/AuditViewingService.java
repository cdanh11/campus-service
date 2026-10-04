package com.campus.audit.application;

import java.util.*;
import com.campus.shared.application.PageResult;
import com.campus.shared.application.audit.*;
import org.springframework.stereotype.Service;

@Service
public class AuditViewingService {
    private final Map<AuditSource, AuditReadPort> owners;
    public AuditViewingService(List<AuditReadPort> ports) {
        var registered = new EnumMap<AuditSource, AuditReadPort>(AuditSource.class);
        for (var port : ports) if (registered.put(port.source(), port) != null)
            throw new IllegalStateException("Duplicate audit owner");
        if (registered.size() != AuditSource.values().length) throw new IllegalStateException("Missing audit owner");
        owners = Map.copyOf(registered);
    }
    public AuditView get(AuditSource source, UUID id) { return owner(source).find(id).orElseThrow(NotFoundException::new); }
    public PageResult<AuditView> search(AuditSource source, AuditSearch search) {
        var owner = owner(source); owner.policy().validate(search); return owner.search(search);
    }
    private AuditReadPort owner(AuditSource source) {
        if (source == null) throw new IllegalArgumentException("Audit source required"); return owners.get(source);
    }
    public static final class NotFoundException extends RuntimeException { }
}
