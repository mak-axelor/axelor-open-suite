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
package com.axelor.apps.businessproject.web;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceServiceDemo;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.businessproject.service.InvoiceLineProjectService;
import com.axelor.apps.businessproject.service.InvoiceServiceProject;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Singleton;

@Singleton
public class InvoiceController {

  public Invoice getInvoice(Context context) {

    Context parentContext = context.getParent();

    if (parentContext == null) {
      return null;
    }

    if (parentContext.getContextClass().equals(Invoice.class)) {
      return parentContext.asType(Invoice.class);
    }
    return getInvoice(parentContext);
  }


  public void updateLines(ActionRequest request, ActionResponse response) throws AxelorException {
    try {
      Invoice invoice = request.getContext().asType(Invoice.class);
      invoice = Beans.get(InvoiceRepository.class).find(invoice.getId());
      invoice = Beans.get(InvoiceServiceProject.class).updateLines(invoice);
      response.setValue("invoiceLineList", invoice.getInvoiceLineList());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void createInvoiceLinesForSubProducts(ActionRequest request, ActionResponse response)
          throws AxelorException {
    Context context = request.getContext();
    InvoiceLine invoiceLine = context.asType(InvoiceLine.class);
    Invoice invoice = this.getInvoice(context);
    invoiceLine =
            Beans.get(InvoiceLineProjectService.class)
                    .createInvoiceLinesRelatedToSubProducts(invoiceLine, invoice);
    response.setValues(invoiceLine);
  }
}
