package com.lemondrop.controller.admin;

import com.lemondrop.model.User;
import com.lemondrop.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/usuarios")
public class UserCrudController {

    private final UserService userService;

    public UserCrudController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.getAllUsers());
        return "admin/usuarios";
    }

    @PostMapping("/guardar")
    public String save(@ModelAttribute User user) {
        // Hash password will be managed in UserService
        userService.save(user);
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/toggle/{id}")
    public String toggleActive(@PathVariable String id) {
        userService.toggleActive(id);
        return "redirect:/admin/usuarios";
    }
}
