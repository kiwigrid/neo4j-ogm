package org.neo4j.ogm.cypher.compiler;

import static org.assertj.core.api.Assertions.*;

import java.util.Collection;
import java.util.Collections;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.Filters;
import org.neo4j.ogm.domain.gh613.Node;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;
import org.neo4j.ogm.testutil.MultiDriverTestClass;
import org.neo4j.ogm.testutil.TestUtils;

/**
 * @author Andreas Berger
 */
public class SavingTest extends MultiDriverTestClass {

    private Session session;

    @BeforeClass
    public static void initSesssionFactory() {
        sessionFactory = new SessionFactory(driver, "org.neo4j.ogm.domain.gh613");
    }

    @Before
    public void init() {
        session = sessionFactory.openSession();
        session.purgeDatabase();
        session.clear();

        session.query(TestUtils.readCQLFile("org/neo4j/ogm/cql/gh613.cql").toString(), Collections.emptyMap());
    }

    /**
     * GH-613
     */
    @Test
    public void testSaveParentAfterChild() {

        Node loc1_1 = queryNode("loc1_1");
        assertThat(loc1_1.getNodeType()).isNotNull();
        loc1_1.setLabels(null);
        session.save(loc1_1);

        Node loc1 = queryNode("loc1");
        assertThat(loc1.getChildNodes()).hasSize(3);
        assertThat(loc1.getNodeType()).isNotNull();
        session.save(loc1);

        loc1_1 = queryNode("loc1_1");
        assertThat(loc1_1.getNodeType()).isNotNull();
    }

    @Test
    public void testChangeParent() {

        Node loc2 = queryNode("loc2");
        Node loc1_1 = queryNode("loc1_1");
        Node loc1_2 = queryNode("loc1_2");

        loc1_1.setChildOfBidirectional(loc2);
        session.save(loc1_1);
        loc1_2.setChildOfBidirectional(loc2);
        session.save(loc1_2);

        Node loc1 = queryNode("loc1");
        assertThat(loc1.getChildNodes()).hasSize(1);

        loc2 = queryNode("loc2");
        assertThat(loc2.getChildNodes()).hasSize(2);
    }

    @Test
    public void testClearRelationship() {
        Node loc1 = queryNode("loc1");
        assertThat(loc1.getNodeType()).isNotNull();

        Node loc1_1 = queryNode("loc1_1");
        assertThat(loc1_1.getLabels()).hasSize(1);

        loc1_1.setLabels(null);
        session.save(loc1_1);

        Node root = queryNode("root");
        assertThat(root.getChildNodes()).hasSize(2);
        session.save(root);

        loc1 = queryNode("loc1");
        assertThat(loc1.getNodeType()).isNotNull();
    }

    private Node queryNode(String nodeId) {
        Collection<Node> nodeTypes = session
            .loadAll(Node.class, new Filters(new Filter("nodeId", ComparisonOperator.EQUALS, nodeId)));
        assertThat(nodeTypes).hasSize(1);
        return nodeTypes.iterator().next();
    }
}
