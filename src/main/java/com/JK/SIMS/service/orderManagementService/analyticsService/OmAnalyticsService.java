package com.JK.SIMS.service.orderManagementService.analyticsService;

public class OmAnalyticsService {

    // Average Fulfill Time.
    // Method for future reference.
//    @Transactional(readOnly = true)
//    public int getAverageFulfillTime() {
//        try {
//            long totalEntities = salesOrderRepository.count();
//            if(totalEntities == 0) return 0;
//            long totalDeliveryDate = salesOrderRepository.calculateTotalDeliveryDate();
//            return (int) (totalDeliveryDate / totalEntities);
//        } catch (DataAccessException da) {
//            log.error("OS (getAverageFulfillTime): Failed to calculate average fulfill time: {}", da.getMessage(), da);
//            throw new DatabaseException("Failed to calculate average fulfill time", da);
//        } catch (Exception e) {
//            log.error("OS (getAverageFulfillTime): Error calculating average fulfill time: {}", e.getMessage());
//            throw new ServiceException("Failed to calculate average fulfill time", e);
//        }
//    }
}
