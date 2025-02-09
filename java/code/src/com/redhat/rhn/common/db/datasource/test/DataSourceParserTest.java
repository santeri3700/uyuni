/*
 * Copyright (c) 2009--2014 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.rhn.common.db.datasource.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.redhat.rhn.common.conf.Config;
import com.redhat.rhn.common.conf.ConfigDefaults;
import com.redhat.rhn.common.db.datasource.DataResult;
import com.redhat.rhn.common.db.datasource.MapColumnNotFoundException;
import com.redhat.rhn.common.db.datasource.ModeFactory;
import com.redhat.rhn.common.db.datasource.ModeNotFoundException;
import com.redhat.rhn.common.db.datasource.ParameterValueNotFoundException;
import com.redhat.rhn.common.db.datasource.ParsedMode;
import com.redhat.rhn.common.db.datasource.ParsedQuery;
import com.redhat.rhn.common.db.datasource.SelectMode;
import com.redhat.rhn.common.hibernate.HibernateFactory;
import com.redhat.rhn.common.hibernate.HibernateHelper;
import com.redhat.rhn.common.util.manifestfactory.ManifestFactoryLookupException;
import com.redhat.rhn.testing.RhnBaseTestCase;

import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DataSourceParserTest extends RhnBaseTestCase {

    private String db_sufix;
    private String db_user;

    public DataSourceParserTest() {
        db_sufix = "_pg";
        db_user = Config.get().getString(ConfigDefaults.DB_USER);
    }

    @Test
    public void testGetModes() throws Exception {
        SelectMode m = ModeFactory.getMode("System_queries", "ssm_remote_commandable");
        assertNotNull(m);
    }

    @Test
    public void testGetModesNoFile() throws Exception {
        try {
            ModeFactory.getMode("Garbage", "ssm_remote_commandable");
            fail("Should have received an exception");
        }
        catch (ManifestFactoryLookupException e) {
            // Expected this exception, Garbage isn't a valid file.
        }
    }

    @Test
    public void testGetModesNoMode() throws Exception {
        try {
            ModeFactory.getMode("test_queries", "Garbage");
            fail("Should have received an exception");
        }
        catch (ModeNotFoundException e) {
            // Expected this exception, Garbage isn't a valid file.
        }
    }

    @Test
    public void testExternalElaborator() throws Exception {
        SelectMode m = ModeFactory.getMode("test_queries",
            "user_tables_external_elaborator" + db_sufix);
        assertNotNull(m);

        DataResult dr = m.execute(new HashMap());
        assertNotNull(dr);

        Iterator i = dr.iterator();
        int pos = 0;
        while (i.hasNext()) {
            Map hm = (Map)i.next();
            String name = (String)hm.get("username");

            if (name.toLowerCase().equals(db_user)) {
                dr = dr.subList(pos, pos + 1);
            }
            pos++;
        }

        Map parameters = new HashMap();
        parameters.put("user_name", db_user);
        dr.elaborate(parameters);
        assertNotNull(dr);

        i = dr.iterator();
        while (i.hasNext()) {
            Map hm = (Map)i.next();
            Map elab = (Map)hm.get("external_elaborator" + db_sufix);
            assertTrue(((Long)elab.get("table_count")).intValue() > 0);
        }
    }

    @Test
    public void testRunQuery() throws Exception {
        SelectMode m = ModeFactory.getMode("test_queries", "user_tables" + db_sufix);
        assertNotNull(m);

        DataResult dr = m.execute(new HashMap());
        assertNotNull(dr);

        Iterator i = dr.iterator();
        int pos = 0;
        while (i.hasNext()) {
            Map hm = (Map)i.next();
            String name = (String)hm.get("username");

            if (name.toLowerCase().equals(db_user)) {
                dr = dr.subList(pos, pos + 1);
            }
            pos++;
        }

        Map parameters = new HashMap();
        parameters.put("user_name", db_user);
        dr.elaborate(parameters);
        assertNotNull(dr);

        i = dr.iterator();
        while (i.hasNext()) {
            Map hm = (Map)i.next();
            Map elab = (Map)hm.get("table_elaborator" + db_sufix);
            assertTrue(((Long)elab.get("table_count")).intValue() > 0);
        }
    }

    private boolean shouldSkip(ParsedMode m) {
        /* Don't do plans for queries that use system tables or for
         * dummy queries.
         */
        return (m != null && m.getParsedQuery() != null &&
                (m.getName().equals("tablespace_overview") ||
                 m.getParsedQuery().getSqlStatement().trim().startsWith("--")));
    }

    @Test
    public void testPrepareAll() throws Exception {
        HibernateFactory.getSession().doWork(connection -> {
            PreparedStatement ps = null;
            try {
                Collection<?> fileSet = ModeFactory.getKeys();
                for (Object valueIn : fileSet) {
                    String file = (String) valueIn;

                    for (Object oIn : ModeFactory.getFileKeys(file).values()) {
                        ParsedMode m = (ParsedMode) oIn;

                        if (shouldSkip(m)) {
                            continue;
                        }
                        ParsedQuery pq = m.getParsedQuery();
                        if (pq != null) {
                            String query = pq.getSqlStatement();

                            // HACK: some of the queries actually have %s in them.
                            // So, replace all %s with :rbb so that the explain plan
                            // can be generated.
                            query = query.replaceAll("%s", ":rbb");

                            ps = connection.prepareStatement(query);
                        }
                    }
                }
            }
            finally {
                if (connection != null) {
                    connection.commit();
                }
                HibernateHelper.cleanupDB(ps);
            }
        });
    }

    private void runTestQuery(String queryName, String elabName) throws Exception {
        SelectMode m = ModeFactory.getMode("test_queries", queryName);
        assertNotNull(m);

        DataResult dr = m.execute(new HashMap());
        assertNotNull(dr);

        Iterator i = dr.iterator();
        // Pick the first three tables, just so that we aren't elaborating
        // all of the tables.
        dr = dr.subList(0, 3);

        dr.elaborate(new HashMap());
        assertNotNull(dr);
        assertEquals(3, dr.size());

        i = dr.iterator();
        while (i.hasNext()) {
            Map hm = (Map)i.next();
            List elab = (List)hm.get(elabName);
            assertTrue(!elab.isEmpty());
            for (Object oIn : elab) {
                Map curr = (Map) oIn;
                assertTrue(((Number) curr.get("column_id")).intValue() > 0);
                assertNotNull(curr.get("column_name"));
                assertNotNull(curr.get("table_name"));
            }
        }
    }

    @Test
    public void testPercentS() throws Exception {
        runTestQuery("all_tables" + db_sufix, "elaborator0");
    }

    @Test
    public void testBrokenDriving() throws Exception {
        try {
            runTestQuery("broken_driving" + db_sufix, "elaborator0");
            fail("Should have thrown an exception");
        }
        catch (MapColumnNotFoundException e) {
            assertEquals("Column, id, not found in driving query results",
                         e.getMessage());
        }
    }

    @Test
    public void testBrokenElaborator() throws Exception {
        try {
            runTestQuery("broken_elaborator" + db_sufix, "elaborator0");
            fail("Should have thrown an exception");
        }
        catch (MapColumnNotFoundException e) {
            assertEquals("Column, id, not found in elaborator results",
                         e.getMessage());
        }
    }

    @Test
    public void testAlias() throws Exception {
        runTestQuery("all_tables_with_alias" + db_sufix, "details" + db_sufix);
    }

    @Test
    public void testExtraParams() throws Exception {
        SelectMode m = ModeFactory.getMode("test_queries", "all_tables" + db_sufix);
        assertNotNull(m);

        Map<String, Object> params = new HashMap<>();
        params.put("foo", "bar");
        DataResult dr = m.execute(params);
        assertNotNull(dr);
    }

    @Test
    public void testDrivingParams() throws Exception {
        SelectMode m = ModeFactory.getMode("test_queries", "user_tables_for_user" +
                db_sufix);
        assertNotNull(m);

        Map hm = new HashMap();
        hm.put("username", db_user);
        DataResult dr = m.execute(hm);
        assertNotNull(dr);
        assertTrue(dr.size() > 0);
    }

    @Test
    public void testNullParam() throws Exception {
        SelectMode m = ModeFactory.getMode("test_queries", "user_tables_for_user" +
                db_sufix);
        assertNotNull(m);

        try {
            m.execute(new HashMap());
            fail("Should have received an exception");
        }
        catch (ParameterValueNotFoundException e) {
            assertTrue(e.getMessage().startsWith(
                        "Parameter 'username' not given for query:"));
        }
    }

    @Test
    public void testExternalQuery() throws Exception {
        SelectMode m = ModeFactory.getMode("System_queries", "visible_to_uid");
        Map<String, Object> params = new HashMap<>();
        params.put("formvar_uid", 12345L);
        DataResult dr = m.execute(params);
        assertEquals(m, dr.getMode());
    }

    @Test
    public void testSpecifiedClass() throws Exception {
        SelectMode m = ModeFactory.getMode("test_queries", "withClass" + db_sufix);
        String clazz = m.getClassString();
        assertEquals("com.redhat.rhn.common.db.datasource.test.TableData", clazz);
    }

    @Test
    public void testSpecifiedClassExecute() throws Exception {
        SelectMode m = ModeFactory.getMode("test_queries", "withClass" + db_sufix);
        String clazz = m.getClassString();
        assertEquals("com.redhat.rhn.common.db.datasource.test.TableData", clazz);
        DataResult dr = m.execute(new HashMap());
        assertNotNull(dr);
        assertTrue(dr.size() > 0);
        Iterator i = dr.iterator();
        TableData first = (TableData)i.next();
        assertTrue(first.getTableName().toLowerCase().startsWith("rhn"));
    }

    @Test
    public void testClassElaborateList() throws Exception {
        SelectMode m = ModeFactory.getMode("test_queries", "withClass" + db_sufix);
        String clazz = m.getClassString();
        assertEquals("com.redhat.rhn.common.db.datasource.test.TableData", clazz);
        DataResult dr = m.execute(new HashMap());
        assertNotNull(dr);
        assertTrue(dr.size() > 0);
        dr = dr.subList(0, 1);
        dr.elaborate(new HashMap());

        Iterator i = dr.iterator();
        TableData first = (TableData)i.next();
        assertTrue(first.getTableName().toLowerCase().startsWith("rhn"));
        assertTrue(!first.getColumnName().isEmpty());
        assertTrue(!first.getColumnId().isEmpty());
    }

    @Test
    public void testSpecifiedClassElaborate() throws Exception {
        SelectMode m = ModeFactory.getMode("test_queries", "user_class" + db_sufix);
        String clazz = m.getClassString();
        assertEquals("com.redhat.rhn.common.db.datasource.test.UserData", clazz);
        Map hm = new HashMap();
        hm.put("username", db_user);
        DataResult dr = m.execute(hm);
        assertNotNull(dr);
        assertTrue(dr.size() > 0);

        dr.elaborate(hm);

        Iterator i = dr.iterator();
        UserData first = (UserData)i.next();
        assertNotNull(first.getUsername());
        assertTrue(first.getTableCount().intValue() > 0);
    }
}
