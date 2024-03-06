/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2024 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.businessproject.service;

import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.AnalyticMoveLineRepository;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.InvoiceTermPfpService;
import com.axelor.apps.account.service.invoice.InvoiceTermService;
import com.axelor.apps.account.service.invoice.factory.CancelFactory;
import com.axelor.apps.account.service.invoice.factory.ValidateFactory;
import com.axelor.apps.account.service.invoice.factory.VentilateFactory;
import com.axelor.apps.account.service.invoice.print.InvoiceProductStatementService;
import com.axelor.apps.account.service.move.MoveToolService;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.tax.TaxService;
import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.service.IntercoService;
import com.axelor.apps.supplychain.service.invoice.InvoiceServiceSupplychainImpl;
import com.axelor.inject.Beans;
import com.axelor.message.service.TemplateMessageService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;

public class InvoiceServiceProjectImpl extends InvoiceServiceSupplychainImpl
    implements InvoiceServiceProject {

  @Inject
  public InvoiceServiceProjectImpl(
      ValidateFactory validateFactory,
      VentilateFactory ventilateFactory,
      CancelFactory cancelFactory,
      InvoiceRepository invoiceRepo,
      AppAccountService appAccountService,
      PartnerService partnerService,
      InvoiceLineService invoiceLineService,
      AccountConfigService accountConfigService,
      MoveToolService moveToolService,
      InvoiceTermService invoiceTermService,
      InvoiceTermPfpService invoiceTermPfpService,
      AppBaseService appBaseService,
      TaxService taxService,
      InvoiceProductStatementService invoiceProductStatementService,
      TemplateMessageService templateMessageService,
      InvoiceLineRepository invoiceLineRepo,
      IntercoService intercoService,
      StockMoveRepository stockMoveRepository) {
    super(
        validateFactory,
        ventilateFactory,
        cancelFactory,
        invoiceRepo,
        appAccountService,
        partnerService,
        invoiceLineService,
        accountConfigService,
        moveToolService,
        invoiceTermService,
        invoiceTermPfpService,
        appBaseService,
        taxService,
        invoiceProductStatementService,
        templateMessageService,
        invoiceLineRepo,
        intercoService,
        stockMoveRepository);
  }

  @Override
  @Transactional(rollbackOn = Exception.class)
  public void cancel(Invoice invoice) throws AxelorException {
    super.cancel(invoice);
    for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {
      for (AnalyticMoveLine analyticMoveLine : invoiceLine.getAnalyticMoveLineList()) {
        analyticMoveLine.setProject(null);
      }
    }
  }

  @Transactional(rollbackOn = Exception.class)
  public Invoice updateLines(Invoice invoice) {
    AnalyticMoveLineRepository analyticMoveLineRepository =
        Beans.get(AnalyticMoveLineRepository.class);
    for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {
      invoiceLine.setProject(invoice.getProject());
      for (AnalyticMoveLine analyticMoveLine : invoiceLine.getAnalyticMoveLineList()) {
        analyticMoveLine.setProject(invoice.getProject());
        analyticMoveLineRepository.save(analyticMoveLine);
      }
    }
    return invoice;
  }

  @Transactional(rollbackOn = Exception.class)
  @Override
  public void setTasksIndex(Set<ProjectTask> projectTaskSet) {
    for (ProjectTask projectTask : projectTaskSet) {
      if (projectTask.getTaskIndex() == null) {
        ProjectTask parent = projectTask.getParentTask();
        if (parent == null) {
          projectTask.setTaskIndex(calculatePrentSolLineIndex(projectTaskSet));
          setSubTasksIndex(projectTask);
        }
      }
    }
    for (ProjectTask projectTask : projectTaskSet) {
      Beans.get(ProjectTaskRepository.class).save(projectTask);
    }
  }

  protected void setSubTasksIndex(ProjectTask projectTask) {
    int count = 1;
    for (ProjectTask subTask : projectTask.getProjectTaskList()) {
      subTask.setTaskIndex(projectTask.getTaskIndex() + "." + count);
      count++;
      if (CollectionUtils.isEmpty(subTask.getProjectTaskList())) {
        return;
      }
      setSubTasksIndex(subTask);
    }
  }

  protected String calculatePrentSolLineIndex(Set<ProjectTask> projectTaskSet) {
    return projectTaskSet.stream()
        .filter(slo -> slo.getTaskIndex() != null)
        .map(slo -> slo.getTaskIndex().split("\\.")[0])
        .mapToInt(Integer::parseInt)
        .boxed()
        .collect(Collectors.maxBy(Integer::compareTo))
        .map(max -> String.valueOf(max + 1))
        .orElse("1");
  }
}
