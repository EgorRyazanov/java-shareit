package ru.practicum.shareit.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.practicum.shareit.booking.dto.BookingState;

@RestControllerAdvice
public class ErrorHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        MissingRequestHeaderException.class, MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidRequest(final Exception e) {
        return new ErrorResponse("Invalid request");
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(final MethodArgumentTypeMismatchException e) {
        return new ErrorResponse(e.getRequiredType() == BookingState.class ? "Unknown state: " + e.getValue() : "Invalid request");
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handleServerUnavailable(final ResourceAccessException e) {
        return new ErrorResponse("ShareIt server is unavailable");
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGlobalErrors(final RuntimeException e) {
        return new ErrorResponse("internal server error");
    }
}
