package onlineshopping.service.base;

import onlineshopping.entity.Order;
import onlineshopping.model.CartItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface OrderService {
    ResponseEntity<String> processOrder(String email, String street, String region, CartItem item);

    ResponseEntity<String> publishItem(String itemName, List<String> sizes, List<String> colors, int stokeQuantity, float actualPrice, float discountPrice, String description, MultipartFile imageUrl, List<String> category, String type);
}
