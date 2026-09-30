package com.academic.management.service;

import com.academic.management.exception.AppException;
import com.academic.management.exception.BusinessRuleException;
import com.academic.management.exception.RecordNotFoundException;
import com.academic.management.util.AppLogger;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared behaviour for every service.
 *
 * <h2>Why services exist at all</h2>
 * The DAOs persist. They do not decide. A DAO will happily insert a
 * student whose email is already taken or a result for a student who was
 * never enrolled, because enforcing either of those rules needs a second
 * query and a judgement call - and a DAO that starts making judgement
 * calls stops being a DAO.
 *
 * <p>So the rules live here, where they can be tested without a database
 * mock, and the services are the only thing the UI is allowed to talk to.
 * Every method that reaches MySQL does so through a DAO this class owns.
 *
 * <h2>Rules that deliberately are not here</h2>
 * Field-level validation (is this an email, is this semester in range)
 * already lives in the domain model, because an invalid
 * {@link com.academic.management.model.Student} cannot be constructed at
 * all. Re-checking it here would be duplicated logic that could drift out
 * of step with the model. What these services add is the
 * <em>cross-record</em> rules the model cannot see: uniqueness against
 * existing rows, referential integrity, and the workflow rules like "a
 * result may only be published for an enrolled student".
 */
public abstract class BaseService {

    protected final transient Logger logger = AppLogger.getLogger(getClass());

    /** A human-readable name for this service, used in error messages. */
    protected abstract String entityName();

    /**
     * Returns the required record, or throws a not-found error.
     *
     * @param found the result of a lookup that may be empty
     * @param id    the identifier that was searched for
     * @param <T>   the record type
     * @return the record, guaranteed non-null
     * @throws RecordNotFoundException when the lookup was empty
     */
    protected <T> T requireFound(Optional<T> found, String id) throws AppException {
        if (found.isEmpty()) {
            throw new RecordNotFoundException(entityName(), id);
        }
        return found.get();
    }

    /**
     * Fails when a collection is unexpectedly empty.
     *
     * @param rows   the rows that were expected to contain something
     * @param reason what the user should be told
     * @param <T>    the element type
     * @return {@code rows}, guaranteed non-empty
     */
    protected <T> List<T> requireAny(List<T> rows, String reason) throws AppException {
        if (rows == null || rows.isEmpty()) {
            logger.log(Level.INFO, reason);
            throw new BusinessRuleException(reason);
        }
        return rows;
    }

    /** Logs an informational event; the UI shows its own feedback. */
    protected void log(String message) {
        logger.log(Level.INFO, message);
    }
}
