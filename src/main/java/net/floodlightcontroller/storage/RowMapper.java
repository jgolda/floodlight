package net.floodlightcontroller.storage;

/**
 * Type-safe version of IRowMapper
 * @param <RETURN_TYPE> the type, to which a row is mapped
 */
public interface RowMapper<RETURN_TYPE> {

    /**
     * Function which maps single row returned by query to a domain object
     */
    RETURN_TYPE mapRow(IResultSet resultSet);
}
