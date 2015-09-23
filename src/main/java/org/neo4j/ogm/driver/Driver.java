package org.neo4j.ogm.driver;

import org.neo4j.ogm.driver.config.DriverConfig;
import org.neo4j.ogm.mapper.MappingContext;
import org.neo4j.ogm.session.request.Request;
import org.neo4j.ogm.session.transaction.Transaction;
import org.neo4j.ogm.session.transaction.TransactionManager;

/**
 * @author vince
 */
public interface Driver {

    public void configure(DriverConfig config);
    public Object getConfig(String key);
    public Transaction openTransaction(MappingContext context, TransactionManager tx, boolean autoCommit);
    public void close();
    public Request requestHandler();


}