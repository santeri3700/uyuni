/*
 * Copyright (c) 2013 Red Hat, Inc.
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
package com.redhat.rhn.frontend.action.iss.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * IssEditSlaveActionTest
 *
 */
public class EditSlaveSetupActionTest extends BaseIssTestAction {

    @Test
    public void testExecute() throws Exception {
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addRequestParameter("sid", slaveDto.getId().toString());
    }

    @Test
    public void testEditSlave() {
        doPerform(true);
        verifyFormValue("id", slaveDto.getId());
        verifyFormValue("slave", slaveDto.getSlave());
        verifyFormValue("enabled", ("Y".equals(slaveDto.getEnabled())));
        verifyFormValue("allowAllOrgs", ("Y".equals(slaveDto.getAllowAllOrgs())));
    }

    @Override
    protected String getUrl() {
        return "/admin/iss/EditSlave";
    }

    @Override
    protected String getListName() {
        return null;
    }

    @Override
    protected Class getListClass() {
        return null;
    }
}
