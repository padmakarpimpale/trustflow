package com.padmakar.trustflow.web;

import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice(assignableTypes = PageController.class)
public class ErrorAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(IllegalArgumentException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "problem";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String denied(Model model) {
        model.addAttribute("message", "You do not have permission to perform this action.");
        return "problem";
    }

    @ExceptionHandler(
        {
            ObjectOptimisticLockingFailureException.class,
            org.springframework.dao.DataIntegrityViolationException.class,
        }
    )
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(Model model) {
        model.addAttribute("message", "Another user changed this record. Refresh and try again.");
        return "problem";
    }
}
