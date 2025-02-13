package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StoredArtifactMapper implements RowMapper<StoredArtifactDto> {

    public static final StoredArtifactMapper instance = new StoredArtifactMapper();

    /**
     * Constructor.
     */
    private StoredArtifactMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public StoredArtifactDto map(ResultSet rs) throws SQLException {
        return StoredArtifactDto.builder()
                .content(ContentHandle.create(rs.getBytes("content")))
                .contentId(rs.getLong("contentId"))
                .globalId(rs.getLong("globalId"))
                .version(rs.getString("version"))
                .versionOrder(rs.getInt("versionOrder"))
                .references(SqlUtil.deserializeReferences(rs.getString("artifactreferences")))
                .build();
    }
}
