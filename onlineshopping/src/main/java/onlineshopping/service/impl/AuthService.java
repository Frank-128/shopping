package onlineshopping.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onlineshopping.constants.UserRole;
import onlineshopping.entity.Customer;
import onlineshopping.entity.Otp;
import onlineshopping.exc.HandleExceptions;
import onlineshopping.exc.InvalidOtpException;
import onlineshopping.exc.SearchExceptions;
import onlineshopping.jwt.service.JwtService;
import onlineshopping.model.AuthRequest;
import onlineshopping.model.AuthResponse;
import onlineshopping.model.UserDto;
import onlineshopping.model.UserResponse;
import onlineshopping.notification.model.LoginRequest;
import onlineshopping.notification.service.BeemOtpService;
import onlineshopping.repo.OtpCodeRepo;
import onlineshopping.repo.UserRepo;
import onlineshopping.service.base.BaseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService implements BaseService {

    private final UserRepo userRepo;
    private final JwtService jwtService;
    private  final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final OtpCodeRepo otpCodeRepo;
    private final BeemOtpService beemOtpService;

    private static final long OTP_EXPIRATION_MINUTES = 5;

    @Override
    public ResponseEntity<AuthResponse> createAccount(UserDto userDto) {
        try {
            Customer checkExisting = userRepo.findByEmail(userDto.getEmail());
            if (checkExisting != null){
                throw new HandleExceptions("Already user with same email exists");
            }

            Customer customer = new Customer();
            customer.setName(userDto.getName());
            customer.setEmail(userDto.getEmail());
            customer.setMobile(userDto.getMobile());
            customer.setPassword(passwordEncoder.encode(userDto.getPassword()));
            if (userDto.getRole() != null && (userDto.getRole().equalsIgnoreCase("manufacturer") ||
                            userDto.getRole().equalsIgnoreCase("sale"))) {
                customer.setRole(UserRole.ENTREPRENEUR);
            } else {
                customer.setRole(UserRole.CUSTOMER);
            }
            customer.setEnrollNumber(generateUniqueNumber());
            userRepo.save(customer);

            // otp
            String otpCode = beemOtpService.generateOtp();
            log.info("Generated OTP code for mobile {}: {}", userDto.getMobile(), otpCode);
            beemOtpService.sendOtp(userDto.getMobile(), otpCode);

            //storing otp
            Otp otp = Otp.builder()
                    .otpCode(otpCode)
                    .createdAt(LocalDateTime.now().withNano(0))
                    .customer(customer)
                    .build();
            otpCodeRepo.save(otp);

            AuthResponse response = new AuthResponse("OTP sent successfully to your phone number");
            return ResponseEntity.ok(response);
        }
        catch (HandleExceptions exceptions){
            AuthResponse authResponse = new AuthResponse(exceptions.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(authResponse);
        }
        catch (Exception e){
            AuthResponse error;
            if (e instanceof NullPointerException) {
                error = new AuthResponse("Error: User role information is missing.");
            } else {
                error = new AuthResponse("Error: " + e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);
        }
    }

    public static String generateUniqueNumber() {
        int orderNumberLength = 15;
        StringBuilder builder = new StringBuilder();

        Random random = new Random();
        for (int i=0; i < orderNumberLength; i++){
            int digit = random.nextInt(10);
            builder.append(digit);
        }
        return "222"+builder;
    }

    @Override
    public ResponseEntity<AuthResponse> authenticate(AuthRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
            var user = userRepo.findByEmail(request.getEmail());
            var token = jwtService.generateToken(user);
            return ResponseEntity.ok(AuthResponse.builder().token(token).build());
        }catch (AuthenticationException handleExceptions){
            AuthResponse error = AuthResponse.builder().token("Error: "+handleExceptions.getMessage()).build();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    @Override
    public ResponseEntity<AuthResponse> verifyOtp(LoginRequest loginRequest) {
        try {
            Customer customerWithOtp = userRepo.findByMobile(loginRequest.getMobile());

            if (customerWithOtp == null){
                throw new HandleExceptions("Oops! user not found");
            }
            else {
                Otp otpWithUser = otpCodeRepo.findByCustomer(customerWithOtp);
                 String storedOtp = otpWithUser.getOtpCode();

                 if (!loginRequest.getOtp().equals(storedOtp)){
                     throw new HandleExceptions("Invalid or Expired OTP");
                 }
                  else if (LocalDateTime.now().minusMinutes(OTP_EXPIRATION_MINUTES).isAfter(otpWithUser.getCreatedAt())) {
                    throw new InvalidOtpException("OTP has expired");
                 }
                  else {
                      // generate token for user with valid otp codes
                    String jwt = jwtService.generateToken(customerWithOtp);

                    // clear the otp entity associated with customer from database
                     otpCodeRepo.delete(otpWithUser);

                     AuthResponse authResponse = AuthResponse
                             .builder()
                             .token(jwt)
                             .build();

                     return ResponseEntity.ok(authResponse);
                  }
            }
        }
        catch (HandleExceptions exceptions) {
            AuthResponse authResponse = new AuthResponse(exceptions.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(authResponse);
        }
        catch (Exception e) {
            AuthResponse error = new AuthResponse("Error occurred!");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }

    }


    @Override
    public ResponseEntity<AuthResponse> resendOtpCodes(String phoneNumber, String oldOtpCodes) {
        try {
            Customer isUserPresent = userRepo.findByMobile(phoneNumber);
            if (isUserPresent == null){
                throw new SearchExceptions("Oops! invalid phone number!");
            }

            if (isOtpExpired(isUserPresent,oldOtpCodes)){
               throw new InvalidOtpException("Your otp have not expired");
            }

            // otp
            String otpCode = beemOtpService.generateOtp();
            log.info("Generated OTP code for mobile {}: {}", phoneNumber, otpCode);
            beemOtpService.sendOtp(phoneNumber, otpCode);

            String hashedOtpCodes = passwordEncoder.encode(otpCode);

            Otp regeneratedOtpCodes =  Otp.builder()
                    .otpCode(hashedOtpCodes)
                    .createdAt(LocalDateTime.now())
                    .customer(isUserPresent)
                    .build();
            otpCodeRepo.save(regeneratedOtpCodes);

            AuthResponse response = new AuthResponse("OTP sent successfully to your phone number");
            return ResponseEntity.ok(response);

        }catch (SearchExceptions exceptions){
            throw new SearchExceptions(exceptions.getMessage());

        } catch (Exception exception){
            throw new HandleExceptions(exception.getMessage());
        }
    }

    @Override
    public ResponseEntity<UserResponse> getUser(String enrollmentID) {
        try {
            Customer customer = userRepo.findByEnrollmentNumber(enrollmentID)
                    .orElseThrow(()-> new SearchExceptions("User not found"));

            UserResponse response = new UserResponse();
            response.setName(customer.getName());
            response.setEmail(customer.getEmail());
            response.setMobile(customer.getMobile());
            response.setRoles(String.valueOf(customer.getRole()));
            response.setUserID(customer.getEnrollNumber());

            return ResponseEntity.ok(response);

        }catch (SearchExceptions exceptions){
            throw new SearchExceptions("Error: "+exceptions.getMessage());
        }
    }

    private boolean isOtpExpired(Customer customer, String providedOtp) {
        Otp otp = otpCodeRepo.findByCustomer(customer);
        if (otp == null || !passwordEncoder.matches(providedOtp, otp.getOtpCode())) {
            return false;
        }
        return LocalDateTime.now().minusMinutes(OTP_EXPIRATION_MINUTES).isAfter(otp.getCreatedAt());
    }

}
