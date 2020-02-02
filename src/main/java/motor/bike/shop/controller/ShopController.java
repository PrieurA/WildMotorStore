package motor.bike.shop.controller;

import com.google.common.hash.Hashing;
import motor.bike.shop.entity.Cart;
import motor.bike.shop.entity.Product;
import motor.bike.shop.entity.Purchase;
import motor.bike.shop.entity.User;
import motor.bike.shop.repository.CartRepository;
import motor.bike.shop.repository.ProductRepository;
import motor.bike.shop.repository.PurchaseRepository;
import motor.bike.shop.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.util.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

@Controller
public class ShopController {


    @Autowired
    public UserRepository userRepository;

    @Autowired
    public CartRepository cartRepository;

    @Autowired
    public PurchaseRepository purchaseRepository;

    @Autowired
    public ProductRepository productRepository;

    @GetMapping("/")
    public String connection(Model out) {
        User user = new User();
        out.addAttribute("user", user);
        return "index";
    }

    @PostMapping("connexion")
    public String checkConnexion(@ModelAttribute User user, HttpSession session) {
        String encryptedPassword = Hashing.sha256()
                .hashString("!w1Ld" + user.getPassword(), StandardCharsets.UTF_8)
                .toString();
        user.setPassword(encryptedPassword);
        Optional<User> optionalUser = userRepository.findByEmailAndPassword(user.getEmail(), user.getPassword());
        if (optionalUser.isPresent()) {
            user = optionalUser.get();
            session.setAttribute("sessionUser", user);
            return "redirect:/products";
        }
        return "redirect:/connection";
    }

    @GetMapping("deconnexion")
    public String deconnexion() {
        return "redirect:/connection";
    }

