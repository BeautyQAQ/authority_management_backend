package com.liushao.vueadmin.common.exception;

import com.liushao.vueadmin.common.lang.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<Result> api(ApiException e) {
    return error(e.status(), e.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Result> denied() {
    return error(403, "没有操作权限");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Result> invalid(MethodArgumentNotValidException e) {
    return error(400, e.getBindingResult().getAllErrors().getFirst().getDefaultMessage());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class
  })
  ResponseEntity<Result> badRequest() {
    return error(400, "请求参数不正确");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Result> conflict() {
    return error(409, "名称或编码已存在，或记录仍有关联数据");
  }

  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  ResponseEntity<Result> notFound() {
    return error(404, "请求的资源不存在");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<Result> methodNotAllowed() {
    return error(405, "请求方法不支持");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Result> unexpected(Exception e) {
    log.error("Request failed", e);
    return error(500, "服务暂时不可用，请稍后重试");
  }

  private ResponseEntity<Result> error(int code, String message) {
    return ResponseEntity.status(code).body(Result.fail(code, message, null));
  }
}
