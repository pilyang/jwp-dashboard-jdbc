package org.springframework.jdbc.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.JdbcException;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcTemplate {

    private static final Logger log = LoggerFactory.getLogger(JdbcTemplate.class);

    private final DataSource dataSource;

    public JdbcTemplate(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public int update(final String sql, final Object... args) {
        return executeExecution(sql, PreparedStatement::executeUpdate, args);
    }

    public <T> List<T> query(final String sql, final RowMapper<T> rowMapper, final Object... args) {
        return executeExecution(sql, pstm -> {
                    final ResultSet resultSet = pstm.executeQuery();
                    final List<T> results = new ArrayList<>();
                    while (resultSet.next()) {
                        results.add(rowMapper.map(resultSet));
                    }
                    log.debug("run sql {}", sql);
                    return results;
                },
                args);
    }

    public <T> T queryForObject(final String sql, final RowMapper<T> rowMapper, final Object... args) {
        return executeExecution(sql, pstm -> {
                    final ResultSet resultSet = pstm.executeQuery();
                    log.debug("run sql {}", sql);
                    if (resultSet.next()) {
                        final T result = rowMapper.map(resultSet);
                        validateIsOnlyResult(resultSet);
                        return result;
                    }
                    logAndThrowNoDataFoundException();
                    return null;
                },
                args);
    }

    private void validateIsOnlyResult(ResultSet resultSet) throws SQLException {
        if (resultSet.next()) {
            log.error("selected data count is larger than 1");
            throw new JdbcException("selected data count is larger than 1");
        }
    }

    private static void logAndThrowNoDataFoundException() {
        log.error("no data found");
        throw new JdbcException("no data found");
    }

    private <T> T executeExecution(final String sql, final JdbcExecution<T> execution, final Object[] args) {
        final Connection conn = DataSourceUtils.getConnection(dataSource);
        try (
                final PreparedStatement pstmt = getPrepareStatement(conn, sql, args);
        ) {
            return execution.excute(pstmt);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new JdbcException(e);
        } finally {
            closeConnectionIfAutoCommited(conn);
        }
    }

    private PreparedStatement getPrepareStatement(final Connection connection, final String sql, final Object[] args) throws SQLException {
        final PreparedStatement pstm = connection.prepareStatement(sql);
        setSqlParameters(pstm, args);
        return pstm;
    }

    private void setSqlParameters(final PreparedStatement pstmt, final Object[] args) throws SQLException {
        for (int parameterIndex = 0; parameterIndex < args.length; parameterIndex++) {
            pstmt.setObject(parameterIndex + 1, args[parameterIndex]);
        }
    }

    private void closeConnectionIfAutoCommited(final Connection conn) {
        try {
            if (conn.getAutoCommit()) {
                DataSourceUtils.releaseConnection(conn, dataSource);
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
