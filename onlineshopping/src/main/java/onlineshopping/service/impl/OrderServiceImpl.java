package onlineshopping.service.impl;

import lombok.RequiredArgsConstructor;
import onlineshopping.constants.Status;
import onlineshopping.entity.*;
import onlineshopping.exc.HandleExceptions;
import onlineshopping.model.CartItem;
import onlineshopping.repo.*;
import onlineshopping.service.base.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Random;


@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final UserRepo userRepo;
    private final OrderRepo orderRepo;
    private final OrderItemRepo orderItemRepo;
    private final OrderStatusRepo statusRepo;
    private final ItemRepo itemRepo;


    @Override
    public ResponseEntity<Order> processOrder(String email, String street, String region, CartItem item) {
        try {

            Customer customer = userRepo.findByEmail(email);
            if (customer == null) {
                throw new HandleExceptions("Oops! you need to have an account");
            }
            Item items = itemRepo.findByItemNo(item.getItemNo());
            double totalPrice = getTotalPrice(item, items);

            Order order = new Order();
            order.setOrderNo(generateRandomOrderNumber());
            order.setAddress(street + " " + region);
            order.setCustomer(customer);
            order.setTotalPrice(totalPrice);
            orderRepo.save(order);

            OrderStatus orderStatus = new OrderStatus();
            orderStatus.setOrder_status(Status.ongoing.name());
            orderStatus.setOrder(order);
            statusRepo.save(orderStatus);

            saveOrderItem(order, item.getItemNo(), item.getProductQuantity(), item.getColors(), item.getSizes());

            return ResponseEntity.ok(order);
        } catch (HandleExceptions exception) {
            return ResponseEntity.badRequest().body(null);
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private static double getTotalPrice(CartItem item, Item items) {
        if (items == null) {
            throw new HandleExceptions("Oops! Can't find that item");
        }

        double actualPrice = items.getActualPrice();
        double discountPrice = items.getDiscountPrice();
        double itemPrice = 0;
        if (discountPrice > 0){
            itemPrice = actualPrice - discountPrice;
        }else if (discountPrice == 0){
            itemPrice = actualPrice;
        }
        return itemPrice * item.getProductQuantity();
    }

    @Override
    public ResponseEntity<String> publishItem(String itemName, List<String> sizes, List<String> colors, int stokeQuantity, float actualPrice, float discountPrice, String description, MultipartFile imageUrl, List<String> category) {
            try {
                String item_no = generateRandomAlphanumericItemNo();

                Item item = getItem(itemName,sizes,colors,stokeQuantity,actualPrice,discountPrice,description,imageUrl, item_no, category);

                itemRepo.save(item);
                return ResponseEntity.ok("publishing successfully");

            }catch (HandleExceptions exception){
                throw new HandleExceptions("Something went wrong while processing publication of item: "+ exception);
            } catch (IOException e) {
                throw new HandleExceptions("Error: "+e);
            }
    }

    private Item getItem(String itemName, List<String> sizes, List<String> colors, int stokeQuantity, float actualPrice, float discountPrice, String description, MultipartFile imageUrl, String itemNo, List<String> category) throws IOException {
        Item item = new Item();
        item.setItemNo(itemNo);
        item.setItemName(itemName);
        item.setActualPrice(actualPrice);
        item.setInitialQuantity(stokeQuantity);
        item.setDescription(description);
        item.setDiscountPrice(discountPrice);
        item.setImageUrl(storeImages(imageUrl));
        item.setRatings(0);// Default each product/item has 0 ratings
        item.setColors(colors);
        item.setSizes(sizes);
        item.setCategory(category);
        return item;
    }


    public static  String storeImages(MultipartFile imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new IllegalArgumentException("Image file is null or empty");
        }

        String uploadDirectory = "/home/muddy/shopping/onlineshopping/src/main/resources/static/images";
        String imageName = StringUtils.cleanPath(Objects.requireNonNull(imageUrl.getOriginalFilename()));

        if (imageName.contains("..")) {
            throw new IllegalArgumentException("Invalid file format");
        }

        Path uploadPath = Paths.get(uploadDirectory);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path filePath = uploadPath.resolve(imageName);
        Files.copy(imageUrl.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return imageName;
    }


    private void saveOrderItem(Order order, String itemNo, int productQuantity, List<String> sizes, List<String> colors) {
        Item item = itemRepo.findByItemNo(itemNo);
        if (item != null) {
            OrderItem orderItem = new OrderItem();
            orderItem.setItem(item);
            orderItem.setQuantity(productQuantity);
            orderItem.setSizes(sizes);
            orderItem.setColors(colors);
            orderItem.setOrder(order);
            orderItemRepo.save(orderItem);

            item.setCurrentQuantity(item.getInitialQuantity() - productQuantity);
            itemRepo.save(item);
        } else {
            throw new HandleExceptions("Oops! invalid or not exist item number");
        }
    }


    private String generateRandomOrderNumber(){
        int orderNumberLength = 5;
        StringBuilder builder = new StringBuilder();

        Random random = new Random();
        for (int i=0; i < orderNumberLength; i++){
            int digit = random.nextInt(10);
            builder.append(digit);
        }
        return "555"+builder;
    }

    private String generateRandomAlphanumericItemNo() {
        StringBuilder randomAlphanumericItemNo = new StringBuilder(5);

        String alphanumeric = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        Random random = new Random();

        for (int i = 0; i < 15; i++) {
            int index = random.nextInt(alphanumeric.length());
            char randomChar = alphanumeric.charAt(index);
            randomAlphanumericItemNo.append(randomChar);
        }

        return randomAlphanumericItemNo.toString();
    }
}
