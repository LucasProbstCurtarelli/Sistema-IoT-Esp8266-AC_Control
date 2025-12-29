package com.iot.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller // Controlador de PÁGINAS (Retorna HTML)
public class MainController {

    @GetMapping("/login")
    public String login() {
        return "login"; // Vai procurar login.html
    }

    @GetMapping("/")
    public String dashboard() {
        return "dashboard"; // Vai procurar dashboard.html
    }
}