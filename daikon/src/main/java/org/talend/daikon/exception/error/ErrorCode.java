package org.talend.daikon.exception.error;

import java.io.Serializable;
import java.util.Collection;

/**
 * Interface used to unify error message behaviour.
 */
public interface ErrorCode extends Serializable {

    /**
     * @return the product used for the error message... Hum... TDP ? ;-)
     */
    String getProduct();

    /**
     * @return the group this message belongs to (API, DATASET, PREPARATION...)
     */
    String getGroup();

    /**
     * @return the http status to return.
     */
    int getHttpStatus();

    /**
     * @return the expected context entries if any.
     */
    Collection<String> getExpectedContextEntries();

    /**
     * @return the full code for this message.
     */
    String getCode();

}
