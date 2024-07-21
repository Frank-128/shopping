package onlineshopping.service.impl;

import lombok.RequiredArgsConstructor;
import onlineshopping.constants.Status;
import onlineshopping.entity.Item;
import onlineshopping.entity.Order;
import onlineshopping.entity.OrderStatus;
import onlineshopping.exc.DatabaseAccessException;
import onlineshopping.exc.HandleExceptions;
import onlineshopping.exc.SearchExceptions;
import onlineshopping.model.SalesPerMonthDTO;
import onlineshopping.repo.*;
import onlineshopping.service.base.SearchBaseService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchBaseService {

    private final ItemRepo itemRepo;
    private final UserRepo userRepo;
    private final OrderRepo orderRepo;
    private final OrderItemRepo orderItemRepo;
    private final TransactionRepo transactionRepo;
    private final OrderStatusRepo statusRepo;

    @Override
    public List<String> findItemNames(String queryStr) {
       try {
           return itemRepo.findItemNames(queryStr);
       }catch (DataAccessException exception){
           throw new HandleExceptions("Error: No search result found");
       }
    }


    @Override
    public List<Item> findFoundItems() {
        List<Item> items = itemRepo.findAllItem();
        for (Item item : items) {
            // Process sizes and colors to remove escaped quotes
            List<String> processedSizes = item.getSizes().stream()
                    .map(size -> size.replaceAll("^\"|\"$", ""))
                    .collect(Collectors.toList());
            item.setSizes(processedSizes);

            List<String> processedColors = item.getColors().stream()
                    .map(color -> color.replaceAll("^\"|\"$", ""))
                    .collect(Collectors.toList());
            item.setColors(processedColors);
        }
        return items;
    }


    @Override
    public Item findUniqueItem(String queryString) {
        try {
            return itemRepo.findByItemNo(queryString);
        } catch (NoSuchElementException e) {
            throw new HandleExceptions("No item found matching your search query.");
        }
    }

    @Override
    public Page<Object[]> findAllUsers(Pageable pageable) {
        try {
            return userRepo.findAllUsers(pageable);
        }catch (DataAccessException accessException){
            throw new DatabaseAccessException("Error: "+accessException.getMessage());
        }
    }

    @Override
    public Page<Object[]> findOrders(Pageable pageable) {
        try {
            return orderRepo.findOrders(pageable);
        }catch (DataAccessException accessException){
            throw new DatabaseAccessException("Error: "+accessException.getMessage());
        }
    }

    @Override
    public Page<Object[]> findLatestOrders(Pageable pageable) {
        try {
            return orderRepo.findLatestOrders(pageable);
        }catch (DataAccessException accessException){
            throw new DatabaseAccessException("Error: "+accessException.getMessage());
        }
    }

    @Override
    public int findTotalSales() {
        try {
           return orderRepo.findTotalSales();
        }catch (DataAccessException accessException){
            throw new DatabaseAccessException("Error: "+accessException.getMessage());
        }
    }

    @Override
    public int findTotalOrders() {
        try {
            return orderRepo.findTotalOrders();
        }catch (DataAccessException accessException){
            throw new DatabaseAccessException("Error: "+accessException.getMessage());
        }
    }

    @Override
    public int findTotalProduct() {
        try {
            return orderItemRepo.findTotalProduct();
        }catch (DataAccessException accessException){
            throw new DatabaseAccessException("Error: "+accessException.getMessage());
        }
    }

    @Override
    public Page<Object[]> findProducts(Pageable pageable) {
        try {
            return orderItemRepo.findProducts(pageable);
        }catch (DataAccessException accessException){
            throw new DatabaseAccessException("Error: "+accessException);
        }
    }

    @Override
    public List<SalesPerMonthDTO> getSalesPerMonth() {
            List<Object[]> results = transactionRepo.findSalesPerMonth();
            List<SalesPerMonthDTO> salesPerMonthList = new ArrayList<>();

            for (Object[] result : results) {
                String month = (String) result[0];
                double totalSales = (Double) result[1];
                salesPerMonthList.add(new SalesPerMonthDTO(month, totalSales));
            }

            return salesPerMonthList;
    }


    @Override
    public ResponseEntity<String> processOrderStatus(String orderNo, String orderStatus) {
        try {
            Order order = orderRepo.findByOrderNo(orderNo);
            if (order == null){
                throw new SearchExceptions("Oops!!! No order matches");
            }

            OrderStatus status = order.getOrderStatus();
            if (status == null) {
                throw new SearchExceptions("Oops!!! No order status found for the order");
            }

            Status newStatus;
            try {
                newStatus = Status.valueOf(orderStatus);
            } catch (IllegalArgumentException e) {
                throw new SearchExceptions("Invalid order status: " + orderStatus);
            }

            switch (newStatus) {
                case completed:
                    status.setOrder_status(Status.completed.name());
                    break;
                case canceled:
                    status.setOrder_status(Status.canceled.name());
                    break;
                case processing:
                    status.setOrder_status(Status.processing.name());
                    break;
                case shipped:
                    status.setOrder_status(Status.shipped.name());
                    break;
                case delivered:
                    status.setOrder_status(Status.delivered.name());
                    break;
                default:
                    throw new SearchExceptions("Unhandled order status: " + newStatus);
            }

            status.setDate_updated(LocalDateTime.now().withNano(0));
            order.setDate_updated(LocalDateTime.now().withNano(0));

            statusRepo.save(status);
            orderRepo.save(order);

            return ResponseEntity.ok("Order confirmed successfully");
        }catch (SearchExceptions exceptions){
            throw new SearchExceptions("Error: "+exceptions.getMessage());
        }
    }

    @Override
    public ResponseEntity<String> cancelOrder(String orderNo) {
        try {
            Order order = orderRepo.findByOrderNo(orderNo);
            if (order == null){
                throw new SearchExceptions("Oops!!! No order matches");
            }

            OrderStatus status = order.getOrderStatus();
            if (status == null) {
                throw new SearchExceptions("Oops!!! No order status found for the order");
            }

            status.setOrder_status(Status.canceled.name());
            status.setDate_updated(LocalDateTime.now().withNano(0));

            order.setDate_updated(LocalDateTime.now().withNano(0));

            statusRepo.save(status);
            orderRepo.save(order);

            return ResponseEntity.ok("Order confirmed successfully");
        }catch (SearchExceptions exceptions){
            throw new SearchExceptions("Error: "+exceptions.getMessage());
        }
    }


    public ResponseEntity<String> getImagePath(String imageName){
        Optional<String> image_path = itemRepo.findByImageUrl(imageName);
        if (image_path.isPresent()){
            String imagePath = "/images/" + imageName;
            return ResponseEntity.status(HttpStatus.FOUND).body(imagePath);
        }
        else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Oops! image not found");
        }
    }

}
