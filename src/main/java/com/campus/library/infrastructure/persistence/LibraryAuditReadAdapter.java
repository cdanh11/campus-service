package com.campus.library.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import com.campus.library.application.LibraryAuditQueries;
import com.campus.shared.application.PageResult;
import com.campus.shared.application.audit.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Queries only this owner's table; no raw metadata/private payload leaves PostgreSQL. */
@Repository
class LibraryAuditReadAdapter implements LibraryAuditQueries {
    private static final String TABLE = "library_audit_events";
    private static final String SELECT = "SELECT id,action,actor_user_id,resource_type AS resource,target_id AS target,resource_version AS version,occurred_at,CASE WHEN jsonb_typeof(metadata->'status')='string' AND char_length(metadata->>'status')<=16 THEN metadata->>'status' ELSE NULL END AS safe_status FROM " + TABLE;
    private final NamedParameterJdbcTemplate jdbc;
    LibraryAuditReadAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(readOnly = true) public Optional<AuditView> find(UUID id) {
        return jdbc.query(SELECT + " WHERE id=:id", Map.of("id",id), (row,index) -> view(row)).stream().findFirst();
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<AuditView> search(AuditSearch search) {
        policy().validate(search);
        var predicates = new ArrayList<String>(); var parameters = new HashMap<String,Object>();
        if (search.targetId() != null) { predicates.add("target_id=:target"); parameters.put("target",search.targetId()); }
        if (search.actorId() != null) { predicates.add("actor_user_id=:actor"); parameters.put("actor",search.actorId()); }
        if (search.resource() != null) { predicates.add("resource_type=:resource"); parameters.put("resource",search.resource()); }
        if (search.action() != null) { predicates.add("action=:action"); parameters.put("action",search.action()); }
        if (search.from() != null) { predicates.add("occurred_at>=:from"); parameters.put("from",Timestamp.from(search.from())); }
        if (search.until() != null) { predicates.add("occurred_at<:until"); parameters.put("until",Timestamp.from(search.until())); }
        String where = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ",predicates);
        long count = jdbc.queryForObject("SELECT count(*) FROM " + TABLE + where,parameters,Long.class);
        parameters.put("limit",search.size()); parameters.put("offset",search.page()*search.size());
        var content = jdbc.query(SELECT + where + " ORDER BY occurred_at " + (search.ascending()?"ASC":"DESC") + ",id ASC LIMIT :limit OFFSET :offset",parameters,(row,index) -> view(row));
        return new PageResult<>(content,count);
    }
    private AuditView view(ResultSet row) throws SQLException {
        var recordedVersion = (Number)row.getObject("version");
        String resource = row.getString("resource");
        return new AuditView(row.getObject("id",UUID.class),source(),resource,row.getObject("target",UUID.class),
                row.getObject("actor_user_id",UUID.class),row.getString("action"),recordedVersion==null?null:recordedVersion.longValue(),
                row.getTimestamp("occurred_at").toInstant(),policy().metadata(resource,row.getString("safe_status")));
    }
}
