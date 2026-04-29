package com.example.notification.controller;
import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import java.util.*;
@ControllerAdvice public class ApiExceptionHandler { @ExceptionHandler(NoSuchElementException.class) ResponseEntity<Map<String,String>> notFound(){return ResponseEntity.status(404).body(Map.of("error","not_found"));} @ExceptionHandler(Exception.class) ResponseEntity<Map<String,String>> err(Exception e){return ResponseEntity.status(500).body(Map.of("error","internal_error","message",e.getMessage()==null?"":e.getMessage()));}}
