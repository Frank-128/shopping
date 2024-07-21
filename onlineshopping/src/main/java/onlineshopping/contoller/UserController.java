package onlineshopping.contoller;

import lombok.RequiredArgsConstructor;
import onlineshopping.model.*;
import onlineshopping.pay.PaymentFacade;
import onlineshopping.service.impl.AuthService;
import onlineshopping.service.impl.OrderServiceImpl;
import onlineshopping.service.impl.SearchServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;


@CrossOrigin()
@RestController
@RequestMapping(path = "/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final OrderServiceImpl orderService;
    private final PaymentFacade paymentFacade;
    private final AuthService authService;
    private final SearchServiceImpl searchService;

    @CrossOrigin()
    @PostMapping("/cart/checkout")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest orderRequest) {
        OrderResponse response = new OrderResponse();

        if (orderRequest.getEmail().isEmpty() || orderRequest.getStreet().isEmpty() || orderRequest.getRegion().isEmpty()) {
            response.setErrorMessage("Please fill in all address fields!");
            return ResponseEntity.badRequest().body(response);
        } else if (orderRequest.getCartItems() == null || orderRequest.getCartItems().isEmpty()) {
            response.setErrorMessage("Cart is empty!");
            return ResponseEntity.badRequest().body(response);
        }

        for (CartItem item : orderRequest.getCartItems()) {
            try {
                ResponseEntity<String> result = orderService.processOrder(orderRequest.getEmail(), orderRequest.getStreet(), orderRequest.getRegion(), item);
                if (result.getStatusCode().is2xxSuccessful()) {
                    String successMessage = result.getBody();
                    response.setSuccessful(true);
                    response.setOrderNo(successMessage);
                    response.setCustomerEmail(orderRequest.getEmail());
                    response.setBilling_address(orderRequest.getStreet() + " " + orderRequest.getRegion());
                } else {
                    response.setSuccessful(false);
                    String itemErrorMessage = String.format("Failed to process item %s: %s", item.getItemNo(), result.getBody());
                    response.setErrorMessage((response.getErrorMessage() == null ? "" : response.getErrorMessage() + "\n") + itemErrorMessage);
                }
            } catch (Exception e) {
                response.setSuccessful(false);
                String itemErrorMessage = String.format("Failed to process item %s: An unexpected error occurred.", item.getItemNo());
                response.setErrorMessage((response.getErrorMessage() == null ? "" : response.getErrorMessage() + "\n") + itemErrorMessage);
            }
        }

        response.setSuccessful(response.getErrorMessage() == null);
        if (response.isSuccessful()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @CrossOrigin()
    @PostMapping("/publish-product")
    public ResponseEntity<String> publishItem(
            @RequestParam(name = "itemName", required = false) String itemName,
            @RequestParam(name = "sizes", required = false) List<String> sizes,
            @RequestParam(name = "colors", required = false) List<String> colors,
            @RequestParam(name = "stokeQuantity", required = false) int stokeQuantity,
            @RequestParam(name = "actualPrice", required = false) float actualPrice,
            @RequestParam(name = "discountPrice", required = false) float discountPrice,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "imageUrl", required = false) MultipartFile imageUrl,
            @RequestParam(name = "category", required = false) List<String> category,
            @RequestParam(name = "type", required = false) String type
            ){
        return orderService.publishItem(
                itemName,sizes,colors,
                stokeQuantity,actualPrice,discountPrice,description,imageUrl,category,type
        );
    }

    @CrossOrigin()
    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> makePayment(@RequestBody PaymentRequest paymentRequest)
    {
       return ResponseEntity.ok(paymentFacade.pay(paymentRequest));
    }


    //find a specific user with the provided enrollmentID
    @CrossOrigin()
    @GetMapping("/{enrollmentID}")
    public ResponseEntity<UserResponse> getUser(@PathVariable("enrollmentID") String enrollmentID){
        return authService.getUser(enrollmentID);
    }

    @CrossOrigin()
    @PostMapping("/profile")
    public ResponseEntity<String> updateProfile(
            @RequestParam(name = "enrollmentID", required = false) String enrollmentID,
            @RequestParam(name = "profile", required = false) MultipartFile profile
    ) throws IOException {
        return authService.updateProfile(enrollmentID, profile);
    }

    @CrossOrigin()
    @PostMapping("/forget-password")
    public ResponseEntity<String> updateProfile(
            @RequestParam(name = "email") String email,
            @RequestParam(name = "password", required = false) String newPassword
    ){
        return authService.updateProfile(email,newPassword);
    }
    @CrossOrigin()
    @PostMapping("/cancel-order")
    public ResponseEntity<String> cancelOrder(
            @RequestParam(name = "orderNo", required = false) String orderNo
    ){
        return searchService.cancelOrder(orderNo);
    }
}
