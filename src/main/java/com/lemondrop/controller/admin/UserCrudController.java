package com.lemondrop.controller.admin;

import com.lemondrop.model.User;
import com.lemondrop.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

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
    public String save(@RequestParam String name,
                       @RequestParam String phone,
                       @RequestParam String username,
                       @RequestParam String password,
                       @RequestParam(required = false) String confirmPassword,
                       @RequestParam String role,
                       RedirectAttributes redirectAttributes) {

        if (username == null || username.trim().length() < 3) {
            redirectAttributes.addFlashAttribute("errorMessage", "El nombre de usuario debe tener al menos 3 caracteres.");
            return "redirect:/admin/usuarios";
        }

        if (password == null || password.length() < 8) {
            redirectAttributes.addFlashAttribute("errorMessage", "La contraseña debe tener al menos 8 caracteres.");
            return "redirect:/admin/usuarios";
        }

        if (confirmPassword != null && !password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Las contraseñas no coinciden.");
            return "redirect:/admin/usuarios";
        }

        if (userService.getByUsername(username.trim()).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "El nombre de usuario '" + username + "' ya está en uso.");
            return "redirect:/admin/usuarios";
        }

        User user = User.builder()
                .name(name.trim())
                .phone(phone != null ? phone.trim() : "")
                .username(username.trim().toLowerCase())
                .passwordHash(password)
                .role(role != null ? role.toUpperCase() : "ASESOR")
                .active(true)
                .build();

        userService.save(user);
        redirectAttributes.addFlashAttribute("successMessage", "Usuario registrado exitosamente.");
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/toggle/{id}")
    public String toggleActive(@PathVariable String id, RedirectAttributes redirectAttributes) {
        userService.toggleActive(id);
        redirectAttributes.addFlashAttribute("successMessage", "Estado de usuario actualizado.");
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/api/{id}/password")
    @ResponseBody
    public ResponseEntity<?> resetPasswordApi(@PathVariable String id, @RequestBody Map<String, String> payload) {
        String newPassword = payload.get("password");
        if (newPassword == null || newPassword.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "La nueva contraseña debe tener al menos 8 caracteres."));
        }

        userService.updatePassword(id, newPassword);
        return ResponseEntity.ok(Map.of("message", "Contraseña restablecida con éxito."));
    }
}