    @GetMapping("inscription")
    public String signUp(HttpSession session, Model out) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        out.addAttribute("sessionUser", sessionUser);
        User newUser = new User();
        out.addAttribute("newUser", newUser);
        return "register";
    }

    @PostMapping("/creation/de/compte")
    public String register(Model out, User user, @RequestParam String password, @RequestParam String confirmPassword, HttpSession session) {
        if (session.getAttribute("user") != null) {
            return "redirect:/products";
        }
        if (password.equals(confirmPassword)) {
            String encryptedPassword = Hashing.sha256()
                    .hashString("!w1Ld" + password, StandardCharsets.UTF_8)
                    .toString();
            user.setPassword(encryptedPassword);
            User sessionUser = (User) session.getAttribute("sessionUser");
            out.addAttribute("sessionUser", sessionUser);
            userRepository.save(user);
            out.addAttribute("user", user);
            return "redirect:/products";
        }
        return "redirect:/inscription";
    }

    @GetMapping("/userApi")
    @ResponseBody
    public User API(@RequestParam Long id) {
        return userRepository.findById(id).get();
    }

    @GetMapping("/products")
    public String product(Model out, HttpSession session) {
        User user = (User) session.getAttribute("sessionUser");
        out.addAttribute("user", user);
        if (session.getAttribute("sessionUser") == null) {
            return "redirect:/";
        }
        List<Cart> carts = cartRepository.findAllByUserAndOrdered(user, false);
        Cart cart = new Cart();
        if (carts.size() == 0 || cart.isOrdered()) {
            cart.setOrdered(false);
            cart.setUser(user);
            cart = cartRepository.save(cart);
        } else {
            cart = carts.get(0);
        }
        out.addAttribute("idCart", cart.getIdCart());
        List<Product> products = productRepository.findAll();
        out.addAttribute("products", products);
        out.addAttribute("purchase", new Purchase());
        return "product";
    }

    @PostMapping("/addProduct")
    public String addProduct(@ModelAttribute Purchase purchase, HttpSession session, Model out, @RequestParam Long idProduct) {
        User user = (User) session.getAttribute("sessionUser");
        out.addAttribute("user", user);
        if (session.getAttribute("sessionUser") == null) {
            return "redirect:/";
        }
        List<Cart> carts = cartRepository.findAllByUserAndOrdered(user, false);
        Cart cart = new Cart();
        if (carts.size() == 0) {
            cart.setOrdered(false);
            cart.setUser(user);
            cart = cartRepository.save(cart);
        } else {
            cart = carts.get(0);
        }
        purchase.setCart(cart);
        Product product = productRepository.findById(idProduct).get();
        purchase.setProduct(product);
        purchaseRepository.save(purchase);
        return "redirect:/products";
    }

    @GetMapping("/cart/{id}")
    public String cart(Model out, HttpSession session, @PathVariable("id") Long idCart, @RequestParam(required = false) Long idPurchase) {
        User user = (User) session.getAttribute("sessionUser");
        out.addAttribute("user", user);
        if (session.getAttribute("sessionUser") == null) {
            return "redirect:/";
        }
        List<Cart> carts = cartRepository.findAllByUserAndOrdered(user, false);
        Cart cart = new Cart();
        if (carts.size() == 0 || cart.isOrdered()) {
            cart.setOrdered(false);
            cart.setUser(user);
            cart = cartRepository.save(cart);
        } else {
            cart = carts.get(0);
        }
        out.addAttribute("idCart", cart.getIdCart());
        List<Purchase> purchases = purchaseRepository.findAll();
        out.addAttribute("purchases", purchases);
        Product product = new Product();
        out.addAttribute("idProduct", product.getIdProduct());
        List<Product> products = productRepository.findAll();
        out.addAttribute("products", products);
        Optional<Cart> cart1 = cartRepository.findById(idCart);
        if (cart1.isPresent()) {
            out.addAttribute("cart", cart1.get());
            out.addAttribute("idCart1", idCart);
            Purchase purchase = new Purchase();
            out.addAttribute("idPurchase", idPurchase);
            if (idPurchase != null) {
                Optional<Purchase> optionalPurchase = purchaseRepository.findById(idPurchase);
                if (optionalPurchase.isPresent()) {
                    purchase = optionalPurchase.get();
                }
            }
            out.addAttribute("total", total(idCart));
            out.addAttribute("purchase", purchase);
            out.addAttribute("page", "cart/" + idCart);
            return "cart";
        }
        return "products";
    }

    @PostMapping("/cart")
    public String buyCart(@RequestParam Long idCart, Model out, @ModelAttribute Cart cart) {
        out.addAttribute("idCart", idCart);
        Optional<Cart> optionalCart = cartRepository.findById(idCart);
        if (optionalCart.isPresent()) {
            if (cart.getIdCart().equals(idCart)) {
                Cart cartFromDb = optionalCart.get();
                cartFromDb.setOrdered(true);
                cartRepository.save(cartFromDb);
            }
        }
        return "redirect:/user";
    }

    @PostMapping("/delete/product")
    public String deleteProduct(Model out, @RequestParam Long idPurchase, @RequestParam String idCart, @RequestParam String page) {
        out.addAttribute("idPurchase", idPurchase);
        Optional<Purchase> optionalPurchase = purchaseRepository.findById(idPurchase);
        if (optionalPurchase.isPresent()) {
            Purchase purchaseFromDb = optionalPurchase.get();
            purchaseRepository.delete(purchaseFromDb);
            out.addAttribute("idCart", idCart);
            if (page.equals("cart/" + idCart)) {
                return "redirect:/cart/" + idCart;
            }

        }
        return "redirect:/products";
    }

    @GetMapping("/user")
    public String profil(Model out, HttpSession session, @ModelAttribute Cart cart) {
        User user = (User) session.getAttribute("sessionUser");
        out.addAttribute("user", user);
        if (session.getAttribute("sessionUser") == null) {
            return "redirect:/";
        }
        Long idUser = user.getIdUser();
        if (idUser != null) {
            Optional<User> optionalUser = userRepository.findById(idUser);
            if (optionalUser.isPresent()) {
                user = optionalUser.get();
            }
            List<Purchase> purchases = purchaseRepository.findAll();
            out.addAttribute("purchases", purchases);
            List<Cart> carts = cartRepository.findAllByUserOrderByOrderedAsc(user);
            out.addAttribute("carts", carts);
            for (Cart cartFromDb : carts) {
                Long idCart = cartFromDb.getIdCart();
                cartFromDb.setTotal(total(idCart));
            }
            Cart newCart = new Cart();
            if (carts.size() == 0 || cart.isOrdered()) {
                newCart.setOrdered(false);
                newCart.setUser(user);
                newCart = cartRepository.save(cart);
            } else {
                newCart = carts.get(0);
            }
            out.addAttribute("idCart", newCart.getIdCart());
            return "user";
        }
        return "products";
    }

    @PostMapping("/updateUser")
    public String updateProfil(@ModelAttribute User user, HttpSession session, @RequestParam Long idUser) {
        Optional<User> optionalUser = userRepository.findById(idUser);
        if (optionalUser.isPresent()) {
            User userFromDb = (User) session.getAttribute("sessionUser");
            userFromDb.setFirstname(user.getFirstname());
            userFromDb.setLastname(user.getLastname());
            userFromDb.setPhoneNumber(user.getPhoneNumber());
            userFromDb.setAddress(user.getAddress());
            userRepository.saveAndFlush(userFromDb);
        }
        return "redirect:/user";
    }

    @GetMapping("/showPicture")
    public void showImage(HttpServletResponse response, @RequestParam String url)
            throws ServletException, IOException {

        response.setContentType("image/jpeg, image/jpg, image/png, image/gif");
        File file;
        if (StringUtils.isEmpty(url)) {
            file = new ClassPathResource("static/images/user-icon.png").getFile();
        } else {
            file = new File(url);
        }
        byte[] fileContent = Files.readAllBytes(file.toPath());
        response.getOutputStream().write(fileContent);

        response.getOutputStream().close();
    }

    public int total(Long idCart) {
        int price;
        List<Purchase> purchases = purchaseRepository.findAll();
        int result = 0;
        for (Purchase purchase : purchases) {
            if (purchase.getCart().getIdCart().equals(idCart)) {
                price = purchase.getProduct().getPrice();
                result += price;
            }
        }
        return result;
    }
}