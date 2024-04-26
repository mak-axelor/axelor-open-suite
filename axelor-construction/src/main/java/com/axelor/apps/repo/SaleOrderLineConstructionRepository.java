package com.axelor.apps.repo;

import com.axelor.apps.supplychain.db.repo.SaleOrderLineSupplychainRepository;
import com.axelor.inject.Beans;
import com.axelor.studio.db.repo.AppConstructionRepository;
import java.util.Map;

public class SaleOrderLineConstructionRepository extends SaleOrderLineSupplychainRepository {
  @Override
  public Map<String, Object> populate(Map<String, Object> json, Map<String, Object> context) {

    json.put(
        "$isUnitPriceCalculationEnabled",
        Beans.get(AppConstructionRepository.class)
            .all()
            .fetchOne()
            .getIsUnitPriceCalculationEnabled());

    return super.populate(json, context);
  }
}
