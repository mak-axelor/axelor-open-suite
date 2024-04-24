/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2022 Axelor (<http://axelor.com>).
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
package com.axelor.apps.account.service.invoice;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.db.repo.AccountConfigRepository;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.tax.TaxService;
import com.axelor.common.ObjectUtils;
import com.axelor.db.mapper.Mapper;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;

public class InvoiceServiceDemo {

  protected final InvoiceLineRepository invoiceLineRepository;

  protected final TaxService taxService;

  protected final AppBaseService appBaseService;
  protected final InvoiceLineService invoiceLineService;

  @Inject
  public InvoiceServiceDemo(
      TaxService taxService,
      AppBaseService appBaseService,
      InvoiceLineRepository invoiceLineRepository,
      InvoiceLineService invoiceLineService) {
    this.invoiceLineRepository = invoiceLineRepository;
    this.taxService = taxService;
    this.appBaseService = appBaseService;
    this.invoiceLineService = invoiceLineService;
  }

  public List<InvoiceLine> updateRelatedLines(InvoiceLine dirtyLine, Invoice invoice)
      throws AxelorException {
    List<InvoiceLine> items = invoice.getExpendableInvoiceLineList();
    if (CollectionUtils.isEmpty(items)) {
      return items;
    }
    updateChild(dirtyLine, invoice);
    replaceDirtyLineInItems(dirtyLine, items);
    updateParent(dirtyLine, items, invoice);
    return items;
  }

  protected void updateChild(InvoiceLine line, Invoice invoice) throws AxelorException {
    List<InvoiceLine> items = line.getSubInvoiceLineList();
    BigDecimal qtyCoef = getQtyCoef(line);
    BigDecimal priceCoef = getPriceCoef(line);
    if (ObjectUtils.isEmpty(items)
        || (BigDecimal.ONE.compareTo(qtyCoef) == 0 && BigDecimal.ONE.compareTo(priceCoef) == 0)) {
      return;
    }
    for (InvoiceLine orderLine : items) {
      updateValues(orderLine, qtyCoef, priceCoef, invoice);
    }
  }

  protected void updateValues(
      InvoiceLine invoiceLine, BigDecimal qtyCoef, BigDecimal priceCoef, Invoice invoice)
      throws AxelorException {
    updateQty(invoiceLine, qtyCoef);
    updatePrice(invoiceLine, priceCoef);
    invoiceLine.setExTaxTotal(invoiceLine.getQty().multiply(invoiceLine.getPrice()));
    computeAllValues(invoiceLine, invoice);
    // setDefaultSaleOrderLineProperties(orderLine, order);
    invoiceLine.setPriceBeforeUpdate(invoiceLine.getPrice());
    invoiceLine.setQtyBeforeUpdate(invoiceLine.getQty());
    List<InvoiceLine> items = invoiceLine.getSubInvoiceLineList();
    if (ObjectUtils.isEmpty(items)) {
      return;
    }
    for (InvoiceLine line : items) {
      updateValues(line, qtyCoef, priceCoef, invoice);
    }
  }

  protected void updateQty(InvoiceLine invoiceLine, BigDecimal qtyCoef) {
    BigDecimal qty =
        invoiceLine.getQty().equals(BigDecimal.ZERO) ? BigDecimal.ONE : invoiceLine.getQty();
    invoiceLine.setQty(qtyCoef.multiply(qty).setScale(2, RoundingMode.HALF_EVEN));
  }

  protected void updatePrice(InvoiceLine invoiceLine, BigDecimal priceCoef) {
    BigDecimal newPrice =
        priceCoef
            .multiply(
                invoiceLine.getPrice().signum() == 0 ? BigDecimal.ONE : invoiceLine.getPrice())
            .setScale(4, RoundingMode.HALF_EVEN);
    invoiceLine.setPrice(newPrice);
  }

  protected boolean replaceDirtyLineInItems(InvoiceLine dirtyLine, List<InvoiceLine> items) {
    if (items == null) {
      return false;
    }

    int i = 0;
    for (InvoiceLine invoiceLine : items) {
      if (isEqual(invoiceLine, dirtyLine)) {
        items.set(i, dirtyLine);
        return true;
      }
      if (invoiceLine.getSubInvoiceLineList() != null
          && replaceDirtyLineInItems(dirtyLine, invoiceLine.getSubInvoiceLineList())) {
        return true;
      }
      i++;
    }
    return false;
  }

  protected void updateParent(InvoiceLine dirtyLine, List<InvoiceLine> list, Invoice invoice)
      throws AxelorException {
    for (InvoiceLine invoiceLine : list) {
      if (isOrHasDirtyLine(invoiceLine, dirtyLine)) {
        compute(invoiceLine, invoice, invoiceLine.getLineIndex());
      }
    }
  }

  protected void compute(InvoiceLine invoiceLine, Invoice invoice, String parent)
      throws AxelorException {
    List<InvoiceLine> items = invoiceLine.getSubInvoiceLineList();
    if (ObjectUtils.isEmpty(items)) {
      return;
    }
    BigDecimal quantity = invoiceLine.getQty();
    BigDecimal totalPrice = BigDecimal.ZERO;
    int i = 1;
    for (InvoiceLine line : items) {
      line.setLineIndex(parent + "." + i);
      compute(line, invoice, parent + "." + i);
      totalPrice = totalPrice.add(line.getExTaxTotal());
      i++;
    }
    totalPrice = quantity.equals(BigDecimal.ZERO) ? BigDecimal.ZERO : totalPrice;
    BigDecimal price =
        quantity.equals(BigDecimal.ZERO)
            ? BigDecimal.ZERO
            : totalPrice.divide(quantity, 4, RoundingMode.HALF_EVEN);
    invoiceLine.setPrice(price);
    invoiceLine.setExTaxTotal(totalPrice);
    computeAllValues(invoiceLine, invoice);
    invoiceLine.setPriceBeforeUpdate(invoiceLine.getPrice());
    invoiceLine.setQtyBeforeUpdate(invoiceLine.getQty());
  }

  protected boolean isOrHasDirtyLine(InvoiceLine invoiceLine, InvoiceLine dirtyLine) {

    if (isEqual(invoiceLine, dirtyLine)) {
      return true;
    }
    if (invoiceLine.getSubInvoiceLineList() == null) {
      return false;
    }

    List<InvoiceLine> items = invoiceLine.getSubInvoiceLineList();

    for (InvoiceLine line : items) {
      if (isOrHasDirtyLine(line, dirtyLine)) {
        return true;
      }
    }
    return false;
  }

  protected BigDecimal getPriceCoef(InvoiceLine line) {
    BigDecimal oldPrice =
        line.getPriceBeforeUpdate().signum() == 0 ? BigDecimal.ONE : line.getPriceBeforeUpdate();
    return line.getPrice().divide(oldPrice, 4, RoundingMode.HALF_EVEN);
  }

  protected BigDecimal getQtyCoef(InvoiceLine line) {
    BigDecimal oldQty =
        line.getQtyBeforeUpdate().equals(BigDecimal.ZERO)
                || !(line.getQtyBeforeUpdate() instanceof BigDecimal)
            ? BigDecimal.ONE
            : line.getQtyBeforeUpdate();
    return line.getQty().divide(oldQty, 4, RoundingMode.HALF_EVEN);
  }

  @SuppressWarnings("unchecked")
  public InvoiceLine findDirtyLine(List<Map<String, Object>> list) {
    if (list == null) {
      return null;
    }
    for (Map<String, Object> invoiceLine : list) {
      Object items = invoiceLine.get("subInvoiceLineList");
      if (isChanged(invoiceLine)) {
        return getDirtyLine(invoiceLine);
      }
      if (items == null) {
        continue;
      }
      InvoiceLine dirtyLine = findDirtyLine((List<Map<String, Object>>) items);
      if (dirtyLine != null) {
        return dirtyLine;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  protected InvoiceLine getDirtyLine(Map<String, Object> orderLine) {
    InvoiceLine bean = Mapper.toBean(InvoiceLine.class, orderLine);
    if (orderLine.get("_original") != null) {
      InvoiceLine oldValue =
          Mapper.toBean(InvoiceLine.class, (Map<String, Object>) orderLine.get("_original"));
      bean.setQtyBeforeUpdate(oldValue.getQty());
      bean.setPriceBeforeUpdate(oldValue.getPrice());
    }
    if (bean.getId() != null && bean.getSubInvoiceLineList() == null) {
      InvoiceLine line2 = invoiceLineRepository.find(bean.getId());
      bean.setSubInvoiceLineList(line2.getSubInvoiceLineList());
    }
    return bean;
  }

  protected boolean isChanged(Map<String, Object> isChanged) {
    return Boolean.TRUE.equals(isChanged.get("_changed"));
  }

  protected boolean isEqual(InvoiceLine a, InvoiceLine b) {
    boolean isId = b.getId() != null && a.getId() != null;
    return isId
        ? a.getId().equals(b.getId())
        : (a.getCid() != null && a.getCid().equals(b.getCid()));
  }

  public void synchronizeInvoiceLineList(Invoice invoice) {
    invoice.clearInvoiceLineList();
    if (invoice.getExpendableInvoiceLineList() != null) {

      // Copy childrens
      if (invoice.getCompany().getAccountConfig().getCountTypeSelect()
          == AccountConfigRepository.COUNT_ONLY_CHILDREN) {
        for (var expendableSaleOrderLineList : invoice.getExpendableInvoiceLineList()) {
          synchronizeInvoiceLineList(expendableSaleOrderLineList.getSubInvoiceLineList(), invoice);
        }
      }

      // Copy parents
      if (invoice.getCompany().getAccountConfig().getCountTypeSelect()
          == AccountConfigRepository.COUNT_ONLY_PARENTS) {
        for (InvoiceLine invoiceLine : invoice.getExpendableInvoiceLineList()) {
          copyToInvoiceLineList(invoice, invoiceLine);
        }
      }
    }
  }

  protected void synchronizeInvoiceLineList(List<InvoiceLine> invoiceLineList, Invoice invoice) {
    // This method will only copy last childrens
    if (invoiceLineList != null) {
      for (InvoiceLine invoiceLine : invoiceLineList) {
        if (invoiceLine.getSubInvoiceLineList() != null
            && !invoiceLine.getSubInvoiceLineList().isEmpty()) {
          synchronizeInvoiceLineList(invoiceLine.getSubInvoiceLineList(), invoice);
        } else {
          copyToInvoiceLineList(invoice, invoiceLine);
        }
      }
    }
  }

  protected void copyToInvoiceLineList(Invoice invoice, InvoiceLine invoiceLine) {
    copyInvoiceLine(invoice, invoiceLine);
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void copyInvoiceLine(Invoice invoice, InvoiceLine invoiceLine) {
    InvoiceLine copy = invoiceLineRepository.copy(invoiceLine, false);
    copy.clearSubInvoiceLineList();
    copy.setParentLine(null);
    copy.setRootInvoice(null);
    copy.setParentCid(invoiceLine.getCid());
    invoice.addInvoiceLineListItem(copy);
  }

  protected void computeAllValues(InvoiceLine invoiceLine, Invoice invoice) throws AxelorException {
    BigDecimal exTaxPrice = invoiceLine.getPrice();
    Set<TaxLine> taxLineSet = invoiceLine.getTaxLineSet();
    BigDecimal inTaxPrice =
        taxService.convertUnitPrice(
            false, taxLineSet, exTaxPrice, appBaseService.getNbDecimalDigitForUnitPrice());
    invoiceLine.setInTaxPrice(inTaxPrice);
    invoiceLineService.compute(invoice, invoiceLine);
  }

  public String calculateParentInvoiceLineIndex(Invoice invoice) {
    return invoice.getExpendableInvoiceLineList().stream()
        .filter(il -> il.getLineIndex() != null)
        .map(il -> il.getLineIndex().split("\\.")[0])
        .mapToInt(Integer::parseInt)
        .boxed()
        .collect(Collectors.maxBy(Integer::compareTo))
        .map(max -> String.valueOf(max + 1))
        .orElse("1");
  }

  public InvoiceLine setSOLineStartValues(InvoiceLine invoiceLine, Context context) {
    if (invoiceLine.getLineIndex() == null) {
      Context parentContext = context.getParent();
      if (parentContext != null && parentContext.getContextClass().equals(Invoice.class)) {
        Invoice parent = parentContext.asType(Invoice.class);
        if (parent.getExpendableInvoiceLineList() != null) {
          invoiceLine.setLineIndex(calculateParentInvoiceLineIndex(parent));
        } else {
          invoiceLine.setLineIndex("1");
        }
      }

      if (context.getParent() != null
          && context.getParent().getContextClass().equals(InvoiceLine.class)) {
        InvoiceLine parent = context.getParent().asType(InvoiceLine.class);
        if (parent.getSubInvoiceLineList() != null) {
          invoiceLine.setLineIndex(
              parent.getLineIndex() + "." + (parent.getSubInvoiceLineList().size() + 1));
        } else {
          invoiceLine.setLineIndex(parent.getLineIndex() + ".1");
        }
      }
    }
    return invoiceLine;
  }

  public void recalculateAllPrices(Context context, Invoice invoice) throws AxelorException {
    int i = 1;
    for (InvoiceLine invoiceLine : invoice.getExpendableInvoiceLineList()) {
      invoiceLine.setLineIndex(String.valueOf(i));
      compute(invoiceLine, invoice, String.valueOf(i));
      i++;
    }
  }

  public void removeLines(Invoice invoice) {
    Set<Long> expendableSaleOrderLineCids = new HashSet<>();
    for (InvoiceLine invoiceLine : invoice.getExpendableInvoiceLineList()) {
      expendableSaleOrderLineCids.add(invoiceLine.getCid());
    }
    List<InvoiceLine> saleOrderLineList = invoice.getInvoiceLineList();
    invoice.setInvoiceLineList(
        saleOrderLineList.stream()
            .filter(line -> expendableSaleOrderLineCids.contains(line.getParentCid()))
            .collect(Collectors.toList()));
  }
}
