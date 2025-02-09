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
package com.redhat.rhn.frontend.action.configuration.ssm;

import com.redhat.rhn.common.db.datasource.DataResult;
import com.redhat.rhn.common.util.DatePicker;
import com.redhat.rhn.domain.action.ActionFactory;
import com.redhat.rhn.domain.action.ActionType;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.action.MaintenanceWindowsAware;
import com.redhat.rhn.frontend.dto.ConfigSystemDto;
import com.redhat.rhn.frontend.listview.PageControl;
import com.redhat.rhn.frontend.struts.ActionChainHelper;
import com.redhat.rhn.frontend.struts.BaseListAction;
import com.redhat.rhn.frontend.struts.MaintenanceWindowHelper;
import com.redhat.rhn.frontend.struts.RequestContext;
import com.redhat.rhn.manager.configuration.ConfigurationManager;
import com.redhat.rhn.manager.rhnset.RhnSetDecl;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.DynaActionForm;

import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

/**
 * DiffConfirmAction
 */
public class ConfigConfirmAction extends BaseListAction implements MaintenanceWindowsAware {

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataResult getDataResult(RequestContext rctxIn, PageControl pcIn) {
        User user = rctxIn.getCurrentUser();
        String feature  = rctxIn.getRequest().getParameter("feature");
        return ConfigurationManager.getInstance().listSystemsForConfigAction(user, pcIn,
                                    feature);
    }

    @Override
    protected void processRequestAttributes(RequestContext rctxIn) {
        User user = rctxIn.getCurrentUser();
        int size = RhnSetDecl.CONFIG_FILE_NAMES.get(user).size();
        rctxIn.getRequest().setAttribute("filenum", size);
        super.processRequestAttributes(rctxIn);
    }

    @Override
    protected void processPageControl(PageControl pcIn) {
        pcIn.setFilter(true);
        pcIn.setFilterColumn("name");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void processForm(RequestContext ctxt, ActionForm formIn) {
        if (formIn == null) {
            return; //no date picker on diff page
        }

        DynaActionForm dynaForm = (DynaActionForm) formIn;
        DatePicker picker = getStrutsDelegate().prepopulateDatePicker(ctxt.getRequest(),
                dynaForm, "date", DatePicker.YEAR_RANGE_POSITIVE);
        ctxt.getRequest().setAttribute("date", picker);

        Set<Long> systems = getSystemIds(ctxt, ActionFactory.TYPE_CONFIGFILES_DEPLOY);
        populateMaintenanceWindows(ctxt.getRequest(), systems);
        ActionChainHelper.prepopulateActionChains(ctxt.getRequest());
    }

    private Set<Long> getSystemIds(RequestContext ctxt, ActionType actionType) {
        ConfigurationManager cm = ConfigurationManager.getInstance();
        return cm.listSystemsForConfigAction(ctxt.getCurrentUser(), null, actionType.getLabel()).stream()
                .map(ConfigSystemDto::getId)
                .collect(Collectors.toSet());
    }

    @Override
    public void populateMaintenanceWindows(HttpServletRequest request, Set<Long> systemIds) {
        // we only handle 'deploy' actions here. for 'diff' actions, we early return at the beginning of processForm
        if (ActionFactory.TYPE_CONFIGFILES_DEPLOY.isMaintenancemodeOnly()) {
            MaintenanceWindowHelper.prepopulateMaintenanceWindows(request, systemIds);
        }
    }
}
