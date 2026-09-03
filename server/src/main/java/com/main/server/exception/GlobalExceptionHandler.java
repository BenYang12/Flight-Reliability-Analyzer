package com.main.server.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.stream.Collectors;

// Exception Handler is a class that catches exceptions escaping my controllers and turns them into response I control


// Every failure in this API leaves through this class, as an ErrorResponse.
// @RestControllerAdvice = @ControllerAdvice + @ResponseBody. 
// "Advice" is Spring's word for cross-cutting behaviour: code that wraps around every controller
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        return build(status, detailOf(ex, status), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return build(status, message, request);
    }
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        String message = ex.getAllErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .collect(Collectors.joining("; "));

        return build(status, message, request);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception on {}", pathOf(request), ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our end.", request);
    }

    private ResponseEntity<Object> build(
            HttpStatusCode status, String message, WebRequest request) {

        ErrorResponse body = new ErrorResponse(
                status.value(),

                message == null || message.isBlank()
                        ? HttpStatus.valueOf(status.value()).getReasonPhrase()
                        : message,
                pathOf(request),
                Instant.now());

        return ResponseEntity.status(status).body(body);
    }

    
    private static String detailOf(Exception ex, HttpStatusCode status) {
        if (ex instanceof org.springframework.web.ErrorResponse errorResponse) {
            return errorResponse.getBody().getDetail();
        }
        return HttpStatus.valueOf(status.value()).getReasonPhrase();
    }

    private static String pathOf(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return "";
    }

    private static String describe(MessageSourceResolvable error) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField() + " " + fieldError.getDefaultMessage();
        }
        return error.getDefaultMessage();
    }
}
