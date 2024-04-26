package com.axelor.apps.service;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.tax.TaxService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.service.MultiLevelSaleOrderLineServiceImpl;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.saleorder.SaleOrderComputeService;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import com.axelor.common.ObjectUtils;
import com.axelor.inject.Beans;
import com.axelor.studio.db.AppConstruction;
import com.axelor.studio.db.repo.AppConstructionRepository;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class ConstructionMultiLevelSaleOrderLineServiceImpl
    extends MultiLevelSaleOrderLineServiceImpl {
  protected final AppConstruction appConstruction;

  @Inject
  public ConstructionMultiLevelSaleOrderLineServiceImpl(
      SaleOrderComputeService saleOrderComputeService,
      SaleOrderRepository saleOrderRepository,
      TaxService taxService,
      AppBaseService appBaseService,
      SaleOrderLineService saleOrderLineService,
      SaleOrderLineRepository saleOrderLineRepository,
      AppConstruction appConstruction) {
    super(
        saleOrderComputeService,
        saleOrderRepository,
        taxService,
        appBaseService,
        saleOrderLineService,
        saleOrderLineRepository);
    this.appConstruction = appConstruction;
  }

  @Override
  protected void compute(SaleOrderLine saleOrderLine, SaleOrder saleOrder, String parent)
      throws AxelorException {
    List<SaleOrderLine> items = saleOrderLine.getSubSaleOrderLineList();
    if (ObjectUtils.isEmpty(items)) {
      return;
    }
    BigDecimal quantity = saleOrderLine.getQty();
    BigDecimal totalPrice = BigDecimal.ZERO;
    BigDecimal costPriceTotal = BigDecimal.ZERO;
    int i = 1;

    for (SaleOrderLine line : items) {
      line.setLineIndex(parent + "." + i);
      compute(line, saleOrder, parent + "." + i);
      totalPrice = totalPrice.add(line.getExTaxTotal());
      if (Beans.get(AppConstructionRepository.class)
          .all()
          .fetchOne()
          .getIsUnitPriceCalculationEnabled()) {
        costPriceTotal = costPriceTotal.add(line.getCostPrice().multiply(line.getQty()));
      }
      i++;
    }
    totalPrice = quantity.equals(BigDecimal.ZERO) ? BigDecimal.ZERO : totalPrice;
    BigDecimal price =
        quantity.equals(BigDecimal.ZERO)
            ? BigDecimal.ZERO
            : totalPrice.divide(
                quantity, appBaseService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_EVEN);
    saleOrderLine.setPrice(price);
    saleOrderLine.setExTaxTotal(totalPrice);
    if (Beans.get(AppConstructionRepository.class)
        .all()
        .fetchOne()
        .getIsUnitPriceCalculationEnabled()) {
      saleOrderLine.setCostPrice(
          costPriceTotal.divide(
              quantity, appBaseService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_EVEN));
      if (saleOrderLine.getCostPrice().compareTo(BigDecimal.ZERO) == 0) {
        saleOrderLine.setGrossMarging(BigDecimal.ZERO);
      } else {
        saleOrderLine.setGrossMarging(
            saleOrderLine
                .getPrice()
                .divide(
                    saleOrderLine.getCostPrice(),
                    AppSaleService.DEFAULT_NB_DECIMAL_DIGITS,
                    RoundingMode.HALF_EVEN)
                .subtract(saleOrderLine.getGeneralExpenses().add(BigDecimal.ONE)));
      }
    }
    computeAllValues(saleOrderLine, saleOrder);
    saleOrderLine.setPriceBeforeUpdate(saleOrderLine.getPrice());
    saleOrderLine.setQtyBeforeUpdate(saleOrderLine.getQty());
  }

  @Override
  protected void updatePrice(SaleOrderLine saleOrderLine, BigDecimal priceCoef) {
    super.updatePrice(saleOrderLine, priceCoef);

    if (Beans.get(AppConstructionRepository.class)
        .all()
        .fetchOne()
        .getIsUnitPriceCalculationEnabled()) {

      if (saleOrderLine.getCostPrice().compareTo(BigDecimal.ZERO) == 0) {
        saleOrderLine.setGrossMarging(BigDecimal.ZERO);
      } else {
        saleOrderLine.setGrossMarging(
            saleOrderLine
                .getPrice()
                .divide(
                    saleOrderLine.getCostPrice(),
                    AppSaleService.DEFAULT_NB_DECIMAL_DIGITS,
                    RoundingMode.HALF_EVEN)
                .subtract(saleOrderLine.getGeneralExpenses().add(BigDecimal.ONE)));
      }
    }
  }
}
