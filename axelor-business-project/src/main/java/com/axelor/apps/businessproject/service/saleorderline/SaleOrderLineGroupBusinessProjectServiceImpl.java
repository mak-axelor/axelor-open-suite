package com.axelor.apps.businessproject.service.saleorderline;

import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.service.saleorderline.SaleOrderLineAttrsSetService;
import com.axelor.apps.sale.service.saleorderline.SaleOrderLineRecordUpdateService;
import com.axelor.apps.supplychain.service.saleOrderLine.SaleOrderLineAttrsSetSupplychainService;
import com.axelor.apps.supplychain.service.saleOrderLine.SaleOrderLineRecordUpdateSupplyChainService;
import com.axelor.apps.supplychain.service.saleOrderLine.SaleOrderLineSupplyChainGroupServiceImpl;
import com.google.inject.Inject;
import java.util.Map;

public class SaleOrderLineGroupBusinessProjectServiceImpl
    extends SaleOrderLineSupplyChainGroupServiceImpl {

  protected final SaleOrderLineAttrsSetBusinessProjectService
      saleOrderLineAttrsSetBusinessProjectService;
  protected final SaleOrderLineRecordUpdateBusinessProjectService
      saleOrderLineRecordUpdateBusinessProjectService;

  @Inject
  public SaleOrderLineGroupBusinessProjectServiceImpl(
      SaleOrderLineAttrsSetService saleOrderLineAttrsSetService,
      SaleOrderLineRecordUpdateService saleOrderLineRecordUpdateService,
      SaleOrderLineAttrsSetSupplychainService saleOrderLineAttrsSetSupplychainService,
      SaleOrderLineRecordUpdateSupplyChainService saleOrderLineRecordUpdateSupplyChainService,
      SaleOrderLineAttrsSetBusinessProjectService saleOrderLineAttrsSetBusinessProjectService,
      SaleOrderLineRecordUpdateBusinessProjectService
          saleOrderLineRecordUpdateBusinessProjectService) {
    super(
        saleOrderLineAttrsSetService,
        saleOrderLineRecordUpdateService,
        saleOrderLineAttrsSetSupplychainService,
        saleOrderLineRecordUpdateSupplyChainService);
    this.saleOrderLineAttrsSetBusinessProjectService = saleOrderLineAttrsSetBusinessProjectService;
    this.saleOrderLineRecordUpdateBusinessProjectService =
        saleOrderLineRecordUpdateBusinessProjectService;
  }

  @Override
  public void getOnNewValuesMap(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, Map<String, Map<String, Object>> attrsMap) {
    super.getOnNewValuesMap(saleOrder, saleOrderLine, attrsMap);
    saleOrderLineAttrsSetBusinessProjectService.setProjectTitle(attrsMap);
    saleOrderLineRecordUpdateBusinessProjectService.setProjectValue(saleOrder, attrsMap);
    saleOrderLineRecordUpdateBusinessProjectService.setEstimatedDateValue(
        saleOrderLine, saleOrder, attrsMap);
  }
}
